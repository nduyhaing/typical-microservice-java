import { FormEvent, useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import { ApiError } from '../../../common/services/ApiClientService';
import { createRating } from '../services/RatingService';

const MAX_CONTENT = 1000;

/** The server's reason for a 4xx (e.g. "Only customers who bought this product can review it"). */
function refusalReason(error: unknown): string {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
    const detail = (error.body as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string') {
      return detail;
    }
  }
  return 'Could not send your review. Please try again later.';
}

type Props = { productId: number; onPosted: () => void };

/** Write-a-review form (YAS PostRatingForm); the server decides whether the customer may review the product. */
export default function PostRatingForm({ productId, onPosted }: Props) {
  const [star, setStar] = useState('');
  const [content, setContent] = useState('');
  const [error, setError] = useState<string>();
  const [sending, setSending] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!star || !content.trim()) {
      setError('Choose a rating and write a short review.');
      return;
    }
    setError(undefined);
    setSending(true);
    try {
      await createRating({ productId, star: Number(star), content: content.trim() });
      onPosted();
    } catch (failure) {
      setError(refusalReason(failure));
    } finally {
      setSending(false);
    }
  };

  return (
    <Form onSubmit={submit} noValidate className="mt-4">
      <h3 className="h5">Write a review</h3>
      {error && (
        <Alert variant="danger" role="alert">
          {error}
        </Alert>
      )}
      <Form.Group className="mb-3" controlId="review-star">
        <Form.Label>Your rating</Form.Label>
        <Form.Select value={star} onChange={(event) => setStar(event.target.value)}>
          <option value="">Choose…</option>
          {[5, 4, 3, 2, 1].map((value) => (
            <option key={value} value={value}>
              {value} {value === 1 ? 'star' : 'stars'}
            </option>
          ))}
        </Form.Select>
      </Form.Group>
      <Form.Group className="mb-3" controlId="review-content">
        <Form.Label>Your review</Form.Label>
        <Form.Control
          as="textarea"
          rows={3}
          maxLength={MAX_CONTENT}
          value={content}
          onChange={(event) => setContent(event.target.value)}
        />
      </Form.Group>
      <Button type="submit" disabled={sending}>
        Submit review
      </Button>
    </Form>
  );
}
