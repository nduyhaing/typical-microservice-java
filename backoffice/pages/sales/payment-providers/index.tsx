import { useState } from 'react';
import Badge from 'react-bootstrap/Badge';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import Table from 'react-bootstrap/Table';
import ErrorMessage from '../../../common/components/ErrorMessage';
import Layout from '../../../common/components/Layout';
import { useFetch } from '../../../common/hooks/useFetch';
import { PaymentProvider } from '../../../modules/payment/models/Payment';
import { getPaymentProviders, updatePaymentProvider } from '../../../modules/payment/services/PaymentService';

function availability(provider: PaymentProvider) {
  if (provider.available) {
    return <Badge bg="success">Offered</Badge>;
  }
  if (provider.enabled && provider.id === 'PAYPAL') {
    return <Badge bg="warning" text="dark">Not configured (PAYPAL_CLIENT_ID / SECRET)</Badge>;
  }
  return <Badge bg="secondary">Not offered</Badge>;
}

/**
 * Payment providers (YAS backoffice pages/system/payment-providers, which only showed placeholder rows): the admin
 * enables/disables a provider and renames it; whether customers are offered it also depends on the payment service
 * (PayPal needs its credentials in .env) (decisions.md 5-19). Credentials are never edited here.
 */
export default function PaymentProvidersPage() {
  const { data, error, loading } = useFetch(getPaymentProviders);
  const [changed, setChanged] = useState<Record<string, PaymentProvider>>({});
  const [editing, setEditing] = useState<PaymentProvider>();
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<unknown>();

  async function save() {
    if (!editing) {
      return;
    }
    setSaving(true);
    setSaveError(undefined);
    try {
      const saved = await updatePaymentProvider({ ...editing, name: editing.name.trim() });
      setChanged((current) => ({ ...current, [saved.id]: saved }));
      setEditing(undefined);
    } catch (e) {
      setSaveError(e);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Layout title="Payment providers">
      <h1 className="h3 mb-3">Payment providers</h1>
      {loading && <p>Loading...</p>}
      {error && <ErrorMessage error={error} />}
      {saveError !== undefined && <ErrorMessage error={saveError} />}
      {data && (
        <Table striped hover responsive>
          <thead>
            <tr>
              <th>Id</th>
              <th>Name</th>
              <th>Enabled</th>
              <th>Customers</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data
              .map((listed) => changed[listed.id] ?? listed)
              .map((provider) => {
                const edited = editing?.id === provider.id ? editing : undefined;
                return (
                  <tr key={provider.id}>
                    <td>
                      <code>{provider.id}</code>
                    </td>
                    <td>
                      {edited ? (
                        <Form.Control
                          aria-label="Name"
                          size="sm"
                          maxLength={255}
                          value={edited.name}
                          onChange={(e) => setEditing({ ...edited, name: e.target.value })}
                        />
                      ) : (
                        provider.name
                      )}
                    </td>
                    <td>
                      <Form.Check
                        type="switch"
                        id={`provider-enabled-${provider.id}`}
                        aria-label="Enabled"
                        disabled={!edited}
                        checked={(edited ?? provider).enabled}
                        onChange={(e) => edited && setEditing({ ...edited, enabled: e.target.checked })}
                      />
                    </td>
                    <td>{availability(provider)}</td>
                    <td className="text-end text-nowrap">
                      {edited ? (
                        <>
                          <Button size="sm" onClick={save} disabled={saving}>
                            Save
                          </Button>{' '}
                          <Button size="sm" variant="outline-secondary" onClick={() => setEditing(undefined)}>
                            Cancel
                          </Button>
                        </>
                      ) : (
                        <Button
                          size="sm"
                          variant="outline-secondary"
                          onClick={() => {
                            setSaveError(undefined);
                            setEditing(provider);
                          }}
                        >
                          Edit
                        </Button>
                      )}
                    </td>
                  </tr>
                );
              })}
          </tbody>
        </Table>
      )}
    </Layout>
  );
}
