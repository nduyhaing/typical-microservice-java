import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import ErrorMessage from '../../../common/components/ErrorMessage';
import { loadSampleData, SampleDataResult } from '../services/SampleDataService';

const CONFIRM_TEXT =
  'Load the sample catalog? Missing sample brands, categories and products are added and deleted sample products are published again. Your own products and edits are kept.';

/** Dashboard card with the "Load sample data" action (YAS: a storefront button). */
export default function SampleDataCard() {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<SampleDataResult>();
  const [error, setError] = useState<unknown>();

  async function load() {
    if (!window.confirm(CONFIRM_TEXT)) {
      return;
    }
    setLoading(true);
    setResult(undefined);
    setError(undefined);
    try {
      setResult(await loadSampleData());
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }

  return (
    <section aria-labelledby="dashboard-sample-data" className="mb-4">
      <Card>
        <Card.Body>
          <h2 id="dashboard-sample-data" className="h5">
            Sample data
          </h2>
          <p>
            Adds the sample brands, categories and products that are missing (for example after deleting them). Running it again
            adds nothing new.
          </p>
          <Button variant="outline-primary" onClick={load} disabled={loading}>
            {loading ? 'Loading...' : 'Load sample data'}
          </Button>
          {error !== undefined && (
            <div className="mt-3">
              <ErrorMessage error={error} />
            </div>
          )}
          {result && (
            <Alert variant="success" role="status" className="mt-3 mb-0">
              {result.message}
              <ul className="mb-0 mt-2">
                {Object.entries(result.inserted).map(([table, count]) => (
                  <li key={table}>
                    {table}: {count}
                  </li>
                ))}
                <li>republished products: {result.republished}</li>
              </ul>
            </Alert>
          )}
        </Card.Body>
      </Card>
    </section>
  );
}
