import { get, post, put } from '../../../common/services/ApiClientService';
import { Stock, StockAdjustment, StockHistoryList } from '../models/Inventory';

const base = '/inventory/backoffice/stocks';

/** Puts stock-tracked products into a warehouse with quantity 0 (204). */
export const addProductsToWarehouse = (warehouseId: number, productIds: number[]) =>
  post<null>(
    base,
    productIds.map((productId) => ({ productId, warehouseId }))
  );

export const getStocks = (warehouseId: number, productName: string, productSku: string) =>
  get<Stock[]>(
    `${base}?warehouseId=${warehouseId}&productName=${encodeURIComponent(productName)}&productSku=${encodeURIComponent(productSku)}`
  );

/** Adds each adjustment (+/-) to its stock with a note; a result below 0 is a 400 and nothing changes. */
export const adjustStocks = (adjustments: StockAdjustment[]) => put<null>(base, { stockQuantityList: adjustments });

export const getStockHistories = (productId: number, warehouseId: number) =>
  get<StockHistoryList>(`${base}/histories?productId=${productId}&warehouseId=${warehouseId}`);
