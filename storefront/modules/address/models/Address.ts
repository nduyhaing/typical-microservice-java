export type Country = { id: number; code2: string; name: string };

export type StateOrProvince = { id: number; name: string; countryId: number };

export type District = { id: number; name: string };

/** A saved address in the address book (customer `ActiveAddressVm`); `id` is the location address id. */
export type Address = {
  id: number;
  contactName: string;
  phone: string;
  addressLine1: string;
  addressLine2: string | null;
  city: string | null;
  zipCode: string | null;
  districtId: number | null;
  districtName: string | null;
  stateOrProvinceId: number | null;
  stateOrProvinceName: string | null;
  countryId: number;
  countryName: string;
  isActive: boolean;
};

/** Create/update body (customer and location `AddressPostVm`). */
export type AddressPayload = {
  contactName: string;
  phone: string;
  addressLine1: string;
  addressLine2: string | null;
  city: string | null;
  zipCode: string | null;
  districtId: number | null;
  stateOrProvinceId: number | null;
  countryId: number;
};

/** "1 Main St, District 1, Ho Chi Minh, 700000, Viet Nam" — the parts that are set. */
export function formatAddress(address: Address): string {
  return [
    address.addressLine1,
    address.addressLine2,
    address.districtName,
    address.city,
    address.stateOrProvinceName,
    address.zipCode,
    address.countryName,
  ]
    .filter((part) => part)
    .join(', ');
}
