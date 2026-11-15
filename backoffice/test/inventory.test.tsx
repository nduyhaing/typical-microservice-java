import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { ApiError } from '../common/services/ApiClientService';
import * as authService from '../modules/auth/services/AuthenticationService';
import * as stockService from '../modules/inventory/services/StockService';
import * as warehouseService from '../modules/inventory/services/WarehouseService';
import * as countryService from '../modules/location/services/CountryService';
import * as stateService from '../modules/location/services/StateOrProvinceService';
import WarehouseProductsPage from '../pages/inventory/warehouse-products/index';
import WarehouseStocksPage from '../pages/inventory/warehouse-stocks/index';
import WarehouseEditPage from '../pages/inventory/warehouses/[id]/edit';
import WarehouseCreatePage from '../pages/inventory/warehouses/create';
import WarehousesPage from '../pages/inventory/warehouses/index';

const push = vi.fn();
const router = { push, query: {} as Record<string, string>, isReady: true };
vi.mock('next/router', () => ({ useRouter: () => router }));
vi.mock('../modules/auth/services/AuthenticationService', () => ({ LOGOUT_URL: '/logout', getAuthentication: vi.fn() }));
vi.mock('../modules/inventory/services/StockService');
vi.mock('../modules/inventory/services/WarehouseService');
vi.mock('../modules/location/services/CountryService');
vi.mock('../modules/location/services/StateOrProvinceService');

const country = (id: number, name: string) => ({
  id,
  code2: name.slice(0, 2).toUpperCase(),
  name,
  code3: null,
  isBillingEnabled: true,
  isShippingEnabled: true,
  isCityEnabled: true,
  isZipCodeEnabled: true,
  isDistrictEnabled: true,
});
const badRequest = (title: string, detail: string) => new ApiError(400, { statusCode: '400 BAD_REQUEST', title, detail, fieldErrors: [] });

beforeEach(() => {
  router.query = {};
  vi.mocked(authService.getAuthentication).mockResolvedValue({ isAuthenticated: true, authenticatedUser: { username: 'admin' } });
  vi.mocked(countryService.getCountries).mockResolvedValue([country(1, 'Viet Nam'), country(2, 'France')]);
  vi.mocked(stateService.getStatesOrProvinces).mockImplementation(async (countryId) =>
    countryId === 1
      ? [
          { id: 10, name: 'Ha Noi', code: null, type: null, countryId: 1 },
          { id: 11, name: 'Da Nang', code: null, type: null, countryId: 1 },
        ]
      : []
  );
  vi.mocked(stateService.getDistricts).mockImplementation(async (stateId) =>
    stateId === 10 ? [{ id: 100, name: 'Ba Dinh' }] : [{ id: 110, name: 'Hai Chau' }]
  );
});
afterEach(() => {
  vi.resetAllMocks();
  vi.restoreAllMocks();
});

describe('Warehouses', () => {
  it('lists warehouses and shows the server error when one still has stock', async () => {
    vi.mocked(warehouseService.getWarehousePage).mockResolvedValue({
      warehouseContent: [{ id: 1, name: 'Main' }],
      pageNo: 0,
      pageSize: 10,
      totalElements: 1,
      totalPages: 1,
      isLast: true,
    });
    vi.mocked(warehouseService.deleteWarehouse).mockRejectedValue(badRequest('WAREHOUSE_HAS_STOCK', 'Warehouse 1 still has stock'));
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    render(<WarehousesPage />);
    const row = (await screen.findByText('Main')).closest('tr')!;
    expect(within(row).getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/inventory/warehouses/1/edit');
    expect(within(row).getByRole('link', { name: 'Stock' })).toHaveAttribute('href', '/inventory/warehouse-stocks?warehouseId=1');
    await userEvent.click(within(row).getByRole('button', { name: 'Delete' }));

    expect(window.confirm).toHaveBeenCalledWith('Delete warehouse "Main"?');
    expect(await screen.findByRole('alert')).toHaveTextContent('Warehouse 1 still has stock');
  });

  it('the address cascade resets state and district when the country changes, then submits the address', async () => {
    vi.mocked(warehouseService.createWarehouse).mockResolvedValue({ id: 5, name: 'North' });

    render(<WarehouseCreatePage />);
    await screen.findByRole('option', { name: 'Viet Nam' });
    await userEvent.selectOptions(screen.getByLabelText('Country'), 'Viet Nam');
    await userEvent.selectOptions(screen.getByLabelText('State or province'), await screen.findByRole('option', { name: 'Ha Noi' }));
    await userEvent.selectOptions(screen.getByLabelText('District'), await screen.findByRole('option', { name: 'Ba Dinh' }));
    expect(screen.getByLabelText('District')).toHaveValue('100');

    await userEvent.selectOptions(screen.getByLabelText('Country'), 'France');
    expect(screen.getByLabelText('State or province')).toHaveValue('');
    expect(screen.getByLabelText('District')).toHaveValue('');
    expect(screen.queryByRole('option', { name: 'Ba Dinh' })).not.toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('Country'), 'Viet Nam');
    await userEvent.selectOptions(screen.getByLabelText('State or province'), await screen.findByRole('option', { name: 'Da Nang' }));
    await userEvent.selectOptions(screen.getByLabelText('District'), await screen.findByRole('option', { name: 'Hai Chau' }));
    await userEvent.type(screen.getByLabelText('Name'), 'North');
    await userEvent.type(screen.getByLabelText('Contact name'), 'Ann');
    await userEvent.type(screen.getByLabelText('Phone'), '0123');
    await userEvent.type(screen.getByLabelText('Address line 1'), '1 Main Street');
    await userEvent.type(screen.getByLabelText('City'), 'Da Nang');
    await userEvent.type(screen.getByLabelText('Zip code'), '550000');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(warehouseService.createWarehouse).toHaveBeenCalledWith({
      name: 'North',
      contactName: 'Ann',
      phone: '0123',
      addressLine1: '1 Main Street',
      addressLine2: '',
      city: 'Da Nang',
      zipCode: '550000',
      districtId: 110,
      stateOrProvinceId: 11,
      countryId: 1,
    });
    expect(push).toHaveBeenCalledWith('/inventory/warehouses');
  });

  it('requires name, contact, phone, address line 1 and country', async () => {
    render(<WarehouseCreatePage />);
    await userEvent.type(screen.getByLabelText('Name'), 'North');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(screen.getByText('Name, contact name, phone, address line 1 and country are required.')).toBeInTheDocument();
    expect(warehouseService.createWarehouse).not.toHaveBeenCalled();
  });

  it('edits a warehouse with its address preselected; a state without districts sends null', async () => {
    router.query = { id: '4' };
    vi.mocked(warehouseService.getWarehouse).mockResolvedValue({
      id: 4,
      name: 'Main',
      contactName: 'Bob',
      phone: '0999',
      addressLine1: '2 Road',
      addressLine2: null,
      city: null,
      zipCode: null,
      districtId: 100,
      stateOrProvinceId: 10,
      countryId: 1,
    });
    vi.mocked(warehouseService.updateWarehouse).mockResolvedValue(null);

    render(<WarehouseEditPage />);
    await screen.findByRole('option', { name: 'Ba Dinh' });
    expect(screen.getByLabelText('Country')).toHaveValue('1');
    expect(screen.getByLabelText('State or province')).toHaveValue('10');
    expect(screen.getByLabelText('District')).toHaveValue('100');
    await userEvent.selectOptions(screen.getByLabelText('State or province'), 'Da Nang');
    expect(screen.getByLabelText('District')).toHaveValue('');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(warehouseService.updateWarehouse).toHaveBeenCalledWith(4, {
      name: 'Main',
      contactName: 'Bob',
      phone: '0999',
      addressLine1: '2 Road',
      addressLine2: '',
      city: '',
      zipCode: '',
      districtId: null,
      stateOrProvinceId: 11,
      countryId: 1,
    });
  });
});

describe('Warehouse stocks', () => {
  const stocks = [
    { id: 5, productId: 10, productName: 'Pixel 9', productSku: 'PX9', quantity: 3, reservedQuantity: 0, warehouseId: 1, productStockQuantity: 12 },
    { id: 6, productId: 11, productName: 'Pixel 8', productSku: 'PX8', quantity: 7, reservedQuantity: 0, warehouseId: 1, productStockQuantity: null },
  ];

  beforeEach(() => {
    vi.mocked(warehouseService.getWarehouses).mockResolvedValue([
      { id: 1, name: 'Main' },
      { id: 2, name: 'Spare' },
    ]);
    vi.mocked(stockService.getStocks).mockResolvedValue(stocks);
  });

  it('adjusts quantities with a note, shows a server error, then saves', async () => {
    vi.mocked(stockService.adjustStocks)
      .mockRejectedValueOnce(badRequest('INVALID_ADJUSTED_QUANTITY', 'Adjusted quantity -5 of stock 5 would make the quantity negative'))
      .mockResolvedValueOnce(null);

    render(<WarehouseStocksPage />);
    await userEvent.selectOptions(screen.getByLabelText('Warehouse'), await screen.findByRole('option', { name: 'Main' }));
    const row = (await screen.findByText('Pixel 9')).closest('tr')!;
    expect(stockService.getStocks).toHaveBeenCalledWith(1, '', '');
    expect(within(row).getByText('PX9')).toBeInTheDocument();
    await userEvent.type(within(row).getByLabelText('Adjustment for Pixel 9'), '-5');
    await userEvent.type(within(row).getByLabelText('Note for Pixel 9'), 'damaged');
    await userEvent.click(screen.getByRole('button', { name: 'Save adjustments' }));

    expect(stockService.adjustStocks).toHaveBeenCalledWith([{ stockId: 5, quantity: -5, note: 'damaged' }]);
    expect(await screen.findByRole('alert')).toHaveTextContent('would make the quantity negative');

    const adjustment = within(row).getByLabelText('Adjustment for Pixel 9');
    await userEvent.clear(adjustment);
    await userEvent.type(adjustment, '-2');
    await userEvent.click(screen.getByRole('button', { name: 'Save adjustments' }));

    expect(stockService.adjustStocks).toHaveBeenLastCalledWith([{ stockId: 5, quantity: -2, note: 'damaged' }]);
    expect(await screen.findByText('Stock quantities updated.')).toBeInTheDocument();
    expect(stockService.getStocks).toHaveBeenCalledTimes(2);
  });

  it("shows the product's sellable stock (all warehouses, after orders) next to the warehouse quantity", async () => {
    router.query = { warehouseId: '1' };

    render(<WarehouseStocksPage />);
    const row = (await screen.findByText('Pixel 9')).closest('tr')!;
    expect(screen.getByRole('columnheader', { name: 'In this warehouse' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Sellable (all warehouses)' })).toBeInTheDocument();
    const cells = within(row).getAllByRole('cell');
    expect(cells[2]).toHaveTextContent('3');
    expect(cells[3]).toHaveTextContent('12');
    const unknown = screen.getByText('Pixel 8').closest('tr')!;
    expect(within(unknown).getAllByRole('cell')[3]).toHaveTextContent('-');
  });

  it('refuses a non-integer adjustment without calling the server', async () => {
    router.query = { warehouseId: '1' };

    render(<WarehouseStocksPage />);
    const row = (await screen.findByText('Pixel 9')).closest('tr')!;
    await userEvent.type(within(row).getByLabelText('Adjustment for Pixel 9'), '1.5');
    await userEvent.click(screen.getByRole('button', { name: 'Save adjustments' }));

    expect(screen.getByText('Adjustments must be whole numbers.')).toBeInTheDocument();
    expect(stockService.adjustStocks).not.toHaveBeenCalled();
  });

  it('searches by product name and sku and shows the history of a product', async () => {
    router.query = { warehouseId: '1' };
    vi.mocked(stockService.getStockHistories).mockResolvedValue({
      data: [{ id: 1, productName: 'Pixel 9', adjustedQuantity: 5, createdBy: 'admin', createdOn: '2026-11-15T10:00:00Z', note: 'initial delivery' }],
    });

    render(<WarehouseStocksPage />);
    await screen.findByText('Pixel 9');
    await userEvent.type(screen.getByLabelText('Product name'), 'pixel');
    await userEvent.type(screen.getByLabelText('Product SKU'), 'PX');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    expect(stockService.getStocks).toHaveBeenLastCalledWith(1, 'pixel', 'PX');

    const row = screen.getByText('Pixel 9').closest('tr')!;
    await userEvent.click(within(row).getByRole('button', { name: 'History' }));
    const dialog = await screen.findByRole('dialog');
    expect(stockService.getStockHistories).toHaveBeenCalledWith(10, 1);
    expect(await within(dialog).findByText('initial delivery')).toBeInTheDocument();
    expect(within(dialog).getByText('+5')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Add products' })).toHaveAttribute('href', '/inventory/warehouse-products?warehouseId=1');
  });
});

describe('Warehouse products', () => {
  beforeEach(() => {
    router.query = { warehouseId: '1' };
    vi.mocked(warehouseService.getWarehouses).mockResolvedValue([{ id: 1, name: 'Main' }]);
  });

  it('adds the selected stock-tracked products to the warehouse', async () => {
    vi.mocked(warehouseService.getWarehouseProducts).mockResolvedValue([
      { id: 10, name: 'Pixel 9', sku: 'PX9', stockTrackingEnabled: true, existInWh: false },
      { id: 11, name: 'Pixel 8', sku: 'PX8', stockTrackingEnabled: true, existInWh: true },
      { id: 12, name: 'Gift card', sku: 'GC', stockTrackingEnabled: false, existInWh: false },
    ]);
    vi.mocked(stockService.addProductsToWarehouse).mockResolvedValue(null);

    render(<WarehouseProductsPage />);
    await screen.findByText('Pixel 9');
    expect(warehouseService.getWarehouseProducts).toHaveBeenCalledWith(1, '', '', 'NO');
    expect(screen.getByLabelText('Select Pixel 8')).toBeDisabled();
    expect(screen.getByLabelText('Select Gift card')).toBeDisabled();
    expect(screen.getByText('Stock tracking off')).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText('Select Pixel 9'));
    await userEvent.click(screen.getByRole('button', { name: 'Add selected products' }));

    expect(stockService.addProductsToWarehouse).toHaveBeenCalledWith(1, [10]);
    expect(await screen.findByText('1 product(s) added to the warehouse.')).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText('Exist in warehouse'), 'All');
    expect(warehouseService.getWarehouseProducts).toHaveBeenLastCalledWith(1, '', '', 'ALL');
  });

  it('shows the server error when a product cannot be added', async () => {
    vi.mocked(warehouseService.getWarehouseProducts).mockResolvedValue([
      { id: 10, name: 'Pixel 9', sku: 'PX9', stockTrackingEnabled: true, existInWh: false },
    ]);
    vi.mocked(stockService.addProductsToWarehouse).mockRejectedValue(
      badRequest('PRODUCT_STOCK_TRACKING_DISABLED', 'Product 10 does not have stock tracking enabled')
    );

    render(<WarehouseProductsPage />);
    await userEvent.click(await screen.findByLabelText('Select Pixel 9'));
    await userEvent.click(screen.getByRole('button', { name: 'Add selected products' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Product 10 does not have stock tracking enabled');
  });
});
