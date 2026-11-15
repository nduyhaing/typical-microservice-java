import { PAGE_SIZE } from '../../../common/models/Page';
import { del, get, post, put } from '../../../common/services/ApiClientService';
import { ExistStatus, Warehouse, WarehouseDetail, WarehousePage, WarehousePost, WarehouseProduct } from '../models/Inventory';

const base = '/inventory/backoffice/warehouses';

/** All warehouses (selects). */
export const getWarehouses = () => get<Warehouse[]>(base);

export const getWarehousePage = (pageNo: number) => get<WarehousePage>(`${base}/paging?pageNo=${pageNo}&pageSize=${PAGE_SIZE}`);

/** The warehouse with its address fields. */
export const getWarehouse = (id: number) => get<WarehouseDetail>(`${base}/${id}`);

export const createWarehouse = (warehouse: WarehousePost) => post<Warehouse>(base, warehouse);

export const updateWarehouse = (id: number, warehouse: WarehousePost) => put<null>(`${base}/${id}`, warehouse);

export const deleteWarehouse = (id: number) => del(`${base}/${id}`);

/** Products matching name and sku, flagged with whether the warehouse already stocks them. */
export const getWarehouseProducts = (warehouseId: number, productName: string, productSku: string, existStatus: ExistStatus) =>
  get<WarehouseProduct[]>(
    `${base}/${warehouseId}/products?productName=${encodeURIComponent(productName)}` +
      `&productSku=${encodeURIComponent(productSku)}&existStatus=${existStatus}`
  );
