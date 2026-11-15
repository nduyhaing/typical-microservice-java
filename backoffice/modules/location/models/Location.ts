import { PageInfo } from '../../../common/models/Page';

/** CountryPostVm (location 3c-1): create and update body; an update replaces every field. */
export type CountryPost = {
  code2: string;
  name: string;
  code3: string | null;
  isBillingEnabled: boolean;
  isShippingEnabled: boolean;
  isCityEnabled: boolean;
  isZipCodeEnabled: boolean;
  isDistrictEnabled: boolean;
};

/** CountryVm. */
export type Country = {
  id: number;
  code2: string;
  name: string;
  code3: string | null;
  isBillingEnabled: boolean | null;
  isShippingEnabled: boolean | null;
  isCityEnabled: boolean | null;
  isZipCodeEnabled: boolean | null;
  isDistrictEnabled: boolean | null;
};

/** CountryListGetVm. */
export type CountryPage = PageInfo & { countryContent: Country[] };

/** StateOrProvincePostVm: `countryId` is required on create and ignored on update (3c-7). */
export type StateOrProvincePost = {
  name: string;
  code: string | null;
  type: string | null;
  countryId: number;
};

/** StateOrProvinceVm. */
export type StateOrProvince = {
  id: number;
  name: string;
  code: string | null;
  type: string | null;
  countryId: number;
};

/** StateOrProvinceListGetVm. */
export type StateOrProvincePage = PageInfo & { stateOrProvinceContent: StateOrProvince[] };

/** StateOrProvinceAndCountryGetNameVm. */
export type StateCountryName = {
  stateOrProvinceId: number;
  stateOrProvinceName: string;
  countryName: string;
};

/** DistrictGetVm. */
export type District = { id: number; name: string };
