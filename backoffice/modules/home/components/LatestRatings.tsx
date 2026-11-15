import Link from 'next/link';
import Card from 'react-bootstrap/Card';
import { useFetch } from '../../../common/hooks/useFetch';
import Stars from '../../rating/components/Stars';
import { customerName } from '../../rating/models/Rating';
import { getLatestRatings } from '../../rating/services/RatingService';

const LATEST_COUNT = 5;

/** Dashboard card with the newest reviews (YAS LatestRatings); the rest of the dashboard works without it. */
export default function LatestRatings() {
  const { data, error, loading } = useFetch(() => getLatestRatings(LATEST_COUNT));
  return (
    <section aria-labelledby="dashboard-latest-ratings" className="mb-4">
      <Card>
        <Card.Body>
          <div className="d-flex justify-content-between align-items-center mb-2">
            <h2 id="dashboard-latest-ratings" className="h5 m-0">
              Latest ratings
            </h2>
            <Link href="/reviews">All reviews</Link>
          </div>
          {loading && <p className="mb-0">Loading...</p>}
          {error && <p className="text-muted mb-0">Latest ratings are not available right now.</p>}
          {data && data.length === 0 && <p className="text-muted mb-0">No ratings yet.</p>}
          {data && data.length > 0 && (
            <ul className="list-unstyled mb-0">
              {data.map((rating) => (
                <li key={rating.id} className="border-bottom py-2">
                  <Stars value={rating.star} /> <strong>{rating.productName}</strong> by {customerName(rating)}{' '}
                  <small className="text-muted">{new Date(rating.createdOn).toLocaleString()}</small>
                  <div>{rating.content}</div>
                </li>
              ))}
            </ul>
          )}
        </Card.Body>
      </Card>
    </section>
  );
}
