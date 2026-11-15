import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
import Row from 'react-bootstrap/Row';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../common/components/ErrorMessage';
import Layout from '../../common/components/Layout';
import Pager from '../../common/components/Pager';
import { useDeleteAction } from '../../common/hooks/useDeleteAction';
import { useFetch } from '../../common/hooks/useFetch';
import Stars from '../../modules/rating/components/Stars';
import { customerName, RatingFilter } from '../../modules/rating/models/Rating';
import { deleteRating, getRatings } from '../../modules/rating/services/RatingService';

const NO_FILTER: RatingFilter = { productName: '', customerName: '', message: '', createdFrom: '', createdTo: '' };

/** Reviews (YAS backoffice pages/reviews): filter by product, customer, message and dates; delete. */
export default function ReviewsPage() {
  const [pageNo, setPageNo] = useState(0);
  const [version, setVersion] = useState(0);
  const [filter, setFilter] = useState<RatingFilter>(NO_FILTER);
  const [draft, setDraft] = useState<RatingFilter>(NO_FILTER);
  const { data, error, loading } = useFetch(() => getRatings(pageNo, filter), [pageNo, filter, version]);
  const deletion = useDeleteAction('review', () => setVersion((v) => v + 1));

  function search(event: FormEvent) {
    event.preventDefault();
    setPageNo(0);
    setFilter({
      productName: draft.productName.trim(),
      customerName: draft.customerName.trim(),
      message: draft.message.trim(),
      createdFrom: draft.createdFrom,
      createdTo: draft.createdTo,
    });
  }

  const text = (key: 'productName' | 'customerName' | 'message', label: string) => (
    <Form.Group as={Col} md={3} controlId={`review-filter-${key}`}>
      <Form.Label>{label}</Form.Label>
      <Form.Control value={draft[key]} maxLength={255} onChange={(e) => setDraft({ ...draft, [key]: e.target.value })} />
    </Form.Group>
  );

  return (
    <Layout title="Reviews">
      <h1 className="h3 mb-3">Reviews</h1>
      <Form onSubmit={search} className="mb-3">
        <Row className="g-2 align-items-end">
          {text('productName', 'Product name')}
          {text('customerName', 'Customer name')}
          {text('message', 'Message')}
        </Row>
        <Row className="g-2 align-items-end mt-1">
          <Form.Group as={Col} md={3} controlId="review-filter-from">
            <Form.Label>Created from</Form.Label>
            <Form.Control
              type="date"
              value={draft.createdFrom}
              onChange={(e) => setDraft({ ...draft, createdFrom: e.target.value })}
            />
          </Form.Group>
          <Form.Group as={Col} md={3} controlId="review-filter-to">
            <Form.Label>Created to</Form.Label>
            <Form.Control type="date" value={draft.createdTo} onChange={(e) => setDraft({ ...draft, createdTo: e.target.value })} />
          </Form.Group>
          <Col md={2}>
            <Button type="submit" variant="outline-primary">
              Search
            </Button>
          </Col>
        </Row>
      </Form>
      {deletion.notice && <Alert variant="success">{deletion.notice}</Alert>}
      {deletion.error !== undefined && <ErrorMessage error={deletion.error} />}
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {data && data.ratingList.length === 0 && <p>No reviews found.</p>}
      {data && data.ratingList.length > 0 && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Product</th>
              <th>Customer</th>
              <th>Stars</th>
              <th>Message</th>
              <th>Created</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.ratingList.map((rating) => (
              <tr key={rating.id}>
                <td>{rating.productName}</td>
                <td>{customerName(rating)}</td>
                <td>
                  <Stars value={rating.star} />
                </td>
                <td className="text-break">{rating.content}</td>
                <td className="small text-nowrap">{new Date(rating.createdOn).toLocaleString()}</td>
                <td className="text-end">
                  <Button
                    size="sm"
                    variant="outline-danger"
                    onClick={() => deletion.remove(rating.content.slice(0, 40), () => deleteRating(rating.id))}
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
      {data && <Pager page={data} noun="reviews" onChange={setPageNo} />}
    </Layout>
  );
}
