# shop_app — Slice 5: Payment, delivery, webhooks, recommendation, sample data — Design

Date: 2026-11-15 · Status: written for automatic implementation (P9) · YAS @ `179e8135`.

## Intent

Close the order lifecycle and add YAS's remaining business services:
- **payment** (+ **payment-paypal**): a customer pays an order (cash on delivery or PayPal sandbox); the order moves
  from PENDING_PAYMENT to PAID; admins manage payment providers and see payments.
- **order lifecycle / delivery**: admins move orders through statuses (accepted → shipping → completed, or
  cancelled/refunded); cancelling gives stock and the coupon use back; ratings require a paid/completed order.
  YAS `delivery` is an empty stub — model delivery as order delivery status/method in order (record it) unless the
  builder finds real YAS behaviour to port.
- **webhook**: admins register webhook URLs for events (product updated, order created, order status updated); the
  webhook service consumes the CDC topics and POSTs signed JSON to those URLs with retries and a delivery log.
- **recommendation**: the product page shows "similar products" computed from product text with vector
  embeddings, fed by the product CDC topic.
- **sampledata**: an admin action (YAS: storefront button) that (re)inserts sample catalog + media data.

End state: a customer places an order, pays with COD (or PayPal sandbox when credentials are configured), the order
shows PAID; an admin ships and completes it; the customer can review it; a cancelled order returns its stock and
coupon; a webhook registered at a test receiver gets a signed `ON_ORDER_CREATED` delivery; the product page shows
similar products.

## Design (ports of YAS services — same paths and view models; shop_app conventions and invariants hold)

### payment (new module, DB `payment`)
Port YAS payment: `Payment` (orderId, checkoutId, amount BigDecimal, paymentFee, paymentMethod COD|BANKING|PAYPAL,
paymentStatus PENDING|COMPLETED|CANCELLED, gatewayTransactionId, failureMessage), `PaymentProvider` (id, name,
enabled, configureUrl, landingViewComponentName, additionalSettings, media). Endpoints (YAS): `POST /init`,
`POST /capture`, `GET /cancel`, `POST /backoffice/payment-providers`, `PUT /backoffice/payment-providers`,
`GET /storefront/payment-providers`; admin payments list (add if YAS lacks it — record). The **amount always comes
from the order service** (server-side), never from the browser. COD: init records a PENDING payment and the order
becomes "awaiting COD" (YAS status name) — confirmed by admin on delivery; BANKING if YAS has it as manual.
After capture, payment tells order (`PUT /storefront/orders/status` YAS path, or an internal endpoint blocked at the
edge like 3c's stock endpoints — record) → order PAID; idempotent by order/checkout id; a second capture for a paid
order → 400.

### payment-paypal (new module)
Port YAS payment-paypal (PayPal Checkout SDK / REST v2 orders: create → approve URL → capture). Sandbox client
id/secret from `.env` (empty by default → the PayPal provider is shown as disabled/unavailable; record). Return/cancel
URLs point at the storefront. Never trust the return URL query alone: capture server-side and verify the captured
amount and currency against the order before marking it paid. ITs stub PayPal with a JDK HttpServer.

### order (extend)
- Status machine (YAS `OrderStatus`): PENDING → PENDING_PAYMENT → PAID → SHIPPING → COMPLETED; CANCELLED / REFUND /
  REJECT; enforce allowed transitions server-side. Admin endpoints (YAS `/backoffice/orders`, status update,
  order detail, list with filters) and the back-office orders pages (list with filters, detail, status actions).
- Cancel (customer for own unpaid order, admin for any not-shipped order) → restore stock (product restore with the
  stored stock reference — the endpoint is in-network only) and release the coupon use (stored
  `promotion_usage_reference`); idempotent.
- Rating eligibility (`/storefront/orders/completed`) narrows to PAID/SHIPPING/COMPLETED (closes 4-30's interim rule).
- Order CDC topic for webhooks (Debezium connector for `order` DB: `order` table; `kafka/connects/debezium-order.json`,
  same pattern and dedicated user as 4-1..9).

### webhook (new module, DB `webhook`)
Port YAS webhook: `Webhook` (payloadUrl, contentType, secret, isActive, events), `Event`, `WebhookEvent`,
`WebhookEventNotification` (delivery log with status, response code, attempts). Admin CRUD
`/backoffice/webhooks` (+ `/paging`), events list. Consumers on the product and order CDC topics map changes to
`ON_PRODUCT_UPDATED`, `ON_ORDER_CREATED`, `ON_ORDER_STATUS_UPDATED` and POST JSON with an HMAC-SHA256 signature
header (`X-Hub-Signature-256` style, secret per webhook) with timeouts and bounded retries/backoff; failures logged,
never block the consumer. **SSRF guard**: payload URLs must be http(s), and private/loopback/link-local/metadata
addresses are refused at registration and at send time (allow-list override in config for local testing — the
smoke uses a receiver container on the compose network, so the allow-list names it). Secrets never returned in
full by the API.

### recommendation (new module, DB `recommendation` with pgvector)
Port YAS recommendation (Spring AI + pgvector, product CDC consumer → re-embed the product text → upsert; endpoint
`GET /embedding/product/{id}/similarity` → related products, published/visible only). YAS uses Azure OpenAI; shop_app
must run offline and keep data local: use a **local embedding model** (Spring AI Transformers/ONNX
`all-MiniLM-L6-v2`, 384 dims, model files cached in a volume) — record. If that is impractical within the memory
budget, fall back to Postgres full-text / `pg_trgm` similarity behind the same endpoint and record the deviation.
Postgres image must provide `vector` (switch to `pgvector/pgvector:pg16` — same major version, volume compatible;
verify) or keep stock postgres if falling back.
- storefront: "Similar products" strip on the product page (degrades to nothing when the service is down).

### sampledata (new module)
Port YAS sampledata: ADMIN-only `POST /backoffice/sampledata` (YAS: `/storefront/sampledata`, public — record the
deviation) that runs bundled SQL into product and media DBs (shop_app's own seed set: reuse the slice 2 seed data
and images; idempotent — upsert by slug/id, never duplicates); back-office "Load sample data" button with confirm.
Must not run on startup.

### Cross-cutting
- Same module layout and rules as before (Liquibase, SecurityConfig, bounded paging, short timeouts, ITs with
  Testcontainers + stubs, JVM cap + Hikari pool 5 in Dockerfiles, compose healthchecks, nginx locations, root pom).
- Memory: the Docker VM is constrained (see README "Resources"); new services must stay within the caps; the
  embedding model's memory is measured and recorded.
- Edge blocking: internal-only endpoints (payment→order status, order→product restore, order→promotion release)
  are denied at nginx and both BFFs, like 3c's stock endpoints.

### Definition of done
1. `mvn verify` green incl. ITs: payment (amount from order, COD flow, capture idempotent, provider admin-only),
   paypal (stubbed create/capture, amount mismatch → not paid), order (status machine, cancel restores stock and
   coupon once, rating eligibility narrowed, admin endpoints), webhook (signature, SSRF refusal, retries/log,
   CDC mapping), recommendation (sync from CDC, similarity excludes unpublished/self), sampledata (admin-only,
   idempotent).
2. Both apps' tests/lint/types/build green.
3. Compose healthy incl. payment, payment-paypal, webhook, recommendation, sampledata, order connector; smoke: customer
   orders and pays COD → admin marks paid/ships/completes → customer can review; customer cancels an unpaid order →
   stock and coupon back; webhook receiver container gets a correctly signed ON_ORDER_CREATED; product page shows
   similar products; sample data load is idempotent.
4. README + decisions 5-*.
