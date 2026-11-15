import { del, get, post, put } from '../../../common/services/ApiClientService';
import { Address, AddressPayload, Country, District, StateOrProvince } from '../models/Address';

const userAddress = '/customer/storefront/user-address';
const location = '/location/storefront';

/** The signed-in customer's saved addresses, default first. */
export const getUserAddresses = () => get<Address[]>(userAddress);

export const createUserAddress = (address: AddressPayload) => post<unknown>(userAddress, address);

/** Changes an address's fields; location only lets the owner do it (YAS: the storefront calls location directly). */
export const updateAddress = (id: number, address: AddressPayload) =>
  put<null>(`${location}/addresses/${id}`, address);

export const deleteUserAddress = (id: number) => del(`${userAddress}/${id}`);

export const chooseDefaultAddress = (id: number) => put<null>(`${userAddress}/${id}`, undefined);

export const getCountries = () => get<Country[]>(`${location}/countries`);

export const getStatesOrProvinces = (countryId: number) =>
  get<StateOrProvince[]>(`${location}/state-or-provinces/${countryId}`);

export const getDistricts = (stateOrProvinceId: number) => get<District[]>(`${location}/district/${stateOrProvinceId}`);
