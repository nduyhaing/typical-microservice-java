# shop_app — Slice 2c: Checkout + my orders — Design

Date: 2026-11-14
Status: Written for automatic implementation (owner asked to implement automatically and record decisions in
`decisions.md`).
Builds on: slices 1, 2a, 2b. YAS reference @ `179e813568c345bad3fce985088b5535e57481aa`.

## Intent

Last step of slice 2 (agreed end state): a logged-in customer turns the cart into an order. The order is saved
**without tax, discount or payment**, with status **PENDING_PAYMENT** (YAS enum), and the ordered items leave the
cart. "My orders" lists the customer's orders.

Flow, as in YAS:
1. Cart page → **Checkout** creates a checkout (`POST /storefront/checkouts`) from the cart lines.
2. `/checkout/[id]` shows items and totals, asks for a shipping address (typed in — saved addresses need the
   location service, slice 3) and an optional note. Payment method: **cash on delivery** only (payments: slice 5).
3. **Place order** → `POST /storefront/orders` → order saved PENDING_PAYMENT, cart items removed → redirect to
   `/my-orders`.
4. `/my-orders` lists orders (date, status, items, total).

## Non-goals

- Payment providers and payment status changes (slice 5); delivery/shipping fees (delivery service, slice 5).
- Tax (slice 3) and promotions/coupons (slice 4): tax = 0, discount = 0, coupon ignored.
- Saved addresses / country-state lookups (slice 3), back-office order screens and CSV export (slice 3).
- Stock reservation / subtracting stock (inventory, slice 3).
- Order events to Kafka (slice 4).

## Anti-goals

- **Trusting client-side prices or totals.** YAS's `OrderPostVm` takes `totalPrice`, item prices, tax and discount
  from the browser. shop_app computes every price on the server from the product service at checkout time; the
  order is built from the stored checkout, never from client totals.
- Ordering something that cannot be bought: unpublished products, products with options (the parent — only a
  variation can be bought), products with `isAllowedToOrder=false` (closes a deferred 2b item).
- One customer seeing or ordering another customer's checkout or orders.

## Design

### product — checkout lookup

Port YAS `GET /products?ids=...&pageNo&pageSize` → `ProductGetCheckoutListVm(List<ProductCheckoutListVm>, paging)`
(authenticated; product's security already requires auth outside `/storefront/**`). Changes vs YAS:
- only **orderable** products: published, `isAllowedToOrder`, `hasOptions = false` (decision 2c-2);
- null-safe brand (YAS throws NPE for a product without brand, e.g. `usb-c-cable`);
- id list capped at 100, paging bounded like 2a-10.

### order service (new module `order`)

Port YAS `order` (Postgres DB `order` exists): Liquibase ddl (16 changesets verbatim), entities `Order`,
`OrderItem`, `OrderAddress`, `Checkout`, `CheckoutItem`, enums (`OrderStatus`, `CheckoutState`, `PaymentMethod`,
`PaymentStatus`, `DeliveryMethod`, `DeliveryStatus`), repositories.

Storefront endpoints (YAS paths), all authenticated (customer id = JWT `sub`):
- `POST /storefront/checkouts` (`CheckoutPostVm`: email?, note?, items `{productId, quantity}`) → `CheckoutVm`
  with items priced from product `GET /products` (relaying the caller's JWT, YAS); unknown/not orderable product →
  404; quantity 1..1000 (2b-11); total = Σ price × qty. State PENDING, owner = caller.
- `GET /storefront/checkouts/{id}` → own PENDING checkout with items (403 for another customer's, YAS).
- `POST /storefront/orders` (`OrderPostVm` reduced to what the server can't know: `checkoutId`, shipping address,
  optional billing address (defaults to shipping), `note`, `paymentMethod` (COD only in 2c)) → `OrderVm`.
  The order is built from the checkout's items and prices: status **PENDING_PAYMENT**, payment status PENDING,
  delivery method/status YAS defaults, tax 0, discount 0, delivery fee 0, email from the JWT `email` claim.
  Checkout state → COMPLETED. Then cart cleanup: `POST cart /storefront/cart/items/remove` with the ordered
  quantities, relaying the caller's JWT (YAS `CartService.deleteCartItems`); a cart failure is logged, not fatal
  (the order exists).
  Ordering an already completed or foreign checkout → 400 / 403.
- `GET /storefront/orders/my-orders` → caller's orders, newest first (YAS shape `OrderGetVm`, items included).
- `GET /storefront/orders/checkout/{id}` → caller's order for a checkout (YAS; used after placing).
- Address validation: contact name, phone, address line 1, city, country name required; ids (country/state/district)
  null until slice 3.

Calls to product/cart: RestClient with 2 s connect / 5 s read timeouts (2b-3); product down → 500 nothing saved.

### nginx / compose

`api.${DOMAIN}/order/` → order (context path `/order`); compose service `order` like cart (DB `order`).

### storefront

- Cart page: **Checkout** button (disabled when empty or with unavailable lines) → `POST /api/order/storefront/checkouts`
  → `/checkout/[id]`.
- `/checkout/[id]`: items, quantities, line totals, total (from the checkout, not recomputed in the browser),
  address form with required fields, note, "Cash on delivery" shown as the payment method, **Place order** →
  redirect `/my-orders?placed=<orderId>` with a confirmation banner. 401/403 → login prompt; 404 → not found.
- `/my-orders`: list (order id, date, status "Pending payment", items, total), empty state, login prompt.
- Header: "My orders" link when logged in.

### Definition of done

1. `mvn verify` green incl. order ITs: checkout pricing from product (stub), not-orderable → 404, foreign checkout
   → 403, place order → PENDING_PAYMENT with server prices (client total ignored), checkout completed, cart remove
   called with the caller's token, second place → 400, my-orders only own; product `/products` orderable filter.
2. Storefront tests (checkout button states, checkout page totals + required address fields + place order,
   my-orders list/empty/login prompt), lint, typecheck, build.
3. Compose healthy incl. order; smoke adds: logged-in add → checkout → place order → my-orders lists it with
   PENDING_PAYMENT and the cart no longer has the item; anonymous checkout → 401/403.
4. README + decisions 2c-*.

## Decisions (Claude; recorded as 2c-* in decisions.md)

- 2c-1 Server-side pricing; client totals never trusted (YAS deviation).
- 2c-2 Only orderable products can be checked out (published, allowed to order, no options).
- 2c-3 Address typed in on checkout (no saved addresses until slice 3); cash on delivery only until slice 5.
- 2c-4 Email from the JWT `email` claim (YAS calls the customer service; same value, one call less).
- 2c-5 Cart cleanup after ordering is best-effort (logged); the order is the source of truth.
