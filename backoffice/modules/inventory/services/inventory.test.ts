import { vi } from 'vitest';
import { del, get, post, put } from '../../../common/services/ApiClientService';
import { addProductsToWarehouse, adjustStocks, getStockHistories, getStocks } from './StockService';
import {
  createWarehouse,
  deleteWarehouse,
  getWarehouse,
  getWarehousePage,
  getWarehouseProducts,
  getWarehouses,
  updateWarehouse,
} from './WarehouseService';

vi.mock('../../../common/services/ApiClientService', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../../common/services/ApiClientService')>()),
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}));

afterEach(() => vi.resetAllMocks());

const warehouse = {
  name: 'Main',
  contactName: 'Ann',
  phone: '0123',
  addressLine1: '1 Street',
  addressLine2: '',
  city: 'Ha Noi',
  zipCode: '100000',
  districtId: 3,
  stateOrProvinceId: 2,
  countryId: 1,
};

describe('WarehouseService', () => {
  it('calls the warehouse endpoints', async () => {
    await getWarehouses();
    await getWarehousePage(3);
    await getWarehouse(4);
    await createWarehouse(warehouse);
    await updateWarehouse(4, warehouse);
    await deleteWarehouse(4);
    await getWarehouseProducts(4, 'pixel phone', 'PX&9', 'NO');

    expect(get).toHaveBeenNthCalledWith(1, '/inventory/backoffice/warehouses');
    expect(get).toHaveBeenNthCalledWith(2, '/inventory/backoffice/warehouses/paging?pageNo=3&pageSize=10');
    expect(get).toHaveBeenNthCalledWith(3, '/inventory/backoffice/warehouses/4');
    expect(post).toHaveBeenCalledWith('/inventory/backoffice/warehouses', warehouse);
    expect(put).toHaveBeenCalledWith('/inventory/backoffice/warehouses/4', warehouse);
    expect(del).toHaveBeenCalledWith('/inventory/backoffice/warehouses/4');
    expect(get).toHaveBeenNthCalledWith(
      4,
      '/inventory/backoffice/warehouses/4/products?productName=pixel%20phone&productSku=PX%269&existStatus=NO'
    );
  });
});

describe('StockService', () => {
  it('adds products to a warehouse as [{productId, warehouseId}]', async () => {
    await addProductsToWarehouse(4, [10, 11]);

    expect(post).toHaveBeenCalledWith('/inventory/backoffice/stocks', [
      { productId: 10, warehouseId: 4 },
      { productId: 11, warehouseId: 4 },
    ]);
  });

  it('lists stocks, adjusts quantities with notes and reads the history', async () => {
    await getStocks(4, 'pixel', '');
    await adjustStocks([{ stockId: 1, quantity: -2, note: 'damaged' }]);
    await getStockHistories(10, 4);

    expect(get).toHaveBeenNthCalledWith(1, '/inventory/backoffice/stocks?warehouseId=4&productName=pixel&productSku=');
    expect(put).toHaveBeenCalledWith('/inventory/backoffice/stocks', {
      stockQuantityList: [{ stockId: 1, quantity: -2, note: 'damaged' }],
    });
    expect(get).toHaveBeenNthCalledWith(2, '/inventory/backoffice/stocks/histories?productId=10&warehouseId=4');
  });
});
