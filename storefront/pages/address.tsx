import { useState } from 'react';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import Spinner from 'react-bootstrap/Spinner';
import ErrorMessage from '../common/components/ErrorMessage';
import Layout from '../common/components/Layout';
import { useFetch } from '../common/hooks/useFetch';
import { ApiError } from '../common/services/ApiClientService';
import { LOGIN_URL } from '../modules/auth/services/AuthenticationService';
import AddressForm from '../modules/address/components/AddressForm';
import AddressList from '../modules/address/components/AddressList';
import { Address, AddressPayload } from '../modules/address/models/Address';
import {
  chooseDefaultAddress,
  createUserAddress,
  deleteUserAddress,
  getUserAddresses,
  updateAddress,
} from '../modules/address/services/AddressService';

type Mode = { kind: 'list' } | { kind: 'create' } | { kind: 'edit'; address: Address };

/** The customer's address book (YAS /address): list with the default first, create, edit, delete, make default. */
export default function AddressPage() {
  const [version, setVersion] = useState(0);
  const [mode, setMode] = useState<Mode>({ kind: 'list' });
  const [busy, setBusy] = useState(false);
  const [actionFailed, setActionFailed] = useState(false);
  const { data: addresses, error, loading } = useFetch(getUserAddresses, [version]);
  const notLoggedIn = error instanceof ApiError && (error.status === 401 || error.status === 403);

  const reload = () => setVersion((current) => current + 1);

  const save = async (payload: AddressPayload) => {
    if (mode.kind === 'edit') {
      await updateAddress(mode.address.id, payload);
    } else {
      await createUserAddress(payload);
    }
    setMode({ kind: 'list' });
    reload();
  };

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setActionFailed(false);
    try {
      await action();
    } catch {
      setActionFailed(true);
    } finally {
      setBusy(false);
      reload();
    }
  };

  const remove = (address: Address) => {
    if (window.confirm(`Delete the address of ${address.contactName}?`)) {
      void run(() => deleteUserAddress(address.id));
    }
  };

  return (
    <Layout title="Address book">
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h1 className="h3 mb-0">Address book</h1>
        {addresses && mode.kind === 'list' && (
          <Button onClick={() => setMode({ kind: 'create' })}>Add address</Button>
        )}
      </div>
      {loading && <Spinner animation="border" role="status" aria-label="Loading" />}
      {notLoggedIn && <a href={LOGIN_URL}>Log in to see your addresses</a>}
      {error && !notLoggedIn && <ErrorMessage />}
      {actionFailed && <ErrorMessage message="The address could not be changed. Please try again later." />}
      {mode.kind !== 'list' && (
        <Card className="mb-4">
          <Card.Body>
            <Card.Title className="h5">{mode.kind === 'edit' ? 'Edit address' : 'New address'}</Card.Title>
            <AddressForm
              key={mode.kind === 'edit' ? mode.address.id : 'new'}
              initial={mode.kind === 'edit' ? mode.address : undefined}
              onSubmit={save}
              onCancel={() => setMode({ kind: 'list' })}
            />
          </Card.Body>
        </Card>
      )}
      {addresses && (
        <AddressList
          addresses={addresses}
          busy={busy}
          onEdit={(address) => setMode({ kind: 'edit', address })}
          onDelete={remove}
          onMakeDefault={(address) => void run(() => chooseDefaultAddress(address.id))}
        />
      )}
    </Layout>
  );
}
