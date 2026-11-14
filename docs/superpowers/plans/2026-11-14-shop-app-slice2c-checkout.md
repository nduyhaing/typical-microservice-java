# shop_app Slice 2c (Checkout + my orders) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A logged-in customer checks out the cart, enters a shipping address, places an order (saved PENDING_PAYMENT with server-computed prices, cart emptied of those items) and sees it under "My orders".

**Architecture:** New `order` service (YAS port, Postgres `order`), product gains the YAS `GET /products` checkout lookup (orderable products only), storefront gains checkout and my-orders pages. Calls between services relay the customer's JWT.

**Tech Stack:** as slice 2b.

**Spec:** `docs/superpowers/specs/2026-11-14-shop-app-slice2c-checkout-design.md`

## Global Constraints

- Everything in the 2b plan's Global Constraints holds (Java 27, Boot 4.1.2, `com.shopapp`, Checkstyle, LF, Maven Central settings file, commit + push to `master` after each task, decisions in `decisions.md`, Java containers `eclipse-temurin:27-jre` with `bash -c` healthchecks, nginx upstream variables with `Forwarded`/`X-Forwarded-Prefix` blanked).
- Never trust client prices or totals (2c-1). Quantities 1..1000.

## Review Focus

1. **Tampered client data:** a place-order body with a fake total/prices, someone else's checkout id, or an already completed checkout → server prices / 403 / 400 — Task 3 tests `placeOrder_ignoresClientTotal`, `placeOrder_foreignCheckout_returns403`, `placeOrder_twice_returns400`.
2. **Not-orderable products** (parent with options, unpublished, not allowed to order) in a checkout → 404, nothing saved — Task 1 `checkoutList_excludesNotOrderable`, Task 3 `createCheckout_notOrderable_returns404`.
3. **Product or cart service down during checkout/order** → product down: 500, nothing saved; cart down after order: order still saved — Task 3 `createCheckout_productDown_returns5xx`, `placeOrder_cartDown_stillCreatesOrder`.
4. **Missing address fields / empty checkout** → 400 field errors; storefront keeps "Place order" disabled until required fields are filled — Task 3 `placeOrder_missingAddress_returns400`, Task 5 `CheckoutPage requires address fields`.
5. **Anonymous or expired session** on checkout/my-orders → 401/403 and the storefront shows the login prompt (2b-13) — Task 3 `anonymous_returns4xx`, Task 5 `MyOrdersPage shows login prompt`.

---

### Task 1: product `GET /products` checkout lookup
- [ ] Tests (`ProductCheckoutListIT`, authenticated via `jwt()`): `checkoutList_returnsOrderableProductsWithPrice` (variation + bare product), `checkoutList_excludesNotOrderable` (parent with options, unpublished, isAllowedToOrder=false), `checkoutList_productWithoutBrand_ok`, `checkoutList_anonymous_returns401`, `checkoutList_tooManyIds_returns400` (>100). RED → port `ProductCheckoutListVm`, `ProductGetCheckoutListVm`, service + controller (repository query: published, allowed to order, no options, id in list) → GREEN. Commit, push.

### Task 2: order module skeleton, schema, entities, repositories
- [ ] Module from the cart template (+ `<module>order</module>`), context `/order`, ddl 0001–0016 verbatim, entities/enums/repositories ported, `OrderSchemaIT` (tables `order`, `order_item`, `order_address`, `checkout`, `checkout_item` exist), `OrderRepositoryIT` (save order with items+address; find by createdBy/customer newest first). Commit, push.

### Task 3: checkout and order API
- [ ] `CheckoutOrderIT` with stub product (`/product/products`) and stub cart (`/cart/storefront/cart/items/remove`, records Authorization + body) via JDK `HttpServer`: `createCheckout_pricesFromProductService`, `createCheckout_notOrderable_returns404`, `createCheckout_productDown_returns5xx` (nothing saved), `createCheckout_quantityOutOfRange_returns400`, `getCheckout_foreign_returns403`, `placeOrder_createsPendingPaymentOrderFromCheckout`, `placeOrder_ignoresClientTotal`, `placeOrder_removesItemsFromCartWithCallersToken`, `placeOrder_cartDown_stillCreatesOrder`, `placeOrder_foreignCheckout_returns403`, `placeOrder_twice_returns400`, `placeOrder_missingAddress_returns400`, `myOrders_returnsOnlyOwnNewestFirst`, `orderByCheckout_returnsOwnOrder`, `anonymous_returns4xx`. RED → port/adapt services, view models, controllers, product/cart clients (JWT relay, timeouts) → GREEN. Commit, push.

### Task 4: order container, compose, nginx
- [ ] Dockerfile, compose `order` (DB `order`, product + cart URLs through the nginx alias, `KEYCLOAK_URL`), nginx `/order/`, nginx depends on order. Verify with a real token: create checkout for `galaxy-s24-violet` → place order → my-orders shows it PENDING_PAYMENT. Commit, push.

### Task 5: storefront checkout and my orders
- [ ] Tests: `CartPage Checkout button creates a checkout and navigates`, `Checkout button disabled with unavailable lines`, `CheckoutPage shows items and server total`, `CheckoutPage requires address fields`, `CheckoutPage places order and redirects to my orders`, `CheckoutPage shows login prompt on 401/403`, `MyOrdersPage lists orders`, `MyOrdersPage empty state`, `MyOrdersPage shows login prompt`, header "My orders" link. RED → `modules/order/{models,services}`, `pages/checkout/[id].tsx`, `pages/my-orders.tsx`, cart page button, header link → GREEN, lint, typecheck, build, Docker rebuild. Commit, push.

### Task 6: smoke, README, decisions, definition of done
- [ ] Smoke: logged-in add Violet → checkout → place order → my-orders contains PENDING_PAYMENT order with 819.00 total for qty 1 → cart no longer has Violet; anonymous checkout → 401/403. `mvn verify`, storefront tests/build, compose `--build --wait`, smoke `--restart` all PASS. README, decisions 2c-*. Commit, push.
