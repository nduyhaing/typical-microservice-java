import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import { useAuth } from '../../../common/hooks/useAuth';
import { redirectToLogin } from '../../auth/services/AuthenticationService';
import { ProductReviewsData, RatingList } from '../models/Rating';
import { getAverageStar, getRatingsByProduct, REVIEWS_PAGE_SIZE } from '../services/RatingService';
import PostRatingForm from './PostRatingForm';
import Stars from './Stars';

type Props = {
  productId: number;
  /** Loaded with the page; null when the rating service could not be reached. */
  initial: ProductReviewsData | null;
};

function authorName(firstName: string | null, lastName: string | null): string {
  return [firstName, lastName].filter((part) => part && part.trim()).join(' ') || 'Customer';
}

/** The product page's "Reviews" section: average, paged reviews and the write-a-review form. */
export default function ProductReviews({ productId, initial }: Props) {
  const { authentication } = useAuth();
  const [average, setAverage] = useState(initial?.average ?? 0);
  const [ratings, setRatings] = useState<RatingList | undefined>(initial?.ratings);
  const [pageNo, setPageNo] = useState(0);
  const [loadFailed, setLoadFailed] = useState(false);
  const [posted, setPosted] = useState(false);

  const loadPage = async (page: number) => {
    try {
      setRatings(await getRatingsByProduct(productId, page, REVIEWS_PAGE_SIZE));
      setPageNo(page);
      setLoadFailed(false);
    } catch {
      setLoadFailed(true);
    }
  };

  const onPosted = async () => {
    setPosted(true);
    await loadPage(0);
    try {
      setAverage(await getAverageStar(productId));
    } catch {
      // the list already shows the new review; the average catches up on the next visit
    }
  };

  if (!ratings) {
    return (
      <section className="mt-5">
        <h2 className="h4">Reviews</h2>
        <p className="text-muted">Reviews are not available right now.</p>
      </section>
    );
  }

  return (
    <section className="mt-5">
      <h2 className="h4">Reviews</h2>
      {ratings.totalElements === 0 ? (
        <p className="text-muted">No reviews yet.</p>
      ) : (
        <p data-testid="average-rating">
          <Stars value={average} /> {average.toFixed(1)} out of 5 ({ratings.totalElements}{' '}
          {ratings.totalElements === 1 ? 'review' : 'reviews'})
        </p>
      )}
      {loadFailed && <p className="text-muted">Could not load more reviews. Please try again later.</p>}
      {ratings.ratingList.length > 0 && (
        <ul className="list-unstyled">
          {ratings.ratingList.map((rating) => (
            <li key={rating.id} className="border-bottom py-2">
              <div>
                <Stars value={rating.star} />{' '}
                <strong>{authorName(rating.firstName, rating.lastName)}</strong>{' '}
                <small className="text-muted">{new Date(rating.createdOn).toLocaleDateString('en-US')}</small>
              </div>
              <p className="mb-0">{rating.content}</p>
            </li>
          ))}
        </ul>
      )}
      {ratings.totalPages > 1 && (
        <div className="d-flex align-items-center gap-2">
          <Button variant="outline-secondary" size="sm" disabled={pageNo === 0} onClick={() => loadPage(pageNo - 1)}>
            Previous
          </Button>
          <span>
            Page {pageNo + 1} of {ratings.totalPages}
          </span>
          <Button
            variant="outline-secondary"
            size="sm"
            disabled={pageNo + 1 >= ratings.totalPages}
            onClick={() => loadPage(pageNo + 1)}
          >
            Next
          </Button>
        </div>
      )}
      {posted && (
        <Alert variant="success" className="mt-3">
          Thank you for your review!
        </Alert>
      )}
      {!posted && authentication?.isAuthenticated && <PostRatingForm productId={productId} onPosted={onPosted} />}
      {!posted && authentication && !authentication.isAuthenticated && (
        <p className="mt-3">
          <Button variant="link" className="p-0" onClick={redirectToLogin}>
            Log in to write a review
          </Button>
        </p>
      )}
    </section>
  );
}
