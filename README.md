# shop_app

A microservices e-commerce system on Java 27 and Spring Boot 4. See `NOTICE` for third-party licenses.

[![Backend CI](https://github.com/nduyhaing/typical-microservice-java/actions/workflows/backend-ci.yml/badge.svg?branch=master)](https://github.com/nduyhaing/typical-microservice-java/actions/workflows/backend-ci.yml)
[![Storefront CI](https://github.com/nduyhaing/typical-microservice-java/actions/workflows/storefront-ci.yml/badge.svg?branch=master)](https://github.com/nduyhaing/typical-microservice-java/actions/workflows/storefront-ci.yml)
[![Backoffice CI](https://github.com/nduyhaing/typical-microservice-java/actions/workflows/backoffice-ci.yml/badge.svg?branch=master)](https://github.com/nduyhaing/typical-microservice-java/actions/workflows/backoffice-ci.yml)
[![Charts CI](https://github.com/nduyhaing/typical-microservice-java/actions/workflows/charts-ci.yml/badge.svg?branch=master)](https://github.com/nduyhaing/typical-microservice-java/actions/workflows/charts-ci.yml)
[![Secret scan](https://github.com/nduyhaing/typical-microservice-java/actions/workflows/security.yml/badge.svg?branch=master)](https://github.com/nduyhaing/typical-microservice-java/actions/workflows/security.yml)
[![CodeQL](https://github.com/nduyhaing/typical-microservice-java/actions/workflows/codeql.yml/badge.svg?branch=master)](https://github.com/nduyhaing/typical-microservice-java/actions/workflows/codeql.yml)

## Roadmap

The system is built in slices; each ends in something that runs end to end.

1. **Foundation** — parent POM, `common-library`, Postgres, Keycloak, Kafka, nginx *(done)*
2. Core shopping flow, in three steps:
   - **2a Browse** — product service, storefront-bff, storefront catalog pages *(done)*
   - **2b Log in + cart** — Keycloak login through the BFF (Redis sessions), customer and cart services, cart page *(done)*
   - **2c Checkout** — order service, checkout with a typed-in address, "My orders" *(done — slice 2 complete)*
3. Back office, in three steps:
   - **3a Back office + catalog admin** — backoffice-bff (ADMIN only), back office app, brands/categories/products admin *(done)*
   - **3b Media** — media service, image upload in the back office, product images on the storefront *(done)*
   - **3c Inventory, tax, location** — stock, tax rates, saved addresses at checkout *(done — slice 3 complete)*
4. **Events & search** — Debezium CDC → Kafka, search (Elasticsearch) with a storefront search page, reviews (rating),
   coupons (promotion) at checkout *(done — slice 4 complete)*
5. **Payments & the rest** — order lifecycle (admin statuses, delivery status, customer cancel with stock and
   coupon given back), payment (cash on delivery, PayPal sandbox), webhooks, recommendation (similar products),
   sample data *(done — slice 5 complete)*
6. **Operations** — observability (OpenTelemetry → Prometheus, Loki, Tempo, Grafana; compose add-on), Kubernetes
   (Helm charts, kind), CI (GitHub Actions, GHCR images, CodeQL, secret scan, Dependabot) *(done — slice 6 complete)*

Designs and plans live in `docs/superpowers/`.

### Project status

All six slices are done (`decisions.md` has every decision, deviation from YAS and what was left out). Known limits:

- **Local development only**: dev credentials in `.env` and the realm export, plain HTTP, one Postgres for all
  services, single-node Kafka/Elasticsearch/Redis, no backups.
- Observability is an opt-in compose add-on (~1.2 GB); the Helm charts do not deploy it. No JDBC spans, alerting,
  Kafka Connect/Postgres metrics; CDC events start new traces.
- Kubernetes: validated by Charts CI (lint, kubeconform, edge parity with compose nginx); the live kind run needs
  ~6 GiB (core) to ~12 GiB (full) free next to compose (`k8s/README.md`). ingress-nginx is retired upstream; Gateway
  API (NGINX Gateway Fabric) is the recommended move.
- PayPal runs against the sandbox only; webhooks deliver CDC events, not domain events.

## Prerequisites

- **JDK 27.** Maven must run on it: set `JAVA_HOME` to the JDK 27 folder
  (e.g. `~/.jdks/openjdk-27`) before running `mvn`.
- **Maven 3.9+**
- **Docker** with Compose v2 (Docker Desktop or Rancher Desktop)
- **Python 3** — only used by the smoke-test script
- **Hosts entries.** As Administrator, add this line to `C:\Windows\System32\drivers\etc\hosts`
  (or `/etc/hosts` on macOS/Linux):

  ```
  127.0.0.1 identity.shopapp.local api.shopapp.local pgadmin.shopapp.local kafka.shopapp.local storefront.shopapp.local backoffice.shopapp.local grafana.shopapp.local
  ```

## Run it

```bash
mvn verify                              # build + Checkstyle + tests (on JDK 27; needs Docker for Testcontainers)
mvn -DskipTests package                 # build the service jars the Docker images copy
docker compose up -d --build --wait     # start everything (images are rebuilt from the jars / storefront)
bash scripts/verify-foundation.sh       # smoke test (add --restart to also test a down/up cycle)
docker compose down                     # stop, keep data
docker compose down -v                  # stop and delete all data
```

Notes:

- The product service loads a **sample catalog** (about 20 products, e.g. `galaxy-s24`) when
  `PRODUCT_LIQUIBASE_CONTEXTS=sample-data` in `.env` (the default). Set it to `default` for an empty catalog.
- Storefront development with hot reload: `cd storefront && npm ci && npm run dev`, then open
  http://localhost:3000. Server-side rendering calls the BFF at `API_BASE_PATH_SERVER`
  (default `http://localhost:8087/api`); browser calls go to `/api` on the page's own host, so use the
  storefront through `http://storefront.shopapp.local` for the full flow.
- **Login**: the storefront's "Log in" goes through storefront-bff to Keycloak. The BFF keeps the tokens in its
  Redis-backed session and relays the access token to the APIs; the browser only has a session cookie.
  Logging out also ends the Keycloak session.
- **Checkout**: cart → Checkout → shipping address (saved or typed) → Place order. Orders are saved as "Pending
  payment" with server-computed prices and tax; coupons are applied at checkout (see [Coupons](#coupons)); shipping
  fees and real payments come in later slices (cash on delivery only for now).
- **Tax, stock and addresses** (slice 3c, `decisions.md` 3c-*): see [Tax, stock and addresses](#tax-stock-and-addresses).
- **Images**: in the back office, the product form's thumbnail and gallery and the category form's image upload a
  file as soon as it is chosen (PNG, JPEG or GIF, up to **10 MB**; the type is checked by decoding the image,
  not by its name). Images are served by the media service at
  `http://api.shopapp.local:80/media/medias/{id}/file/{fileName}` (public, cached for a day). Files live on the
  Docker volume `media-data` (`docker compose down -v` deletes them). If the media service is down, product
  pages still load and show placeholder images.
- **Keycloak volume created before slice 2b?** Run `bash identity/scripts/apply-kc-admin-api-scope.sh` once
  (adds the admin-API audience scope the customer service needs; fresh volumes get it from the realm file).
- **CDC (change data capture)**: see [CDC](#cdc-change-data-capture) below. Search, reviews and coupons:
  [Search](#search), [Reviews](#reviews), [Coupons](#coupons).
- **Memory**: the stack needs about 7.5 GB of RAM; see [Resources](#resources).
- `postgres_init.sql` creates the per-service databases **only when the Postgres volume is empty**.
  After changing it, run `docker compose down -v` to start fresh.
- **Port 80 already in use?** In `.env` set `NGINX_HTTP_PORT=8000` **and**
  `KEYCLOAK_URL=http://identity.${DOMAIN}:8000`, then `docker compose up -d`. Use
  `http://<host>.shopapp.local:8000`. The smoke script checks that both settings agree.
- All published ports (nginx, Postgres, Kafka) listen on `127.0.0.1` only, so the dev credentials
  below are not reachable from your network.
- The realm import (`identity/realm-export.json`) and pgAdmin's `docker/pgadmin/servers.json` are also
  only read on first start; after editing them, run `docker compose down -v`.
- To change the domain, edit `DOMAIN` in `.env` (and your hosts file) — nginx and the Keycloak realm
  read it from there.

## Tax, stock and addresses

- **Locations** (location service): countries, states/provinces and districts, edited in the back office under
  *Location* (districts are read-only). The storefront reads them anonymously
  (`GET /location/storefront/countries`, `/state-or-provinces/{countryId}`, `/district/{stateId}`).
- **Tax** (tax service): an admin creates **tax classes** and **rates** (percent) for a class at a country, optionally
  narrowed to a state/province and a zip code, and gives a product a class on the product form ("None" = untaxed).
  At checkout the **most specific rate wins**: country + state + zip, then country + zip, then country + state, then
  country only; no matching rate = 0 % (`decisions.md` 3c-11). Prices are always before tax; the tax is computed
  per line on the server (`BigDecimal`, rounded half-up to cents) and shown in the checkout summary and on the order.
- **Stock** — two numbers, on purpose:
  - the product's **sellable stock** (`stockQuantity` on the product, used only when *stock tracking* is on): what
    can still be ordered. Placing an order takes from it atomically (never oversold; a failed order gives it back),
    and the cart refuses more than is in stock;
  - the **physical stock** per warehouse (inventory service, back office *Inventory → Warehouses / Stock*).
    Adjustments are **deltas** (+3 received, −2 broken, each with a note and kept in the history) and add the same
    delta to the sellable stock.
  - Orders do not touch warehouse rows, so the two drift apart by the units sold but not shipped yet. **Do not book
    shipped orders out of a warehouse with a normal adjustment**: that removes them from the sellable stock a second
    time (`decisions.md` 3c-60). Untracked products are always orderable.
- **Saved addresses**: customers keep an address book (storefront `/address`; customer stores the link,
  location the address). At checkout they pick a saved address or type a new one (country/state/district from
  location). Only the caller's own addresses can be used, also for an admin.
- **Checkout preview**: the checkout page asks order for a preview (`POST /order/storefront/orders/preview`) whenever
  the address changes, and shows subtotal, tax and total computed by the server; placing the order prices it again
  and ignores any total from the browser.

## CDC (change data capture)

Product changes flow from Postgres to Kafka without any service code (slice 4, `decisions.md` 4-1..4-9):

```
product service --writes--> Postgres product.public.product --WAL (pgoutput, slot product_slot,
publication dbz_product)--> kafka-connect (Debezium) --> Kafka topic dbproduct.public.product --> search (slice 4)
```

- **Services**: `kafka-connect` (Debezium Connect `quay.io/debezium/connect:3.7.1.Final`, stores its config,
  offsets and status in the Kafka topics `connect-configs`, `connect-offsets`, `connect-statuses`);
  `debezium-db-init` (one-shot: replication user, grants and publication, after product's tables exist);
  `kafka-connect-init` (registers `kafka/connects/debezium-product.json` as `product-connector` with
  `PUT /connectors/product-connector/config` on every `up`, then restarts a FAILED connector every 30 s and
  registers a deleted one again — to delete or stop a connector on purpose, `docker compose stop kafka-connect-init`
  first).
- **Messages**: key `{"id":<product id>}`, value the Debezium envelope without schema:
  `{"before":…,"after":{…product columns…},"op":"c|u|d|r","source":…}`. `r` = initial snapshot (all products, once,
  when the connector first starts), a delete is followed by a tombstone (null value). Every product row update
  sends a message, including stock changes (`stock_quantity` is a product column).
- **Check it**: Kafka UI (http://kafka.shopapp.local) → *Kafka Connect* → `product-connector` (state, tasks,
  restart) and *Topics* → `dbproduct.public.product` → *Messages*. From the shell:
  `docker compose exec kafka-connect curl -s localhost:8083/connectors?expand=status`. The smoke script renames a
  product and waits for its message on the topic.
- **Postgres user**: Debezium logs in as `DEBEZIUM_DB_USER` (`.env`, default `debezium`) with `REPLICATION`,
  `CONNECT` on the product database and `SELECT` on `public.product` only, not the superuser (no login may connect
  to a database it was not granted). After changing `DEBEZIUM_DB_PASSWORD`, run
  `bash scripts/apply-debezium-user.sh` and `docker compose restart kafka-connect`.
- **Disk safety**: a replication slot keeps WAL until Debezium confirms it. If `kafka-connect` is stopped for a long
  time while the other databases keep writing, WAL piles up; `max_slot_wal_keep_size = 1GB`
  (`docker/postgres/postgresql.conf`) caps it — past that Postgres invalidates `product_slot` and the connector
  fails (a FAILED task that `kafka-connect-init` cannot fix by restarting). To recover with a fresh snapshot, inside
  `kafka-connect` (`docker compose exec kafka-connect bash`):
  `curl -X PUT localhost:8083/connectors/product-connector/stop`, then
  `docker compose exec postgres psql -U admin -c "SELECT pg_drop_replication_slot('product_slot')"` (from the host),
  then `curl -X DELETE localhost:8083/connectors/product-connector/offsets` and
  `curl -X PUT localhost:8083/connectors/product-connector/resume`. Debezium creates the slot again and snapshots
  all products (consumers must accept duplicates, which search does by upserting by id). Check WAL held by the
  slot with
  `SELECT slot_name, wal_status, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) FROM pg_replication_slots`.
- **Resources**: `kafka-connect` uses about 0.7–1 GB RAM (heap capped at 512 MB) and some CPU at start-up (it
  takes 30–90 s to become healthy). If it is down, the shop keeps working; only the CDC topic (and search
  updates) stops, and it catches up from its saved position when it comes back.
- **Volume created before slice 4?** Nothing to do: `debezium-db-init` creates the user on the next `up`
  (`postgres_init.sql` only runs on an empty volume).

## Search

Product search runs on Elasticsearch, fed by the CDC topic (slice 4, `decisions.md` 4-10..4-19):

```
dbproduct.public.product --consumer group "search"--> search --GET /storefront/products-es/{id}--> product
                                                         \--index / delete--> elasticsearch (index "product")
```

- **Endpoints** (public, through nginx `http://api.shopapp.local/search/...`):
  `GET /storefront/catalog-search?keyword=&page=0&size=12&brand=&category=&attribute=&minPrice=&maxPrice=&sortType=DEFAULT|PRICE_ASC|PRICE_DESC`
  (brand/category/attribute take comma-separated exact values; size 1..100; the answer has `products`, paging and
  `aggregations.brands|categories|attributes` facet counts) and `GET /storefront/search_suggest?keyword=` (up to 10
  names). Only published, individually visible products are ever returned. Back office:
  `POST /backoffice/reindex` (ADMIN) re-reads every product.
- **How the index is filled**: an event whose row is unpublished or not visible individually removes the document
  at once, without asking product (so an unpublish works while product is down); on every other product event
  (`c`, `r`, `u`) search reads the product again from product and indexes it (a 404 removes it); a delete (`d` or
  the tombstone) removes the document. The consumer group starts from the earliest offset, so a new, empty index is
  filled from Debezium's initial snapshot (`op=r` for every product) already on the topic. Updates that only change
  the stock are not re-read when Debezium sends both row images (by default it does not, so they are re-read —
  harmless). Duplicates and out-of-order events are safe: each event re-reads the current product, and the document
  version is product's last-modified time, so an older read never overwrites a newer one.
- **Failures**: product or Elasticsearch unavailable → the event is retried in place (1 s doubling to 2 min) until
  they answer, however long the outage: search falls behind but loses nothing. A malformed message goes to
  `dbproduct.public.product-dlt` (Kafka UI → Topics) at once; any other error after 2 retries. After an outage, or after renaming a **brand or category** (those changes do not
  produce product events, so search keeps the old name until each product changes), rebuild with
  `curl -X POST -H "Authorization: Bearer $TOKEN" http://api.shopapp.local/search/backoffice/reindex`
  (admin token, see "URLs and credentials").
- **Elasticsearch**: `elasticsearch` service (`docker.elastic.co/elasticsearch/elasticsearch:9.4.8`, single node,
  heap 512 MB, container limit 1 GB, volume `es-data`), security on, plain HTTP inside the compose network, **no
  host port**. Users: `elastic` / `ELASTIC_PASSWORD` (superuser, for manual checks) and `SEARCH_ES_USERNAME` /
  `SEARCH_ES_PASSWORD` (the service; role limited to the `product` index), both from `.env`. Query it with
  `docker compose exec elasticsearch curl -s -u "elastic:$ELASTIC_PASSWORD" localhost:9200/product/_count`.
- **Resources**: Elasticsearch about 0.8–1 GB RAM, 30–60 s to become healthy; `search` ~300–400 MB. If either is
  down, the rest of the shop keeps working (nginx does not wait for search; the storefront search page shows an
  error) and the smoke script reports it; on restart the consumer continues from its committed offset.
- **Index empty although products exist?** The CDC topic is compacted (it keeps the latest message of every
  product), and search re-reads it from the start when it starts with an empty index — `docker compose restart
  search` refills it. Otherwise `POST /backoffice/reindex`, or replay the topic by hand:
  `docker compose stop search`, then
  `MSYS_NO_PATHCONV=1 docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group search --topic dbproduct.public.product --reset-offsets --to-earliest --execute`,
  then `docker compose start search`.
- **Start from scratch**: `docker compose stop search`, delete the index
  (`docker compose exec elasticsearch curl -s -X DELETE -u "elastic:$ELASTIC_PASSWORD" localhost:9200/product`),
  `docker compose start search`, then `POST /backoffice/reindex` (the topic may no longer hold every product).

## Recommendation

The product page shows **Similar products**, computed from product text with a local embedding model (slice 5,
`decisions.md` 5-40..5-49):

```
dbproduct.public.product --consumer group "recommendation"--> recommendation --products-es + product/{slug}--> product
                                                                  \--embed (ONNX, in-process)--> postgres "recommendation"
                                                                      (pgvector table product_embedding, HNSW cosine)
```

- **Model**: `sentence-transformers/all-MiniLM-L6-v2` (ONNX export, 384 dimensions) run by Spring AI's
  `TransformersEmbeddingModel` on ONNX Runtime inside the service — no external AI service, nothing leaves the
  machine. Downloaded once from Hugging Face on the first start (`model.onnx` 90 MB + `tokenizer.json` 0.5 MB,
  pinned to a commit) into the `recommendation-model` volume; afterwards it starts offline.
- **Text that is embedded**: `<categories>. <name>. Brand: <brand>. Categories: <categories>. <short description>.
  <categories>` (HTML removed). Attribute values and the specification are left out: with them a laptop came second
  for a phone ("Screen size: ..." matched). Only published, individually visible products are stored as
  recommendable (variations never are).
- **Resources**: compose caps the container at `mem_limit: 900m` and ONNX Runtime at 2 intra-op threads
  (`RECOMMENDATION_ONNX_THREADS`; its default is one per host core). JVM heap 320 MB, ONNX Runtime native memory on
  top (measured in `decisions.md` 5-71), image 944 MB, model volume 87 MB. Stop it if memory is tight: the product
  page simply has no similar products.
- **Endpoint** (public): `GET http://api.shopapp.local/recommendation/embedding/product/{id}/similarity` → up to 8
  `{id, name, slug, price, brandName, thumbnailUrl}`, nearest first, without the product itself; `[]` for an
  unknown or unpublished product. `POST /recommendation/backoffice/reindex` (ADMIN) re-embeds every product.
- **Sync and failures**: same rules as search — unpublish/hide removes at once without asking product, every other
  event re-reads product, versions stop older reads from winning, product/Postgres down → retried in place until
  they answer, malformed messages → `dbproduct.public.product-recommendation-dlt`. An empty table re-reads the
  topic from the start (`docker compose restart recommendation`).
- **Down?** The storefront waits at most 1.5 s and hides the section; nginx does not wait for the service.
- **Postgres image**: `pgvector/pgvector:0.8.7-pg16-trixie` (PostgreSQL 16.16 on the same Debian as
  `postgres:16.16`, plus the `vector` extension); the existing `postgres-data` volume works unchanged.

## Order lifecycle

Orders move through one status machine (order service, `decisions.md` 5-1..5-9): PENDING → PENDING_PAYMENT →
ACCEPTED (cash on delivery accepted) / PAID → SHIPPING → COMPLETED, or CANCELLED / REJECT / REFUND. Anything else is
a 400.

- **Back office** *Sales → Orders*: filters, detail, status change (with delivery status and method).
- **Customer cancel**: "Cancel order" on My orders while the order is PENDING or PENDING_PAYMENT and no PayPal capture
  is running (5-62).
- **Stock and coupon come back** when an unshipped order is cancelled, rejected or refunded: product restores the
  units and promotion releases the coupon use, each exactly once, called with the order service's own token. If
  product or promotion is down, a sweep retries every 5 minutes (the least recently tried first, with a back-off per
  order, 5-65).

## Sample data

Back office dashboard card **Sample data** (`POST /sampledata/backoffice/sampledata`, ADMIN) restores the sample
catalog (brands, categories, products, options, attributes; `decisions.md` 5-50..5-54): rows that are missing are inserted again and
deleted (unpublished) sample products are published again (as a new version, so search and recommendation pick them
up, 5-70). Loading twice inserts nothing the second time. It never touches orders, customers or carts and runs with
its own Postgres login that can only read and insert catalog rows.

## Webhooks

Admins register URLs that get signed JSON for `ON_PRODUCT_UPDATED`, `ON_ORDER_CREATED` and `ON_ORDER_STATUS_UPDATED`
(webhook service, `decisions.md` 5-30..5-39). Back office: **System → Webhooks** (create/edit, events, delivery log,
"Send test event").

```
dbproduct.public.product / dborder.public.order --group "webhook"--> webhook --log row--> delivery worker --POST--> receiver
```

- **Delivery**: `POST` with `Content-Type: application/json`, body `{"deliveryId","event","createdAt","data"}` and
  headers `X-Shopapp-Delivery`, `X-Shopapp-Event`, `X-Shopapp-Timestamp` (Unix seconds) and
  `X-Hub-Signature-256: sha256=<hex HMAC-SHA256 of timestamp + "." + raw body, keyed with the webhook's secret>`
  (`decisions.md` 5-69). Verify on the receiver — the signature, then that the timestamp is recent (e.g. 5 minutes),
  so a captured delivery cannot be replayed later — e.g. in Python:
  `hmac.compare_digest(sig, "sha256=" + hmac.new(secret, ts.encode() + b"." + body, hashlib.sha256).hexdigest())
  and abs(time.time() - int(ts)) < 300`.
- **Retries**: 3 attempts (after 10 s, then 60 s) for 5xx, 408, 429, timeouts and connection errors; other answers
  and redirects are not retried. A CDC event is queued once per database change (Debezium LSN + transaction +
  table + row, 5-64): re-read events are not sent twice, and events after a topic reset are not mistaken for old
  ones. Every attempt is in the delivery log (`GET /webhook/backoffice/webhooks/{id}/notifications`).
- **SSRF guard**: only http(s) URLs whose host resolves to public addresses are accepted, at registration and again
  at send time (the checked address is the one connected to); IPv6 forms that embed an IPv4 address (mapped, SIIT,
  NAT64, 6to4) are judged by that IPv4 address. Hosts in `WEBHOOK_ALLOWED_HOSTS` (compose default:
  `webhook-receiver`) are exempt — local testing only.
- **Test receiver**: `webhook-receiver` logs every request (headers and body) as one JSON line:
  register `http://webhook-receiver:8080/anything`, then `docker compose logs -f webhook-receiver`.
- The secret is shown once (when you create or regenerate it); the API only returns its last four characters.

## Payments

Customers pay an order cash on delivery or with PayPal (payment + payment-paypal services, `decisions.md`
5-10..5-26). After **Place order** the storefront opens the pay step `/checkout/pay/{orderId}` (also "Pay now" on My
orders).

```
storefront --POST /payment/init {orderId, method}--> payment --GET /internal/orders/{id} (SERVICE token)--> order
                                                       \--PAYPAL--> payment-paypal --Orders v2--> PayPal (approval URL)
PayPal --redirect ?token=--> /checkout/payment-return --POST /payment/capture--> payment --capture + verify--> PUT order /internal/orders/{id}/payment
```

- **The amount always comes from the order service**, never from the browser; a customer can only pay their own
  order (someone else's is 404); paid, cancelled, rejected or refunded orders are refused.
- **Cash on delivery**: the pay step records a pending payment ("Order placed — pay on delivery"). When the cash is
  collected, an admin clicks **Confirm cash received** in the back office (**Sales → Payments**); the order becomes
  PAID and the customer can review its products.
- **PayPal (sandbox)**: off by default — with empty `PAYPAL_CLIENT_ID` / `PAYPAL_CLIENT_SECRET` in `.env` the PayPal
  provider is hidden and its endpoints answer 503. To try it, create a sandbox REST app at developer.paypal.com, put
  its client id and secret in `.env` and `docker compose up -d payment-paypal`. The capture is verified server-side
  (USD, the exact order total, our reference) before the order is marked paid; a replayed return page never pays twice.
  Each payment sends its own random `PayPal-Request-Id` when creating its PayPal order; a capture PayPal reports as
  already captured is read back from PayPal and verified like any other. Cancelling at PayPal is a `POST
  /payment/cancel {token}`.
- **One payment step at a time per order** (5-60): starting a payment, capturing and confirming cash are serialised
  per order, so a capture and a new "pay on delivery" (or two captures, or two starts) can never overwrite each other;
  an order with a completed payment takes no other.
- **Capture in progress** (5-62): just before capturing at PayPal the payment service marks the order "payment in
  progress" for 5 minutes; meanwhile neither the customer nor an admin can cancel or reject it.
- **Order down after the capture** (5-61): the money is kept as a completed payment and the payment service retries
  telling the order every minute (30 s back-off, doubling to an hour, 20 payments per run). If the order refuses it
  (e.g. cancelled after the marker expired), the retries stop. **Sales → Payments** highlights these payments
  ("Needs attention", filter "Needs attention only") together with captures that failed verification: refund them in
  the PayPal dashboard (no refunds through the API yet).
- payment-paypal is internal only (nginx and the BFFs refuse `/payment-paypal/`). Services call each other's
  `/internal/` endpoints with their own Keycloak client and **per-capability realm roles** (5-66): `payment-service`
  has `SERVICE_ORDER_READ`, `SERVICE_ORDER_PAYMENT`, `SERVICE_PAYPAL`; `order-service` has `SERVICE_GOODS_BACK`
  (give stock and coupon use back); neither has the default customer roles. On a Keycloak volume created before this
  change run `bash identity/scripts/apply-service-client.sh` and
  `bash identity/scripts/apply-service-client.sh payment-service PAYMENT_SERVICE_CLIENT_SECRET` once.
- Back office: **Sales → Payment providers** (enable, disable, rename; "Not configured" for PayPal without credentials).

## Reviews

Customers review products on the storefront product page (rating service, slice 4, `decisions.md` 4-30..4-39).

- **Who can review**: a logged-in customer who has **bought** the product (or one of its variations): an order that
  is PAID, SHIPPING or COMPLETED (`decisions.md` 5-3). Others get "Only customers who bought this product can review
  it" (400). Admin accounts cannot post reviews.
- **One review per customer and product** (also enforced by the database; a second one is a 409 "You have already
  reviewed this product"). There is no edit: delete your own review (`DELETE /rating/storefront/ratings/{id}`) and
  write a new one. Product and customer names are copied when the review is written.
- **Product page**: "Reviews" section with the average ("4.0 out of 5 (3 reviews)"), the newest reviews (5 per
  page) and the write-a-review form (1–5 stars, up to 1000 characters). If rating is down the page still loads
  and says "Reviews are not available right now."
- **Back office**: *Catalog → Reviews* (`/reviews`) lists all reviews with filters (product, customer, message,
  date range) and lets an admin delete any review; the dashboard shows the latest ratings.

## Coupons

Coupon codes at checkout (promotion service, slice 4, `decisions.md` 4-40..4-59).

- **Back office** *Promotion → Promotions* (`/promotion/manager-promotion`): name, coupon code (unique, letters,
  digits, `-`, `_`; customers may type it in any case), **percentage** (0–100 %) or **fixed amount** discount, applies
  to chosen **products, brands or categories**, start/end time, optional minimum order amount, **usage limit**
  (unlimited or N uses in total) and an active flag.
- **Discount rule**: only the order lines the coupon applies to are discounted. Percentage: each eligible line
  gets `line subtotal × p %` (rounded half-up to cents). Fixed: the amount (at most the eligible subtotal) is shared
  across the eligible lines in proportion to their subtotals.
- **Tax after discount**: each line is taxed on its discounted amount;
  `total = subtotal − discount + tax`. The checkout's "Coupon code" field asks the server for a preview; the
  placed order is priced again the same way (the browser's numbers are never used).
- **Usage limit**: one use is taken when the order is placed, atomically (two customers can't both take the last
  use); if the order then fails the use is given back. A used-up coupon is refused with `PROMOTION_USED_UP`;
  other reasons: `PROMOTION_NOT_FOUND` (unknown or inactive), `PROMOTION_NOT_STARTED`, `PROMOTION_EXPIRED`,
  `PROMOTION_NOT_APPLICABLE`, `MINIMUM_ORDER_NOT_REACHED`.
- **Deactivate instead of delete**: a coupon that has been used can't be deleted (orders refer to it); clear its
  *Active* flag instead. Unused coupons can be deleted.
- Cancelling, rejecting or refunding an order before it ships gives its coupon use back (once; see Order lifecycle).
  "My orders" shows the total without a separate discount line.

## Observability

Metrics, logs and traces of every Spring service in Grafana (slice 6, `decisions.md` 6-1..6-19). **Off by default**
(memory): the services only export when `OTEL_EXPORTER_OTLP_ENDPOINT` is set, which the add-on file does.

```bash
# on: collector, Prometheus, Loki, Tempo, Grafana, kafka-exporter + the OTLP endpoint for every service
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --wait
# off: services recreated without the endpoint, then remove the add-on containers (their volumes are kept)
docker compose up -d --wait
docker compose -f docker-compose.yml -f docker-compose.observability.yml rm -sf otel-collector prometheus loki tempo grafana kafka-exporter
```

Needs `grafana.shopapp.local` in the hosts line (see Prerequisites): `127.0.0.1 grafana.shopapp.local`.

| What | Where |
|---|---|
| Grafana | http://grafana.shopapp.local - read-only without login; admin `admin` / `GRAFANA_ADMIN_PASSWORD` from `.env` (`grafana-dev`) |
| Dashboards | folder *shop_app*: **Service: JVM & HTTP** (pick a service: requests, errors, p95, outgoing calls, heap, GC, threads, its log lines) and **Kafka consumer lag** (search, recommendation, webhook groups) |
| Traces | Explore → Tempo, e.g. TraceQL `{ resource.service.name = "storefront-bff" } && { resource.service.name = "order" }`: a checkout is one trace storefront-bff → order → location, tax, product, cart; "Logs for this span" opens its Loki lines |
| Logs | Explore → Loki: `{service_name="order"}` or `{service_name=~".+"} \| trace_id="<trace id>"`; a line's *View trace* opens Tempo. Console lines carry `[traceId-spanId]` too, also with the add-on off |
| Metrics | Explore → Prometheus: the service name is the `job` label (`jvm_memory_used_bytes{job="tax"}`, `http_server_requests_seconds_count`, `kafka_consumergroup_lag`) |

How it works: `common-library` brings Spring Boot's OpenTelemetry support to every service and both BFFs; spans,
log records and metrics are pushed over OTLP/HTTP to the collector, which sends traces to Tempo, logs to Loki and
metrics to Prometheus (the services are not scraped; no host ports - only Grafana is reachable, through nginx).
Export is asynchronous with bounded queues: when the collector is down, telemetry is dropped and requests are not
affected. Calls between services are traced because the services build their RestClients with
`ObservedRestClient.builder()` (use it for new clients too). Debezium CDC messages carry no trace context, so each
consumed CDC event starts its own trace. Actuator requests are not traced (nor their Spring Security spans), and
`/<service>/actuator/**` other than `health` is blocked at nginx. Outgoing-call spans keep the URL without its query
string. A browser cannot pick the trace: the storefront, backoffice and identity hosts drop incoming `traceparent`,
`tracestate` and `baggage` (the api host keeps them, the BFFs and services call it with their own). BFF console lines
carry trace ids with the add-on off too (`spring.reactor.context-propagation=auto`).

Memory: about **1.2-1.3 GB** on top of the stack - ~0.6 GB for the six add-on containers (Grafana, Prometheus,
Tempo, Loki, collector, kafka-exporter; each has a memory limit) plus ~15-50 MB per exporting JVM. Turn it on only
with that much free in the Docker VM: when the VM swaps, the backends time out and telemetry is dropped (the shop
keeps working).

## Kubernetes

Helm charts for every workload (`k8s/charts`: a library chart, one thin chart per service/app, the umbrella
`shopapp`), the infrastructure as manifests (`k8s/deploy`: CloudNativePG Postgres, Keycloak, Redis, Kafka + Connect,
Elasticsearch) and a local kind cluster (decisions.md 6-20..6-37, 6-60..6-67). Details: `k8s/README.md`.

```bash
K8S_PROFILE=core bash scripts/k8s-up.sh   # slice 1-2 path (~6 GiB); without K8S_PROFILE the whole shop (~12 GiB)
bash scripts/k8s-down.sh
```

The ingress (ingress-nginx) answers on `127.0.0.1:8000` with the compose hostnames
(`http://storefront.shopapp.local:8000`), images come from GHCR (`K8S_IMAGES=local` uses the compose images). The
edge refuses what compose nginx refuses; `k8s/scripts/check-edge-parity.sh` checks that in CI.

## Resources

- The whole stack (about 25 containers, including Elasticsearch and Kafka Connect) uses about **7.5 GB of RAM**
  when idle (measured with `docker stats` after slice 4): Elasticsearch ~0.8 GB (container limit 1 GB),
  Keycloak ~0.65 GB, Kafka Connect ~0.5 GB, Kafka ~0.45 GB, each Spring service 0.3–0.45 GB.
- Every Java service's JVM is capped in its Dockerfile
  (`JAVA_TOOL_OPTIONS=-Xmx320m -Xss512k -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC`),
  Keycloak at 768 MB heap and Kafka at 512 MB (`docker-compose.yml`); without the caps each JVM could grow to a
  quarter of the Docker VM and the VM ran out of memory.
- Each Spring service uses a **Hikari pool of 5** connections (`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` in its
  Dockerfile), Keycloak at most 20, and Postgres allows 200 connections, so the services sharing one Postgres no
  longer run out of connections.
- Give Docker (Docker Desktop / Rancher Desktop VM) **about 10 GB of free memory**: the stack plus `mvn verify`'s
  Testcontainers (Postgres, Kafka, Elasticsearch) need it. Run one Testcontainers build at a time.

|---|---|---|
| Keycloak admin console | http://identity.shopapp.local/admin | `admin` / `admin` (master realm) |
| Keycloak realm `shopapp` | http://identity.shopapp.local/realms/shopapp | users `admin`/`admin` (ADMIN), `customer`/`customer` (CUSTOMER) |
| pgAdmin | http://pgadmin.shopapp.local | no login (desktop mode); server password `admin` |
| Kafka UI | http://kafka.shopapp.local | — |
| **Storefront** | http://storefront.shopapp.local | log in as `customer` / `customer` (or register) |
| **Back office** | http://backoffice.shopapp.local | `admin` / `admin` (ADMIN role only; customers get 403) |
| API gateway | http://api.shopapp.local/product/storefront/... | product API; other paths return 404 |
| Product API docs | http://api.shopapp.local/product/swagger-ui | |
| Search API | http://api.shopapp.local/search/storefront/catalog-search?keyword=galaxy | public; `POST /search/backoffice/reindex` needs ADMIN |
| Cart / customer / order APIs | http://api.shopapp.local/cart/..., /customer/..., /order/... | need a Keycloak access token |
| Media API / images | http://api.shopapp.local/media/medias/{id}, .../medias/{id}/file/{fileName} | GETs public; upload/delete need ADMIN |
| Location API | http://api.shopapp.local/location/storefront/countries (public lookups), .../location/storefront/addresses, .../location/backoffice/... | lookups public; addresses need a token; `backoffice` needs ADMIN |
| Tax API | http://api.shopapp.local/tax/backoffice/tax-classes, .../tax/backoffice/tax-rates | ADMIN (order reads rates with the customer's token) |
| Inventory API | http://api.shopapp.local/inventory/backoffice/warehouses, .../inventory/backoffice/stocks | ADMIN |
| Storefront search page | http://storefront.shopapp.local/search?keyword=galaxy | public (header search box with suggestions) |
| Recommendation API | http://api.shopapp.local/recommendation/embedding/product/{id}/similarity | public; `POST /recommendation/backoffice/reindex` needs ADMIN |
| Webhook API / test receiver | http://api.shopapp.local/webhook/backoffice/webhooks, `docker compose logs webhook-receiver` | ADMIN; back office System → Webhooks |
| Payment API | http://api.shopapp.local/payment/storefront/payment-providers, .../payment/init, .../payment/capture, .../payment/backoffice/payments | provider list public; init/capture/cancel CUSTOMER; `backoffice` ADMIN; back office Sales → Payments, Payment providers |
| Rating API | http://api.shopapp.local/rating/storefront/ratings/products/{productId}, .../rating/storefront/ratings/product/{productId}/average-star, .../rating/backoffice/ratings | lists and averages public; posting/deleting own reviews needs CUSTOMER; `backoffice` ADMIN |
| Promotion API | http://api.shopapp.local/promotion/backoffice/promotions, .../promotion/storefront/promotions/verify | `backoffice` ADMIN; `storefront` any logged-in user (order calls it with the customer's token) |
| Back office: reviews, promotions | http://backoffice.shopapp.local/reviews, /promotion/manager-promotion | `admin` / `admin` |
| Back office: location, tax, inventory | http://backoffice.shopapp.local/location/countries, /location/state-or-provinces, /tax/tax-classes, /tax/tax-rates, /inventory/warehouses, /inventory/warehouse-products, /inventory/warehouse-stocks | `admin` / `admin` |
| Postgres (from host) | `localhost:5432` | `admin` / `admin` |
| Kafka (from host) | `localhost:29092` | — (in-network: `kafka:9092`) |

Keycloak clients: `storefront-bff`, `backoffice-bff`, `customer-management` (confidential; dev secrets in
`identity/realm-export.json`) and `swagger-ui` (public; password grant enabled for local testing).

## CI

GitHub Actions (`.github/workflows`), all on push and pull request to `master`:

| Workflow | Runs when | What it does |
|---|---|---|
| **Backend CI** (`backend-ci.yml`) | a module's `src/`, `pom.xml` or `Dockerfile`, the root `pom.xml`, `checkstyle/**`, `docker/postgres/**` or the backend CI files change | one job per touched Maven module (list read from the root pom; a change to `common-library`, the root pom, checkstyle or the CI files builds every module; files a module reads from outside its directory - product's changelogs and `docker/postgres/sampledata*` for sampledata - select it too; `changed-modules.test.sh` tests that) running the reusable `maven-module.yml`: JDK 27 (Temurin), `mvn -pl <module> -am verify` (Checkstyle, unit tests, integration tests with Testcontainers on the runner's Docker), JaCoCo report artifact, test reports on failure |
| **Storefront CI** / **Backoffice CI** | `storefront/**` / `backoffice/**` | reusable `node-app.yml` on Node 24: `npm ci`, lint, typecheck, test, build |
| **Charts CI** (`charts-ci.yml`) | `k8s/**`, `nginx/**` | `k8s/scripts/validate-charts.sh` (`helm lint`, `helm template \| kubeconform -strict` for every chart, `values-core.yaml` and `k8s/deploy`) and `k8s/scripts/check-edge-parity.sh` (ingress vs compose nginx) |
| **Secret scan** (`security.yml`) | every push/PR, nightly | gitleaks over the whole history (`.gitleaks.toml`: default rules; only the three documented dev Keycloak client secrets in `.env` and the realm export are allowed) |
| **CodeQL** (`codeql.yml`) | every push/PR except docs, weekly | Java (Maven build) and JavaScript/TypeScript code scanning |

Images: on push to `master`, after the module/app checks are green, the changed services and apps are built and
pushed to `ghcr.io/nduyhaing/shopapp-<service>:<commit sha>` and `:latest` (`GITHUB_TOKEN`; never on pull requests).
Each master push runs on its own (pushes are never cancelled or dropped while queued; PR runs cancel superseded
ones). Dependabot (`.github/dependabot.yml`) opens weekly PRs for Maven, npm, GitHub Actions, the Dockerfile base
images, `k8s/deploy` and `docker-compose*.yml` images, with minor and patch updates grouped. Third-party actions are
pinned to commit SHAs. CodeQL's `java/spring-disabled-csrf-protection` alerts are dismissed as false positives (BFFs:
`CrossSiteRequestFilter`, decisions.md 2b-7; webhook: bearer tokens only). Run `gitleaks git` locally before pushing
a new allowlisted dev secret (the scan checks the whole history on every push).

## Build notes

- Spring Boot **4.1.2** (newest 4.x at the time of slice 1) on JDK 27 build 27+35.
- **Spring Cloud 2025.1.4** for storefront-bff (Spring Cloud Gateway). Spring Cloud has no release train for
  Boot 4.1 (2025.1 targets Boot 4.0, 2026.0 targets Boot 4.2), so the Spring Cloud compatibility check is
  off in storefront-bff; the gateway tests cover the routes. Move to Boot 4.2 + Spring Cloud 2026.0 when GA.
- **Lombok** 1.18.48: kept. It works on JDK 27 but prints a `sun.misc.Unsafe` deprecation warning during
  compilation; harmless for now.
- **MapStruct** 1.7.0.Beta2 (newest; beta) and **JaCoCo** 0.8.16 (reports in each module's `target/site/jacoco`).
- Checkstyle fails the build on any warning.
- Kafka runs in KRaft mode (no ZooKeeper).
- Images: postgres 16.16, pgAdmin 9.19.0, Keycloak 26.8.2, apache/kafka 4.4.0, kafbat/kafka-ui v1.5.0,
  nginx 1.30.6, eclipse-temurin 27-jre (Java services), node 24-alpine (storefront),
  quay.io/debezium/connect 3.7.1.Final (Kafka Connect 4.3.1 clients against the 4.4 broker), curlimages/curl 8.17.0.
- Storefront: Next.js 16.4.6, React 19.3.1, react-bootstrap 2.10.10, TypeScript 6.0.3, ESLint 9.39.5, Vitest 5.1.0.
- Redis 7.4.12 for BFF sessions. nginx resolves containers through Docker DNS on each request, so rebuilt
  containers keep working without restarting nginx.
- Every decision and its reason: see `decisions.md`.
