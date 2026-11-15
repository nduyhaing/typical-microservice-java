import { act, fireEvent, render, screen } from '@testing-library/react';
import Router from 'next/router';
import { vi } from 'vitest';
import * as searchService from '../services/SearchService';
import SearchBox from './SearchBox';

vi.mock('next/router', () => ({ default: { push: vi.fn() } }));
vi.mock('../services/SearchService');

const names = (...values: string[]) => ({ productNames: values.map((name) => ({ name })) });

const type = (value: string) => fireEvent.change(screen.getByRole('combobox', { name: 'Search products' }), { target: { value } });

/** Lets the debounce fire and the mocked request settle. */
const flush = async (ms = 250) => {
  await act(async () => {
    vi.advanceTimersByTime(ms);
  });
  await act(async () => {});
};

describe('SearchBox', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    vi.useRealTimers();
    vi.resetAllMocks();
  });

  it('debounces: one suggestion request for the last term, only after 250 ms', async () => {
    vi.mocked(searchService.getSuggestions).mockResolvedValue(names('Galaxy S24', 'Galaxy Tab'));
    render(<SearchBox />);

    type('ga');
    type('gal');
    type('gala');
    await flush(200);
    expect(searchService.getSuggestions).not.toHaveBeenCalled();

    await flush(50);
    expect(searchService.getSuggestions).toHaveBeenCalledTimes(1);
    expect(vi.mocked(searchService.getSuggestions).mock.calls[0][0]).toBe('gala');
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual(['Galaxy S24', 'Galaxy Tab']);
  });

  it('asks nothing for fewer than 2 characters and hides old suggestions', async () => {
    vi.mocked(searchService.getSuggestions).mockResolvedValue(names('Galaxy S24'));
    render(<SearchBox />);

    type('ga');
    await flush();
    expect(screen.getAllByRole('option')).toHaveLength(1);

    type('g');
    await flush();
    expect(searchService.getSuggestions).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('option')).not.toBeInTheDocument();
  });

  it('aborts a superseded request and ignores its late answer', async () => {
    let resolveFirst: (value: ReturnType<typeof names>) => void = () => {};
    vi.mocked(searchService.getSuggestions)
      .mockImplementationOnce(() => new Promise((resolve) => (resolveFirst = resolve)))
      .mockResolvedValueOnce(names('Galaxy Tab'));
    render(<SearchBox />);

    type('ga');
    await flush();
    const firstSignal = vi.mocked(searchService.getSuggestions).mock.calls[0][1] as AbortSignal;
    expect(firstSignal.aborted).toBe(false);

    type('galaxy t');
    await flush();
    expect(firstSignal.aborted).toBe(true);

    await act(async () => resolveFirst(names('Old answer')));
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual(['Galaxy Tab']);
  });

  it('a failing suggestion request shows no suggestions and no error', async () => {
    vi.mocked(searchService.getSuggestions).mockRejectedValue(new Error('down'));
    render(<SearchBox />);

    type('galaxy');
    await flush();

    expect(screen.queryByRole('option')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('arrow keys move the highlight; Enter searches the highlighted name', async () => {
    vi.mocked(searchService.getSuggestions).mockResolvedValue(names('Galaxy S24', 'Galaxy Tab'));
    render(<SearchBox />);
    const input = screen.getByRole('combobox', { name: 'Search products' });

    type('gal');
    await flush();
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    fireEvent.keyDown(input, { key: 'ArrowDown' });
    expect(screen.getByRole('option', { name: 'Galaxy Tab' })).toHaveAttribute('aria-selected', 'true');
    fireEvent.keyDown(input, { key: 'ArrowUp' });
    expect(screen.getByRole('option', { name: 'Galaxy S24' })).toHaveAttribute('aria-selected', 'true');
    expect(input).toHaveAttribute('aria-activedescendant', screen.getByRole('option', { name: 'Galaxy S24' }).id);

    fireEvent.keyDown(input, { key: 'Enter' });

    expect(Router.push).toHaveBeenCalledWith({ pathname: '/search', query: { keyword: 'Galaxy S24' } });
    expect(input).toHaveValue('Galaxy S24');
    expect(screen.queryByRole('option')).not.toBeInTheDocument();
  });

  it('Escape closes the suggestions', async () => {
    vi.mocked(searchService.getSuggestions).mockResolvedValue(names('Galaxy S24'));
    render(<SearchBox />);
    const input = screen.getByRole('combobox', { name: 'Search products' });

    type('gal');
    await flush();
    fireEvent.keyDown(input, { key: 'Escape' });

    expect(screen.queryByRole('option')).not.toBeInTheDocument();
    expect(input).toHaveAttribute('aria-expanded', 'false');
  });

  it('clicking a suggestion runs the search for it', async () => {
    vi.mocked(searchService.getSuggestions).mockResolvedValue(names('Galaxy S24', 'Galaxy Tab'));
    render(<SearchBox />);

    type('gal');
    await flush();
    fireEvent.mouseDown(screen.getByRole('option', { name: 'Galaxy Tab' }));
    fireEvent.click(screen.getByRole('option', { name: 'Galaxy Tab' }));

    expect(Router.push).toHaveBeenCalledWith({ pathname: '/search', query: { keyword: 'Galaxy Tab' } });
  });

  it('submit navigates to /search with the trimmed keyword (Next encodes it)', async () => {
    render(<SearchBox />);

    type('  galaxy & tab?  ');
    fireEvent.submit(screen.getByRole('search'));

    expect(Router.push).toHaveBeenCalledWith({ pathname: '/search', query: { keyword: 'galaxy & tab?' } });
  });

  it('submit with an empty box opens the unfiltered search page', async () => {
    render(<SearchBox />);

    fireEvent.submit(screen.getByRole('search'));

    expect(Router.push).toHaveBeenCalledWith({ pathname: '/search', query: {} });
  });
});
