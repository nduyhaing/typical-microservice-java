import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../../../common/services/ApiClientService';
import * as authService from '../../auth/services/AuthenticationService';
import { RatingList } from '../models/Rating';
import * as ratingService from '../services/RatingService';
import ProductReviews from './ProductReviews';

vi.mock('../services/RatingService');
vi.mock('../../auth/services/AuthenticationService', async (original) => ({
  ...(await original<typeof authService>()),
  getAuthentication: vi.fn(),
  redirectToLogin: vi.fn(),
}));

const rating = (id: number, star: number, content: string, firstName: string, lastName: string) => ({
  id,
  content,
  star,
  productId: 1,
  productName: 'Galaxy S24',
  createdBy: `c-${id}`,
  firstName,
  lastName,
  createdOn: '2026-11-10T12:00:00Z',
});

const twoReviews: RatingList = {
  ratingList: [rating(2, 5, 'Great camera', 'Lan', 'Nguyen'), rating(1, 4, 'Good battery', 'Minh', 'Tran')],
  totalElements: 2,
  totalPages: 1,
};

const loggedIn = () =>
  vi.mocked(authService.getAuthentication).mockResolvedValue({
    isAuthenticated: true,
    authenticatedUser: { username: 'customer' },
  });

const anonymous = () =>
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: false, authenticatedUser: null });

describe('ProductReviews', () => {
  afterEach(() => vi.resetAllMocks());

  it('shows the average, the count and the reviews', async () => {
    anonymous();
    render(<ProductReviews productId={1} initial={{ average: 4.5, ratings: twoReviews }} />);

    expect(screen.getByRole('heading', { name: 'Reviews' })).toBeInTheDocument();
    expect(screen.getByTestId('average-rating')).toHaveTextContent('4.5 out of 5');
    expect(screen.getByTestId('average-rating')).toHaveTextContent('2 reviews');
    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByText('Great camera')).toBeInTheDocument();
    expect(within(items[0]).getByText('Lan Nguyen')).toBeInTheDocument();
    expect(within(items[0]).getByLabelText('5 out of 5 stars')).toBeInTheDocument();
    expect(within(items[1]).getByLabelText('4 out of 5 stars')).toBeInTheDocument();
  });

  it('without reviews says so', async () => {
    anonymous();
    render(
      <ProductReviews productId={1} initial={{ average: 0, ratings: { ratingList: [], totalElements: 0, totalPages: 0 } }} />
    );

    expect(screen.getByText('No reviews yet.')).toBeInTheDocument();
    expect(screen.queryByTestId('average-rating')).not.toBeInTheDocument();
  });

  it('rating service down: a short message instead of the reviews', async () => {
    anonymous();
    render(<ProductReviews productId={1} initial={null} />);

    expect(screen.getByRole('heading', { name: 'Reviews' })).toBeInTheDocument();
    expect(screen.getByText('Reviews are not available right now.')).toBeInTheDocument();
  });

  it('anonymous visitors see a login hint, not the form', async () => {
    anonymous();
    render(<ProductReviews productId={1} initial={{ average: 4.5, ratings: twoReviews }} />);

    const login = await screen.findByRole('button', { name: 'Log in to write a review' });
    expect(screen.queryByLabelText('Your review')).not.toBeInTheDocument();
    await userEvent.click(login);
    expect(authService.redirectToLogin).toHaveBeenCalled();
  });

  it('a logged-in customer posts a review; the list and average reload', async () => {
    loggedIn();
    vi.mocked(ratingService.createRating).mockResolvedValue(rating(3, 3, 'Average', 'Hoa', 'Le'));
    vi.mocked(ratingService.getRatingsByProduct).mockResolvedValue({
      ratingList: [rating(3, 3, 'Average', 'Hoa', 'Le'), ...twoReviews.ratingList],
      totalElements: 3,
      totalPages: 1,
    });
    vi.mocked(ratingService.getAverageStar).mockResolvedValue(4);
    render(<ProductReviews productId={1} initial={{ average: 4.5, ratings: twoReviews }} />);

    await userEvent.selectOptions(await screen.findByLabelText('Your rating'), '3');
    await userEvent.type(screen.getByLabelText('Your review'), 'Average');
    await userEvent.click(screen.getByRole('button', { name: 'Submit review' }));

    expect(ratingService.createRating).toHaveBeenCalledWith({ productId: 1, star: 3, content: 'Average' });
    expect(await screen.findByText('Thank you for your review!')).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(3));
    expect(screen.getByTestId('average-rating')).toHaveTextContent('4.0 out of 5');
    expect(screen.getByTestId('average-rating')).toHaveTextContent('3 reviews');
    expect(ratingService.getRatingsByProduct).toHaveBeenCalledWith(1, 0, 5);
    // the form is gone after a review: one review per product
    expect(screen.queryByLabelText('Your review')).not.toBeInTheDocument();
  });

  it("shows the server's reason when the review is refused", async () => {
    loggedIn();
    vi.mocked(ratingService.createRating).mockRejectedValue(
      new ApiError(400, { detail: 'Only customers who bought this product can review it' })
    );
    render(<ProductReviews productId={1} initial={{ average: 4.5, ratings: twoReviews }} />);

    await userEvent.selectOptions(await screen.findByLabelText('Your rating'), '5');
    await userEvent.type(screen.getByLabelText('Your review'), 'Love it');
    await userEvent.click(screen.getByRole('button', { name: 'Submit review' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Only customers who bought this product can review it');
    expect(screen.getByLabelText('Your review')).toHaveValue('Love it');
  });

  it('a server failure shows a generic message', async () => {
    loggedIn();
    vi.mocked(ratingService.createRating).mockRejectedValue(new ApiError(500, { detail: 'Order service is down' }));
    render(<ProductReviews productId={1} initial={{ average: 4.5, ratings: twoReviews }} />);

    await userEvent.selectOptions(await screen.findByLabelText('Your rating'), '5');
    await userEvent.type(screen.getByLabelText('Your review'), 'Love it');
    await userEvent.click(screen.getByRole('button', { name: 'Submit review' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not send your review. Please try again later.');
  });

  it('a star and some text are required before sending', async () => {
    loggedIn();
    render(<ProductReviews productId={1} initial={{ average: 4.5, ratings: twoReviews }} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Submit review' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Choose a rating and write a short review.');
    expect(ratingService.createRating).not.toHaveBeenCalled();
  });

  it('pages through the reviews', async () => {
    anonymous();
    vi.mocked(ratingService.getRatingsByProduct).mockResolvedValue({
      ratingList: [rating(9, 1, 'Oldest review', 'Old', 'Timer')],
      totalElements: 6,
      totalPages: 2,
    });
    render(
      <ProductReviews productId={1} initial={{ average: 4, ratings: { ...twoReviews, totalElements: 6, totalPages: 2 } }} />
    );

    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(await screen.findByText('Oldest review')).toBeInTheDocument();
    expect(ratingService.getRatingsByProduct).toHaveBeenCalledWith(1, 1, 5);
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
  });
});
