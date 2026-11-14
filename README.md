# shop_app

A microservices e-commerce system, rebuilt from scratch after
[YAS (Yet Another Shop)](https://github.com/nashtech-garage/yas) — same architecture and features,
on Java 27 and Spring Boot 4. See `NOTICE` for the YAS credit and reference commit.

## Roadmap

The system is built in slices; each ends in something that runs end to end.

1. **Foundation** — parent POM, `common-library`, Postgres, Keycloak, Kafka, nginx *(this slice — done)*
2. Core shopping flow: product, cart, order, customer, storefront-bff, minimal storefront
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
  127.0.0.1 identity.shopapp.local api.shopapp.local pgadmin.shopapp.local kafka.shopapp.local
  ```

## Run it

```bash
mvn verify                              # build + Checkstyle + tests (on JDK 27)
docker compose up -d --wait             # start the infrastructure
bash scripts/verify-foundation.sh       # smoke test (add --restart to also test a down/up cycle)
docker compose down                     # stop, keep data
docker compose down -v                  # stop and delete all data
```

Notes:

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
| API gateway (placeholder) | http://api.shopapp.local | returns 404 until slice 2 |
| Postgres (from host) | `localhost:5432` | `admin` / `admin` |
| Kafka (from host) | `localhost:29092` | — (in-network: `kafka:9092`) |

Keycloak clients: `storefront-bff`, `backoffice-bff`, `customer-management` (confidential; dev secrets in
`identity/realm-export.json`) and `swagger-ui` (public; password grant enabled for local testing).

## Build notes

- Spring Boot **4.1.2** (newest 4.x at the time of slice 1) on JDK 27 build 27+35.
- Spring Cloud is not managed yet: its release train for Boot 4.1 (2026.0) was still a release
  candidate. It is added in slice 2.
- **Lombok** 1.18.48: kept. It works on JDK 27 but prints a `sun.misc.Unsafe` deprecation warning during
  compilation; harmless for now.
- **MapStruct** 1.6.3: kept. **JaCoCo** 0.8.16: kept (report in `common-library/target/site/jacoco`).
- Checkstyle (config from YAS) fails the build on any warning.
- Kafka runs in KRaft mode (no ZooKeeper), unlike YAS which still uses ZooKeeper.
- Images: postgres 16.16, pgAdmin 9.19.0, Keycloak 26.8.2, apache/kafka 4.4.0, kafbat/kafka-ui v1.5.0,
  nginx 1.30.6.
