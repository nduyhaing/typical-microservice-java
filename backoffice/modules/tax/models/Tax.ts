import { PageInfo } from '../../../common/models/Page';

/** TaxClassVm. */
export type TaxClass = { id: number; name: string };

/** TaxClassPostVm. */
export type TaxClassPost = { name: string };

/** TaxClassListGetVm. */
export type TaxClassPage = PageInfo & { taxClassContent: TaxClass[] };

/** TaxRatePostVm: rate is a percentage 0..100 with at most 4 decimals (3c-10). */
export type TaxRatePost = {
  rate: number;
  zipCode: string;
  taxClassId: number;
  stateOrProvinceId: number | null;
  countryId: number;
};

/** TaxRateVm. */
export type TaxRate = {
  id: number;
  rate: number;
  zipCode: string | null;
  taxClassId: number;
  stateOrProvinceId: number | null;
  countryId: number;
};

/** TaxRateGetDetailVm: ids only for country and state (3c-15); the back office resolves the names. */
export type TaxRateDetail = {
  id: number;
  rate: number;
  zipCode: string | null;
  taxClassId: number;
  taxClassName: string;
  stateOrProvinceId: number | null;
  countryId: number;
};

/** TaxRateListGetVm. */
export type TaxRatePage = PageInfo & { taxRateGetDetailContent: TaxRateDetail[] };

/** A rate list row with names from location. */
export type TaxRateRow = TaxRateDetail & { countryName: string; stateOrProvinceName: string | null };
