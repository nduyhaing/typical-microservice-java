import { fireEvent, render, screen, within } from '@testing-library/react';
import { GetServerSidePropsContext } from 'next';
import { ReactNode } from 'react';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import { ProductListGet } from '../modules/search/models/ProductSearch';
import * as searchService from '../modules/search/services/SearchService';
import { parseSearchQuery } from '../modules/search/utils/searchQuery';
import SearchPage, { getServerSideProps } from '../pages/search';

vi.mock('../common/components/Layout', () => ({ default: ({ children }: { children: ReactNode }) => <>{children}</> }));
vi.mock('../modules/search/services/SearchService');
const push = vi.fn();
vi.mock('next/router', () => ({ useRouter: () => ({ push }) }));

const hit = (id: number, name: string, price: number | null = 100) => ({
  id,
  name,
  slug: name.toLowerCase().replace(/ /g, '-'),
  thumbnailId: null,
  thumbnailUrl: null,
  price,
  isAllowedToOrder: true,
  isPublished: true,
  isFeatured: false,
  isVisibleIndividually: true,
  createdOn: '2026-11-15T00:00:00Z',
});

const result = (overrides: Partial<ProductListGet> = {}): ProductListGet => ({
  products: [hit(1, 'Galaxy S24', 799), hit(2, 'Galaxy Tab', 499)],
  pageNo: 0,
  pageSize: 12,
  totalElements: 2,
  totalPages: 1,
  isLast: true,
  aggregations: { brands: { Samsung: 2, Apple: 1 }, categories: { Phones: 1, Tablets: 1 }, attributes: {} },
  ...overrides,
});

const context = (query: Record<string, string | string[]>) => ({ query }) as unknown as GetServerSidePropsContext;

const renderPage = (query: Record<string, string | string[]>, data: ProductListGet | null = result()) =>
  render(<SearchPage params={parseSearchQuery(query)} result={data} />);

describe('search page getServerSideProps', () => {
  afterEach(() => vi.resetAllMocks());

  it('searches with the parsed URL parameters', async () => {
    vi.mocked(searchService.searchProducts).mockResolvedValue(result());

    const props = await getServerSideProps(context({ keyword: 'galaxy', brand: 'Samsung', sortType: 'PRICE_ASC' }));

    expect(searchService.searchProducts).toHaveBeenCalledWith(
      expect.objectContaining({ keyword: 'galaxy', brand: ['Samsung'], sortType: 'PRICE_ASC', page: 0 })
    );
    expect(props).toEqual({
      props: { params: expect.objectContaining({ keyword: 'galaxy' }), result: result() },
    });
  });

  it('search service failure gives result null (the page renders its error state), never an error page', async () => {
    vi.mocked(searchService.searchProducts).mockRejectedValue(new ApiError(503, {}));

    const props = await getServerSideProps(context({ keyword: 'galaxy' }));

    expect(props).toEqual({ props: { params: expect.objectContaining({ keyword: 'galaxy' }), result: null } });
  });

  it('props are JSON-serializable (no undefined values)', async () => {
    vi.mocked(searchService.searchProducts).mockResolvedValue(result());

    const props = (await getServerSideProps(context({}))) as { props: unknown };

    expect(JSON.parse(JSON.stringify(props.props))).toEqual(props.props);
  });
});

describe('search page', () => {
  afterEach(() => vi.resetAllMocks());

  it('renders the results with the product card and the count', () => {
    renderPage({ keyword: 'galaxy' });

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Results for "galaxy"');
    expect(screen.getByText('2 products')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Galaxy S24' })).toContainElement(
      screen.getAllByRole('link', { name: 'Galaxy S24' })[1]
    );
    expect(screen.getAllByRole('link', { name: 'Galaxy Tab' })[1]).toHaveAttribute('href', '/products/galaxy-tab');
    expect(screen.getByText('$799.00')).toBeInTheDocument();
  });

  it('shows brand and category facets with counts; no attribute section when there are none', () => {
    renderPage({ keyword: 'galaxy', brand: 'Samsung' });

    const brands = screen.getByRole('group', { name: 'Brand' });
    expect(within(brands).getByRole('checkbox', { name: 'Samsung (2)' })).toBeChecked();
    expect(within(brands).getByRole('checkbox', { name: 'Apple (1)' })).not.toBeChecked();
    expect(screen.getByRole('group', { name: 'Category' })).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Attribute' })).not.toBeInTheDocument();
  });

  it('checking a facet adds it to the URL and goes back to the first page', () => {
    renderPage({ keyword: 'galaxy', brand: 'Samsung', page: '2' });

    fireEvent.click(screen.getByRole('checkbox', { name: 'Apple (1)' }));

    expect(push).toHaveBeenCalledWith({
      pathname: '/search',
      query: { keyword: 'galaxy', brand: ['Samsung', 'Apple'] },
    });
  });

  it('unchecking a facet removes it from the URL', () => {
    renderPage({ keyword: 'galaxy', brand: 'Samsung', category: 'Phones' });

    fireEvent.click(screen.getByRole('checkbox', { name: 'Phones (1)' }));

    expect(push).toHaveBeenCalledWith({ pathname: '/search', query: { keyword: 'galaxy', brand: ['Samsung'] } });
  });

  it('a selected value missing from the facets stays visible so it can be unchecked', () => {
    renderPage({ brand: 'Nokia' });

    expect(screen.getByRole('checkbox', { name: 'Nokia' })).toBeChecked();
  });

  it('price range goes to the URL', () => {
    renderPage({ keyword: 'galaxy', page: '1' });

    fireEvent.change(screen.getByLabelText('Min price'), { target: { value: '100' } });
    fireEvent.change(screen.getByLabelText('Max price'), { target: { value: '500' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply price' }));

    expect(push).toHaveBeenCalledWith({
      pathname: '/search',
      query: { keyword: 'galaxy', minPrice: '100', maxPrice: '500' },
    });
  });

  it('min above max is refused with a message', () => {
    renderPage({ keyword: 'galaxy' });

    fireEvent.change(screen.getByLabelText('Min price'), { target: { value: '500' } });
    fireEvent.change(screen.getByLabelText('Max price'), { target: { value: '100' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply price' }));

    expect(push).not.toHaveBeenCalled();
    expect(screen.getByText('Min price must not be above max price.')).toBeInTheDocument();
  });

  it('price inputs show the URL values', () => {
    renderPage({ minPrice: '10', maxPrice: '20' });

    expect(screen.getByLabelText('Min price')).toHaveValue(10);
    expect(screen.getByLabelText('Max price')).toHaveValue(20);
  });

  it('sort select goes to the URL', () => {
    renderPage({ keyword: 'galaxy', page: '1' });

    expect(screen.getByLabelText('Sort by')).toHaveValue('DEFAULT');
    fireEvent.change(screen.getByLabelText('Sort by'), { target: { value: 'PRICE_ASC' } });

    expect(push).toHaveBeenCalledWith({ pathname: '/search', query: { keyword: 'galaxy', sortType: 'PRICE_ASC' } });
  });

  it('paging keeps the other parameters', () => {
    renderPage({ keyword: 'galaxy', brand: 'Samsung' }, result({ totalElements: 30, totalPages: 3, isLast: false }));

    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));

    expect(push).toHaveBeenCalledWith({
      pathname: '/search',
      query: { keyword: 'galaxy', brand: ['Samsung'], page: '1' },
    });
  });

  it('page links go to the chosen page; previous is disabled on the first page', () => {
    renderPage({ keyword: 'galaxy', page: '1' }, result({ pageNo: 1, totalElements: 30, totalPages: 3 }));

    fireEvent.click(screen.getByRole('button', { name: '3' }));
    expect(push).toHaveBeenLastCalledWith({ pathname: '/search', query: { keyword: 'galaxy', page: '2' } });

    fireEvent.click(screen.getByRole('button', { name: 'Previous page' }));
    expect(push).toHaveBeenLastCalledWith({ pathname: '/search', query: { keyword: 'galaxy' } });
  });

  it('no hits shows the empty state', () => {
    renderPage({ keyword: 'zzz' }, result({ products: [], totalElements: 0, totalPages: 0 }));

    expect(screen.getByText('No products found.')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Search result pages' })).not.toBeInTheDocument();
  });

  it('search service down shows the error state', () => {
    renderPage({ keyword: 'galaxy' }, null);

    expect(screen.getByRole('alert')).toHaveTextContent('Search is not available right now');
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });
});
