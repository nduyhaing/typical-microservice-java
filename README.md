# shop_app

A microservices e-commerce system on Java 27 and Spring Boot 4. See `NOTICE` for third-party licenses.

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
4. Events & search: Debezium CDC → Kafka, search (Elasticsearch), promotion, rating
5. Payments & the rest: payment, payment-paypal, delivery, webhook, recommendation, sampledata
6. Operations: observability (OTel, Prometheus, Grafana, Loki, Tempo), Kubernetes, CI

Designs and plans live in `docs/superpowers/`.

## Prerequisites

- **JDK 27.** Maven must run on it: set `JAVA_HOME` to the JDK 27 folder
  (e.g. `~/.jdks/openjdk-27`) before running `mvn`.
- **Maven 3.9+**
- **Docker** with Compose v2 (Docker Desktop or Rancher Desktop)
- **Python 3** — only used by the smoke-test script
- **Hosts entries.** As Administrator, add this line to `C:\Windows\System32\drivers\etc\hosts`
  (or `/etc/hosts` on macOS/Linux):

  ```
  127.0.0.1 identity.shopapp.local api.shopapp.local pgadmin.shopapp.local kafka.shopapp.local storefront.shopapp.local backoffice.shopapp.local
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
  payment" with server-computed prices and tax; discounts, shipping fees and real payments come in later slices
  (cash on delivery only for now).
- **Tax, stock and addresses** (slice 3c, `decisions.md` 3c-*): see [Tax, stock and addresses](#tax-stock-and-addresses).
- **Images**: in the back office, the product form's thumbnail and gallery and the category form's image upload a
  file as soon as it is chosen (PNG, JPEG or GIF, up to **10 MB**; the type is checked by decoding the image,
  not by its name). Images are served by the media service at
  `http://api.shopapp.local:80/media/medias/{id}/file/{fileName}` (public, cached for a day). Files live on the
  Docker volume `media-data` (`docker compose down -v` deletes them). If the media service is down, product
  pages still load and show placeholder images.
- **Keycloak volume created before slice 2b?** Run `bash identity/scripts/apply-kc-admin-api-scope.sh` once
  (adds the admin-API audience scope the customer service needs; fresh volumes get it from the realm file).
- **CDC (change data capture)**: see [CDC](#cdc-change-data-capture) below.
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
  `PUT /connectors/product-connector/config` on every `up`, then restarts a FAILED connector every 30 s).
- **Messages**: key `{"id":<product id>}`, value the Debezium envelope without schema:
  `{"before":…,"after":{…product columns…},"op":"c|u|d|r","source":…}`. `r` = initial snapshot (all products, once,
  when the connector first starts), a delete is followed by a tombstone (null value). Every product row update
  sends a message, including stock changes (`stock_quantity` is a product column).
- **Check it**: Kafka UI (http://kafka.shopapp.local) → *Kafka Connect* → `product-connector` (state, tasks,
  restart) and *Topics* → `dbproduct.public.product` → *Messages*. From the shell:
  `docker compose exec kafka-connect curl -s localhost:8083/connectors?expand=status`. The smoke script renames a
  product and waits for its message on the topic.
- **Postgres user**: Debezium logs in as `DEBEZIUM_DB_USER` (`.env`, default `debezium`) with `REPLICATION` and
  `SELECT` on `public.product` only, not the superuser. After changing `DEBEZIUM_DB_PASSWORD`, run
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
  takes 30–90 s to become healthy). If it is down, the shop keeps working; only the CDC topic (and later search
  updates) stops, and it catches up from its saved position when it comes back.
- **Volume created before slice 4?** Nothing to do: `debezium-db-init` creates the user on the next `up`
  (`postgres_init.sql` only runs on an empty volume).

## URLs and credentials

**Local development only — never reuse these anywhere else.**

| What | URL | Credentials |
|---|---|---|
| Keycloak admin console | http://identity.shopapp.local/admin | `admin` / `admin` (master realm) |
| Keycloak realm `shopapp` | http://identity.shopapp.local/realms/shopapp | users `admin`/`admin` (ADMIN), `customer`/`customer` (CUSTOMER) |
| pgAdmin | http://pgadmin.shopapp.local | no login (desktop mode); server password `admin` |
| Kafka UI | http://kafka.shopapp.local | — |
| **Storefront** | http://storefront.shopapp.local | log in as `customer` / `customer` (or register) |
| **Back office** | http://backoffice.shopapp.local | `admin` / `admin` (ADMIN role only; customers get 403) |
| API gateway | http://api.shopapp.local/product/storefront/... | product API; other paths return 404 |
| Product API docs | http://api.shopapp.local/product/swagger-ui | |
| Cart / customer / order APIs | http://api.shopapp.local/cart/..., /customer/..., /order/... | need a Keycloak access token |
| Media API / images | http://api.shopapp.local/media/medias/{id}, .../medias/{id}/file/{fileName} | GETs public; upload/delete need ADMIN |
| Location API | http://api.shopapp.local/location/storefront/countries (public lookups), .../location/storefront/addresses, .../location/backoffice/... | lookups public; addresses need a token; `backoffice` needs ADMIN |
| Tax API | http://api.shopapp.local/tax/backoffice/tax-classes, .../tax/backoffice/tax-rates | ADMIN (order reads rates with the customer's token) |
| Inventory API | http://api.shopapp.local/inventory/backoffice/warehouses, .../inventory/backoffice/stocks | ADMIN |
| Back office: location, tax, inventory | http://backoffice.shopapp.local/location/countries, /location/state-or-provinces, /tax/tax-classes, /tax/tax-rates, /inventory/warehouses, /inventory/warehouse-products, /inventory/warehouse-stocks | `admin` / `admin` |
| Postgres (from host) | `localhost:5432` | `admin` / `admin` |
| Kafka (from host) | `localhost:29092` | — (in-network: `kafka:9092`) |

Keycloak clients: `storefront-bff`, `backoffice-bff`, `customer-management` (confidential; dev secrets in
`identity/realm-export.json`) and `swagger-ui` (public; password grant enabled for local testing).

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
