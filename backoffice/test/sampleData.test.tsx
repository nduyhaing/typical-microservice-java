import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import SampleDataCard from '../modules/sampledata/components/SampleDataCard';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as ratingService from '../modules/rating/services/RatingService';
import * as sampleDataService from '../modules/sampledata/services/SampleDataService';
import HomePage from '../pages/index';

vi.mock('next/router', () => ({ useRouter: () => ({ push: vi.fn(), query: {}, isReady: true, pathname: '/' }) }));
vi.mock('../modules/auth/services/AuthenticationService', () => ({ LOGOUT_URL: '/logout', getAuthentication: vi.fn() }));
vi.mock('../modules/rating/services/RatingService');
vi.mock('../modules/sampledata/services/SampleDataService');

afterEach(() => {
  vi.resetAllMocks();
  vi.restoreAllMocks();
});

const CONFIRM_TEXT =
  'Load the sample catalog? Missing sample brands, categories and products are added and deleted sample products are published again. Your own products and edits are kept.';

describe('Load sample data', () => {
  it('is on the dashboard', async () => {
    vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: true, authenticatedUser: { username: 'admin' } });
    vi.mocked(ratingService.getLatestRatings).mockResolvedValue([]);

    render(<HomePage />);

    expect(await screen.findByRole('heading', { name: 'Sample data' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Load sample data' })).toBeInTheDocument();
    expect(sampleDataService.loadSampleData).not.toHaveBeenCalled();
  });

  it('asks for confirmation, then shows the message and the counts per table', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(sampleDataService.loadSampleData).mockResolvedValue({
      message: 'Sample data loaded: 9 rows inserted, 1 products republished.',
      inserted: { brand: 4, category: 5, product: 0 },
      republished: 1,
      total: 9,
    });

    render(<SampleDataCard />);
    await userEvent.click(screen.getByRole('button', { name: 'Load sample data' }));

    expect(confirm).toHaveBeenCalledWith(CONFIRM_TEXT);
    const status = await screen.findByRole('status');
    expect(status).toHaveTextContent('Sample data loaded: 9 rows inserted, 1 products republished.');
    const items = within(status).getAllByRole('listitem').map((item) => item.textContent);
    expect(items).toEqual(['brand: 4', 'category: 5', 'product: 0', 'republished products: 1']);
    expect(sampleDataService.loadSampleData).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Load sample data' })).toBeEnabled();
  });

  it('does nothing when the confirmation is cancelled', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);

    render(<SampleDataCard />);
    await userEvent.click(screen.getByRole('button', { name: 'Load sample data' }));

    expect(sampleDataService.loadSampleData).not.toHaveBeenCalled();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('disables the button while loading', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    let resolve: (value: Awaited<ReturnType<typeof sampleDataService.loadSampleData>>) => void = () => {};
    vi.mocked(sampleDataService.loadSampleData).mockReturnValue(new Promise((r) => (resolve = r)));

    render(<SampleDataCard />);
    await userEvent.click(screen.getByRole('button', { name: 'Load sample data' }));

    expect(screen.getByRole('button', { name: 'Loading...' })).toBeDisabled();
    resolve({ message: 'Sample data is already loaded; nothing to do.', inserted: {}, republished: 0, total: 0 });
    expect(await screen.findByRole('status')).toHaveTextContent('Sample data is already loaded; nothing to do.');
  });

  it('shows the error when loading fails', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(sampleDataService.loadSampleData).mockRejectedValue(new ApiError(403, null));

    render(<SampleDataCard />);
    await userEvent.click(screen.getByRole('button', { name: 'Load sample data' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('You are not allowed to do this.');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
