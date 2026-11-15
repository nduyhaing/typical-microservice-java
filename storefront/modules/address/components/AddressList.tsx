import Badge from 'react-bootstrap/Badge';
import Button from 'react-bootstrap/Button';
import Card from 'react-bootstrap/Card';
import { Address, formatAddress } from '../models/Address';

type Props = {
  addresses: Address[];
  busy?: boolean;
  onEdit: (address: Address) => void;
  onDelete: (address: Address) => void;
  onMakeDefault: (address: Address) => void;
};

/** Saved addresses as cards; the default one has a badge and no "Make default" button. */
export default function AddressList({ addresses, busy, onEdit, onDelete, onMakeDefault }: Props) {
  if (addresses.length === 0) {
    return <p>You have no saved addresses yet.</p>;
  }
  return (
    <div className="d-grid gap-3">
      {addresses.map((address) => (
        <Card key={address.id} data-testid={`address-${address.id}`}>
          <Card.Body className="d-flex flex-column flex-md-row justify-content-between gap-2">
            <div>
              <Card.Title className="h6 mb-1">
                {address.contactName}
                {address.isActive && (
                  <Badge bg="success" className="ms-2">
                    Default
                  </Badge>
                )}
              </Card.Title>
              <div className="text-muted small">{address.phone}</div>
              <div>{formatAddress(address)}</div>
            </div>
            <div className="d-flex gap-2 align-items-start flex-wrap">
              {!address.isActive && (
                <Button
                  size="sm"
                  variant="outline-success"
                  disabled={busy}
                  onClick={() => onMakeDefault(address)}
                  aria-label={`Make ${address.contactName} default`}
                >
                  Make default
                </Button>
              )}
              <Button
                size="sm"
                variant="outline-primary"
                disabled={busy}
                onClick={() => onEdit(address)}
                aria-label={`Edit ${address.contactName}`}
              >
                Edit
              </Button>
              <Button
                size="sm"
                variant="outline-danger"
                disabled={busy}
                onClick={() => onDelete(address)}
                aria-label={`Delete ${address.contactName}`}
              >
                Delete
              </Button>
            </div>
          </Card.Body>
        </Card>
      ))}
    </div>
  );
}
