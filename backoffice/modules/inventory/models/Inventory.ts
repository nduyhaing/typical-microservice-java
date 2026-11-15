import { PageInfo } from '../../../common/models/Page';

/** WarehouseGetVm. */
export type Warehouse = { id: number; name: string };

/** WarehouseListGetVm. */
export type WarehousePage = PageInfo & { warehouseContent: Warehouse[] };

/** WarehousePostVm: the warehouse and its address (stored in location; state/district optional, 3c-20). */
export type WarehousePost = {
  name: string;
  contactName: string;
  phone: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  zipCode: string;
  districtId: number | null;
  stateOrProvinceId: number | null;
  countryId: number;
};

/** WarehouseDetailVm. */
export type WarehouseDetail = {
  id: number;
  name: string;
  contactName: string | null;
  phone: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  zipCode: string | null;
  districtId: number | null;
  stateOrProvinceId: number | null;
  countryId: number | null;
};

/** `existStatus` of the warehouse product lookup: any product / already in the warehouse / not yet in it. */
export type ExistStatus = 'ALL' | 'YES' | 'NO';

/** ProductInfoVm. */
export type WarehouseProduct = {
  id: number;
  name: string;
  sku: string | null;
  stockTrackingEnabled: boolean | null;
  existInWh: boolean | null;
};

/** StockVm. */
export type Stock = {
  id: number;
  productId: number;
  productName: string | null;
  productSku: string | null;
  quantity: number;
  reservedQuantity: number | null;
  warehouseId: number;
};

/** StockQuantityVm: `quantity` is the adjustment (+/-), not the new quantity. */
export type StockAdjustment = { stockId: number; quantity: number; note: string };

/** StockHistoryVm. */
export type StockHistory = {
  id: number;
  productName: string | null;
  adjustedQuantity: number;
  createdBy: string | null;
  createdOn: string | null;
  note: string | null;
};

/** StockHistoryListVm. */
export type StockHistoryList = { data: StockHistory[] };
