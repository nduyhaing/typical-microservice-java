import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import LatestRatings from '../modules/home/components/LatestRatings';
import { Rating } from '../modules/rating/models/Rating';
import * as ratingService from '../modules/rating/services/RatingService';
import ReviewsPage from '../pages/reviews/index';

vi.mock('next/router', () => ({ useRouter: () => ({ push: vi.fn(), query: {}, isReady: true }) }));
vi.mock('../modules/auth/services/AuthenticationService', () => ({ LOGOUT_URL: '/logout', getAuthentication: vi.fn() }));
vi.mock('../modules/rating/services/RatingService');

const rating = (id: number, patch: Partial<Rating> = {}): Rating => ({
  id,
  content: `Review ${id}`,
  star: 4,
  productId: 10,
  productName: 'Galaxy S24',
  createdBy: 'c-1',
  firstName: 'Lan',
  lastName: 'Nguyen',
  createdOn: '2026-11-10T12:00:00Z',
  ...patch,
});

const page = (list: Rating[], patch = {}) => ({
  ratingList: list,
  pageNo: 0,
  pageSize: 10,
  totalElements: list.length,
  totalPages: 1,
  isLast: true,
  ...patch,
});

beforeEach(() => {
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: true, authenticatedUser: { username: 'admin' } });
});
afterEach(() => vi.resetAllMocks());

describe('Reviews page', () => {
  it('lists reviews with product, customer, stars, message and date', async () => {
    vi.mocked(ratingService.getRatings).mockResolvedValue(
      page([rating(1, { content: 'Great camera', star: 5 }), rating(2, { productName: 'USB-C Cable', firstName: 'Minh', lastName: 'Tran' })])
    );

    render(<ReviewsPage />);

    const rows = await screen.findAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(within(rows[1]).getByText('Galaxy S24')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Lan Nguyen')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Great camera')).toBeInTheDocument();
    expect(within(rows[1]).getByLabelText('5 out of 5 stars')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Minh Tran')).toBeInTheDocument();
    expect(ratingService.getRatings).toHaveBeenCalledWith(0, {
      productName: '',
      customerName: '',
      message: '',
      createdFrom: '',
      createdTo: '',
    });
  });

  it('searches with the filters from the first page', async () => {
    vi.mocked(ratingService.getRatings).mockResolvedValue(page([rating(1)]));
    render(<ReviewsPage />);
    await screen.findByText('Review 1');

    await userEvent.type(screen.getByLabelText('Product name'), ' Galaxy ');
    await userEvent.type(screen.getByLabelText('Customer name'), 'Lan');
    await userEvent.type(screen.getByLabelText('Message'), 'camera');
    await userEvent.type(screen.getByLabelText('Created from'), '2026-11-01');
    await userEvent.type(screen.getByLabelText('Created to'), '2026-11-09');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() =>
      expect(ratingService.getRatings).toHaveBeenLastCalledWith(0, {
        productName: 'Galaxy',
        customerName: 'Lan',
        message: 'camera',
        createdFrom: '2026-11-01',
        createdTo: '2026-11-09',
      })
    );
  });

  it('pages', async () => {
    vi.mocked(ratingService.getRatings).mockResolvedValue(page([rating(1)], { totalElements: 11, totalPages: 2, isLast: false }));
    render(<ReviewsPage />);
    await screen.findByText('Review 1');

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    await waitFor(() => expect(ratingService.getRatings).toHaveBeenLastCalledWith(1, expect.anything()));
  });

  it('deletes after a confirmation and reloads', async () => {
    vi.mocked(ratingService.getRatings).mockResolvedValue(page([rating(1)]));
    vi.mocked(ratingService.deleteRating).mockResolvedValue(null);
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    render(<ReviewsPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    expect(confirm).toHaveBeenCalledWith('Delete review "Review 1"?');
    expect(ratingService.deleteRating).toHaveBeenCalledWith(1);
    expect(await screen.findByText('Review "Review 1" deleted.')).toBeInTheDocument();
    expect(ratingService.getRatings).toHaveBeenCalledTimes(2);
    confirm.mockRestore();
  });

  it('cancelling the confirmation deletes nothing', async () => {
    vi.mocked(ratingService.getRatings).mockResolvedValue(page([rating(1)]));
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    render(<ReviewsPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    expect(ratingService.deleteRating).not.toHaveBeenCalled();
    confirm.mockRestore();
  });

  it('shows the error when the list cannot be loaded', async () => {
    vi.mocked(ratingService.getRatings).mockRejectedValue(new ApiError(400, { detail: 'Request information is not valid' }));

    render(<ReviewsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Request information is not valid');
  });

  it('says so when nothing matches', async () => {
    vi.mocked(ratingService.getRatings).mockResolvedValue(page([]));

    render(<ReviewsPage />);

    expect(await screen.findByText('No reviews found.')).toBeInTheDocument();
  });
});

describe('Latest ratings card', () => {
  it('shows the newest ratings with product and customer', async () => {
    vi.mocked(ratingService.getLatestRatings).mockResolvedValue([
      rating(3, { content: 'Newest', star: 2 }),
      rating(2, { content: 'Older', productName: 'USB-C Cable' }),
    ]);

    render(<LatestRatings />);

    expect(screen.getByRole('heading', { name: 'Latest ratings' })).toBeInTheDocument();
    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByText('Newest')).toBeInTheDocument();
    expect(within(items[0]).getByText(/Galaxy S24/)).toBeInTheDocument();
    expect(within(items[0]).getByLabelText('2 out of 5 stars')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'All reviews' })).toHaveAttribute('href', '/reviews');
    expect(ratingService.getLatestRatings).toHaveBeenCalledWith(5);
  });

  it('rating service down: a short message, the dashboard still works', async () => {
    vi.mocked(ratingService.getLatestRatings).mockRejectedValue(new ApiError(502, 'Bad Gateway'));

    render(<LatestRatings />);

    expect(await screen.findByText('Latest ratings are not available right now.')).toBeInTheDocument();
  });

  it('no ratings yet', async () => {
    vi.mocked(ratingService.getLatestRatings).mockResolvedValue([]);

    render(<LatestRatings />);

    expect(await screen.findByText('No ratings yet.')).toBeInTheDocument();
  });
});
