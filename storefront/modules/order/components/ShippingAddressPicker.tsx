import Link from 'next/link';
import Badge from 'react-bootstrap/Badge';
import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import AddressForm from '../../address/components/AddressForm';
import { Address, AddressPayload, formatAddress } from '../../address/models/Address';

/**
 * The customer's pick: a saved address, or a new one (`address` once the form is confirmed; `draft` prefills the
 * form when the customer changes a confirmed new address).
 */
export type ShippingChoice =
  | { kind: 'saved'; id: number }
  | { kind: 'new'; address?: AddressPayload; draft?: AddressPayload };

/** The default saved address (else the first one), or a new address when there are none. */
export function defaultShippingChoice(addresses: Address[]): ShippingChoice {
  const preferred = addresses.find((address) => address.isActive) ?? addresses[0];
  return preferred ? { kind: 'saved', id: preferred.id } : { kind: 'new' };
}

/** The AddressForm's `initial` for editing a typed address again (the form only reads the ids and texts). */
function asAddress(payload: AddressPayload): Address {
  return {
    ...payload,
    id: 0,
    districtName: null,
    stateOrProvinceName: null,
    countryName: '',
    isActive: false,
  };
}

type Props = {
  addresses: Address[];
  choice: ShippingChoice;
  onChange: (choice: ShippingChoice) => void;
};

/**
 * Checkout shipping address (decisions.md 3c-48): the saved addresses as radio buttons (the default one preselected
 * by the page) or "Use a new address" with the address book's form (country -> state/province -> district).
 */
export default function ShippingAddressPicker({ addresses, choice, onChange }: Props) {
  return (
    <div className="mb-3">
      {addresses.map((address) => (
        <Form.Check
          key={address.id}
          type="radio"
          id={`shipping-address-${address.id}`}
          name="shipping-address"
          className="mb-2"
          checked={choice.kind === 'saved' && choice.id === address.id}
          onChange={() => onChange({ kind: 'saved', id: address.id })}
          label={
            <span>
              <strong>{address.contactName}</strong> ({address.phone}) — {formatAddress(address)}{' '}
              {address.isActive && <Badge bg="secondary">Default</Badge>}
            </span>
          }
        />
      ))}
      {addresses.length > 0 && (
        <Form.Check
          type="radio"
          id="shipping-address-new"
          name="shipping-address"
          className="mb-2"
          checked={choice.kind === 'new'}
          onChange={() => onChange({ kind: 'new' })}
          label="Use a new address"
        />
      )}
      {choice.kind === 'new' && choice.address && (
        <div className="border rounded p-2 mb-2" data-testid="new-address">
          {[choice.address.contactName, choice.address.addressLine1, choice.address.city, choice.address.zipCode]
            .filter((part) => part)
            .join(', ')}{' '}
          <Button variant="link" size="sm" onClick={() => onChange({ kind: 'new', draft: choice.address })}>
            Change
          </Button>
        </div>
      )}
      {choice.kind === 'new' && !choice.address && (
        <div className="border rounded p-3 mb-2">
          <AddressForm
            initial={choice.draft ? asAddress(choice.draft) : undefined}
            submitLabel="Use this address"
            onSubmit={async (payload) => onChange({ kind: 'new', address: payload })}
          />
        </div>
      )}
      <p className="small text-muted mb-0">
        <Link href="/address">Manage my addresses</Link>
      </p>
    </div>
  );
}
