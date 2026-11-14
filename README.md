# shop_app

A microservices e-commerce system on Java 27 and Spring Boot 4. See `NOTICE` for third-party licenses.

## Roadmap

The system is built in slices; each ends in something that runs end to end.

1. **Foundation** — parent POM, `common-library`, Postgres, Keycloak, Kafka, nginx *(done)*
2. Core shopping flow, in three steps:
   - **2a Browse** — product service, storefront-bff, storefront catalog pages *(done)*
   - 2b Log in + cart — customer and cart services, Keycloak login through the BFF
   - 2c Checkout — order service, checkout, "my orders"
3. Back office: backoffice-bff, backoffice UI, media, inventory, tax, location
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
  127.0.0.1 identity.shopapp.local api.shopapp.local pgadmin.shopapp.local kafka.shopapp.local storefront.shopapp.local
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

## URLs and credentials

**Local development only — never reuse these anywhere else.**

| What | URL | Credentials |
|---|---|---|
| Keycloak admin console | http://identity.shopapp.local/admin | `admin` / `admin` (master realm) |
| Keycloak realm `shopapp` | http://identity.shopapp.local/realms/shopapp | users `admin`/`admin` (ADMIN), `customer`/`customer` (CUSTOMER) |
| pgAdmin | http://pgadmin.shopapp.local | no login (desktop mode); server password `admin` |
| Kafka UI | http://kafka.shopapp.local | — |
| **Storefront** | http://storefront.shopapp.local | no login yet (slice 2b) |
| API gateway | http://api.shopapp.local/product/storefront/... | product API; other paths return 404 |
| Product API docs | http://api.shopapp.local/product/swagger-ui | |
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
  nginx 1.30.6, eclipse-temurin 27-jre (Java services), node 24-alpine (storefront).
- Storefront: Next.js 16.4.6, React 19.3.1, react-bootstrap 2.10.10, TypeScript 6.0.3, ESLint 9.39.5, Vitest 5.1.0.
- Every decision and its reason: see `decisions.md`.
