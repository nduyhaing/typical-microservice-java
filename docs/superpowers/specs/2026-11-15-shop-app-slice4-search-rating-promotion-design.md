# shop_app — Slice 4: Change data capture, search, rating, promotion — Design

Date: 2026-11-15 · Status: written for automatic implementation (P9) · YAS @ `179e8135`.

## Intent

Bring in the event-driven part of YAS and the two customer-facing features that sit on the catalog:
- **CDC + search**: product changes flow from Postgres through Debezium into Kafka; a `search` service indexes them
  in Elasticsearch; the storefront gets a search box with suggestions and a search results page with filters.
- **rating**: customers who bought a product leave a star rating and a comment; the product page shows the average
  and the reviews; admins see and delete reviews.
- **promotion**: admins create discount codes (percentage or fixed, for all products, brands, categories or
  products, with dates and usage limits); a customer enters a code at checkout, the server applies the discount and
  records the usage when the order is placed.

End state: an admin renames a product in the back office and within a few seconds the storefront search finds it
by the new name; a customer searches "galaxy", filters by brand and price, opens a product, sees its rating, buys
it, rates it; an admin creates code `SAVE10` (10 %, Samsung brand), the customer applies it at checkout, the order
total shows the discount, and the code's usage count goes up.

## Design (ports of YAS services — same paths and view models; shop_app conventions and invariants hold)

### 4a — CDC and search

- **compose**: add a Kafka Connect worker with the Debezium Postgres connector (`quay.io/debezium/connect`, a current
  3.x tag that supports Kafka 4.x; the builder pins the exact tag and records it), config/offset/status topics on
  the existing KRaft Kafka, healthcheck on the Connect REST API. A one-shot `connect-init` container (or a script
  run by the smoke test) registers the connector from `kafka/connects/debezium-product.json` idempotently
  (`PUT /connectors/{name}/config`). Postgres already runs with `wal_level=logical` (slice 1). Use a dedicated
  replication user with only the rights Debezium needs, not the superuser (YAS uses admin/admin — record the
  deviation); credentials from `.env`. Tables: `public.product` plus what search needs to denormalise brand and
  category names (YAS reads them back from the product service — keep that approach: the event carries the id,
  search fetches the product detail from product's API).
- **search service** (new module `search`, no Postgres DB): port YAS search — Spring Data Elasticsearch,
  `Product` document (id, name, slug, price, isPublished, isVisibleIndividually, isAllowedToOrder, isFeatured,
  thumbnailMediaId/URL as YAS, brand, categories, attributes, createdOn), Kafka consumer on the product CDC topic
  (create/update → fetch product from product service and index; delete or unpublished/hidden → remove from the
  index), retries with a dead-letter topic (YAS uses `@RetryableTopic`; keep it), idempotent upsert by id.
  Endpoints (public, context `/search`): `GET /storefront/catalog-search` (keyword, page, size, brand, category,
  attribute, minPrice, maxPrice, sortType — YAS params; page size bounded like 2a-10), `GET /storefront/search_suggest`.
  Only published, visible-individually products are returned (same rule as the storefront catalog).
  **Initial load**: the index starts empty — Debezium's initial snapshot (`snapshot.mode=initial`) fills it on first
  start; record it. Elasticsearch: single node, security on with a password from `.env` (YAS runs with security off
  — record the deviation if the builder can't make it work cleanly), heap 512 MB, volume `es-data`.
- **product**: nothing to change unless search needs an endpoint YAS has (`/storefront/products/{id}` detail for
  search, or YAS `ProductEsDetailVm` endpoint `GET /storefront/products-es/{id}` — port it).
- **storefront**: search box in the header with suggestions (debounced, through storefront-bff `/api/search/...`),
  `/search?keyword=` results page with brand/category facets, price range, sort, paging; empty and error states.
  Search down → the search page shows an error message, the rest of the storefront is unaffected.
- **nginx**: `/search/` on the api host. **Kafka UI** shows the topics (exists).

### 4b — rating

Port YAS rating (new module `rating`, DB `rating`): `Rating` (content, ratingStar 1..5, productId, productName,
lastName/firstName, createdBy/On), endpoints `GET /storefront/ratings/products/{productId}` (paged, public),
`GET /storefront/ratings/product/{productId}/average-star` (public), `POST /storefront/ratings` (CUSTOMER; **only
if the customer has a completed/placed order containing that product** — YAS checks order
`/storefront/orders/completed?productId=` via the order service; port that endpoint into order; one rating per
customer per product — YAS allows many; record our choice), admin `GET /backoffice/ratings` (filters),
`GET /backoffice/ratings/latest/{count}`, `DELETE /backoffice/ratings/{id}`. Customer name from the customer
service profile (YAS) or the token claims (simpler; builder's call, recorded). Content length bounded; star range
validated. Product's average rating shown on the product page and (optionally) cards.
- **storefront**: product page "Reviews" section — average stars, count, paged list, form for eligible logged-in
  customers (hidden/disabled with an explanation otherwise).
- **backoffice**: reviews page (list with filters, delete), latest ratings on the dashboard.

### 4c — promotion

Port YAS promotion (new module `promotion`, DB `promotion`): `Promotion` (name, slug, description, couponCode
unique, discountType PERCENTAGE|FIXED, discountPercentage / discountAmount (BigDecimal), usageLimit, usageCount,
minimumOrderPurchaseAmount, isActive, startDate, endDate, applyTo PRODUCT|CATEGORY|BRAND|ALL (YAS has no ALL —
if YAS models "all products" differently, follow YAS), PromotionApply rows), `PromotionUsage` (promotionId,
productId, userId, orderId). Endpoints as YAS: admin CRUD `/backoffice/promotions` (list with filters, get, create,
update, delete — ADMIN), `POST /storefront/promotions/verify` (CUSTOMER: couponCode, orderPrice, productIds →
`PromotionVerifyResultDto` with discount details, server-side; invalid/expired/used-up/min-amount-not-reached/
not-applicable → 400 with a clear code), `POST /storefront/promotions/updateUsage` (called by order).
Promotion needs product brand/category for applicability — ask the product service (YAS does).
- **order**: checkout/preview accept an optional `couponCode`; the server verifies it with promotion (customer's
  token), computes the discount (BigDecimal, applied to eligible lines only; percentage of eligible subtotal or the
  fixed amount capped at the eligible subtotal; tax computed on the discounted line amounts — record the rule;
  YAS stores `totalDiscountAmount`), stores the discount and coupon code on the order, and records usage after the
  order is saved (usage failure → compensate as for stock, or record usage inside the same flow before commit with
  a compensating call; usage limit must not be exceeded under concurrency — promotion increments atomically with
  `usage_count < usage_limit`). Promotion down with a code entered → 5xx/clear error, nothing saved; without a code
  → order unaffected.
- **storefront**: checkout "Coupon code" field with Apply/Remove; preview shows the discount line; errors shown.
- **backoffice**: promotions pages (list, create, edit with product/brand/category pickers, delete).

### Cross-cutting

- New services follow the newest module layout (`tax`, `location`, `inventory`): Liquibase, SecurityConfig with
  Keycloak role mapping, bounded paging, RestClients with short timeouts, ITs with Testcontainers and JDK
  `HttpServer` stubs, Dockerfile, compose healthcheck, nginx location, root pom module.
- Kafka/Elasticsearch ITs use Testcontainers (`kafka`, `elasticsearch` modules); a CDC end-to-end test runs in the
  smoke script (needs the real Debezium), not in `mvn verify`.
- Resource use: Elasticsearch + Connect add ~1.5 GB; document it in the README.

### Definition of done

1. `mvn verify` green incl. ITs: search (index from a Kafka message with stubbed product; unpublished removes;
   duplicate/out-of-order events idempotent; poison message goes to DLT; search filters/sort/paging; suggest);
   rating (eligibility via stubbed order, one per product, validation, admin delete, public reads); promotion
   (CRUD admin-only, verify rules: dates, active, usage limit, min amount, applicability by product/brand/category,
   atomic usage increment under concurrency); order (discount applied to eligible lines, tax after discount,
   promotion down, usage recorded/compensated).
2. Both apps' tests/lint/types/build green.
3. Compose healthy incl. connect, elasticsearch, search, rating, promotion; smoke: admin renames a product via the
   back-office API → within 30 s storefront search finds the new name; restore the name; customer with an order
   rates the product, average updates; admin creates a coupon, customer previews checkout with it → discount shown;
   places the order → usage count 1; an exhausted coupon → 400.
4. README + decisions 4-*.
