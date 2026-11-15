# shop_app — Slice 3c: Location, tax, inventory — Design

Date: 2026-11-15 · Status: written for automatic implementation (P9) · YAS @ `179e8135`.

## Intent

Finish slice 3 (back office) with the three services YAS uses at checkout:
- **location**: countries, states/provinces, districts (admin), and customer **saved addresses**;
- **tax**: tax classes and tax rates by country/state/zip (admin); orders charge tax;
- **inventory**: warehouses and stock per product (admin, with stock history); the storefront shows availability;
  ordering a stock-tracked product reserves (subtracts) stock and refuses when there is not enough.

End state: an admin sets up countries/states, a tax class "Standard" with a rate (e.g. Vietnam 10 %), assigns it to
products, creates a warehouse and adds stock. A customer saves an address in an address book, picks it at
checkout, sees subtotal + tax + total, and places the order; stock goes down; an out-of-stock product cannot be
added to the cart or ordered.

## Design (ports of YAS services, same paths and view models; shop_app conventions and invariants hold)

### location (new module, DB `location`)
Port YAS location: Country, StateOrProvince, District, Address entities + ddl (and YAS seed data for countries if
present); admin CRUD `/backoffice/countries`, `/backoffice/state-or-provinces`, district lookups; storefront
`/storefront/countries`, `/storefront/state-or-provinces/{countryId}`, `/storefront/district/{id}`,
address endpoints `/storefront/addresses` (create/get/update/delete) used by the customer service.

### customer (extend)
Port YAS `UserAddress` + `/storefront/user-address` (list, default, create, update, delete) — the customer service
stores (userId, addressId, isActive) and calls location for the address data with the caller's token. Ownership:
a user only sees/changes their own addresses.

### tax (new module, DB `tax`)
Port YAS tax: TaxClass, TaxRate (taxClass, countryId, stateOrProvinceId, zipCode, rate) + ddl; admin CRUD
`/backoffice/tax-classes`, `/backoffice/tax-rates`; `GET /backoffice/tax-rates/tax-percent?taxClassId&countryId&stateOrProvinceId&zipCode`
and `location-based-batch` (YAS) used by order. Most specific rate wins (zip > state > country), 0 when none.

### inventory (new module, DB `inventory`)
Port YAS inventory: Warehouse (with an address in location), Stock (product × warehouse quantity, reserved),
StockHistory; admin `/backoffice/warehouses` CRUD, `/backoffice/stocks` add products to a warehouse and adjust
quantities with a note (history), `/backoffice/stocks/histories`. After a stock change, inventory updates the
product's `stockQuantity` (YAS product `PUT /backoffice/products/update-quantity`, port it).

### product (extend)
Port `PUT /backoffice/products/update-quantity` and `PUT /backoffice/products/subtract-quantity` (YAS; ADMIN or
service calls with the relayed token — subtract is called by order with the customer's token, so it needs its
own rule: allow any authenticated caller but only *decrease*, never below 0, atomic SQL `quantity = quantity - n
where quantity >= n`, 400 when insufficient). Storefront detail/thumbnail VMs expose `stockAvailable` (true when not
tracked or quantity > 0) and the tracked quantity cap for the cart. Tax class: product admin already stores
`taxClassId`; the back-office product form gets a tax class select (from tax).

### cart (extend)
Adding/updating a stock-tracked product beyond available stock → 400 (cart asks product via list-featured, which
returns stock info).

### order (extend)
- Migration: `order.total_tax`/`total_discount_amount` and checkout tax columns `float` → `numeric(19,2)`; entities
  BigDecimal (closes the 2c deferred item).
- Checkout keeps product `taxClassId` per item (from product `/products`).
- Place order with `shippingAddressId` (a saved address of the caller, fetched from customer/location) **or** a typed
  address with country/state ids (2c-3 typed address still allowed but country must be chosen from the list);
  tax per item = price × qty × rate(taxClass, country, state, zip) from tax (one batch call); order stores tax per
  item and total tax; total = subtotal + tax. Tax service down → 500, nothing saved (consistent with product down).
- Stock: before committing, subtract stock for tracked products via product subtract-quantity; insufficient →
  400 "not enough stock" and nothing saved; if the order transaction then fails, add the stock back
  (compensation) — record the approach (YAS does no compensation).

### storefront
Address book page (`/address`, YAS: list, create/edit with country → state → district selects, delete, default);
checkout: choose a saved address (default preselected) or enter a new one with the selects; summary shows subtotal,
tax, total from the server (`POST /storefront/orders/preview` or a checkout re-price endpoint with the chosen address —
builder's call, server computes, browser never sums tax); product page shows "In stock"/"Out of stock" and disables
add-to-cart when out of stock.

### backoffice
Pages: countries, states, (districts read-only), tax classes, tax rates, warehouses, stock (add product to warehouse,
adjust quantity with note, history list); product form: tax class select.

### Definition of done
1. `mvn verify` green incl. ITs per service (admin-only writes, ownership on addresses, tax rate resolution order,
   stock subtract atomic + insufficient, order tax and stock flows with stubs, BigDecimal migration).
2. Both apps' tests/lint/types/build green.
3. Compose healthy incl. location, tax, inventory; smoke: admin creates tax rate + stock via back office API; customer
   saves an address, checks out a tracked product with it, order total includes tax, stock decreased; ordering more
   than in stock → 400.
4. README + decisions 3c-*.
