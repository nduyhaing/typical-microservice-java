# shop_app — Slice 1: Foundation — Design

Date: 2026-11-14
Status: Draft, awaiting approval

## Intent and why

`shop_app` is a rebuild of [YAS (Yet Another Shop)](https://github.com/nashtech-garage/yas) — a Spring Boot
microservices e-commerce system — kept almost identical in architecture and features, but written from
scratch so every part is understood. YAS is MIT-licensed; where code is ported closely, keep a credit line
in `NOTICE` / `README.md`.

The system is built in slices, each ending in something that runs end to end:

1. **Foundation** (this document)
2. Core shopping flow: product, cart, order, customer, storefront-bff, minimal storefront
3. Back office: backoffice-bff, backoffice UI, media, inventory, tax, location
4. Events & search: Debezium CDC → Kafka, search (Elasticsearch), promotion, rating
5. Payments & the rest: payment, payment-paypal, delivery, webhook, recommendation, sampledata
6. Operations: observability (OTel, Prometheus, Grafana, Loki, Tempo), Kubernetes, CI

Each later slice gets its own design and plan.

## Goals of slice 1

At the end of this slice:

- A Maven multi-module build exists with a parent POM targeting **Java 27** and **Spring Boot 4.0.x**,
  dependency management for everything later slices need, and Checkstyle wired in.
- `common-library` exists with the cross-cutting code every service will use, with unit tests.
- `docker compose up` starts the shared infrastructure, all healthy:
  PostgreSQL, pgAdmin, Keycloak (realm imported), Kafka (KRaft), Kafka UI, and nginx as the entry point.
- A developer can get a Keycloak access token for the `shopapp` realm through nginx
  (`http://identity.shopapp.local`), create a Kafka topic via Kafka UI, and connect to Postgres.

## Non-goals (deferred to later slices)

- Any business service, BFF, or frontend (slice 2+).
- Kafka Connect / Debezium connectors, Elasticsearch (slice 4) — but Postgres is configured with
  `wal_level=logical` now so CDC needs no DB rebuild later.
- Redis (slice 2, needed by the BFFs for sessions).
- Observability stack, OTel java agent, Kubernetes, GitHub Actions (slice 6).
- Custom Keycloak login theme (YAS has one; add with the storefront in slice 2/3).
- Swagger UI aggregator (added once services exist).

## Anti-goals

- Diverging from YAS's architecture for taste. Deviations are allowed only where YAS's choice is
  obsolete (listed under Decisions) and each is written down.
- A foundation nobody can run: if `docker compose up` needs manual fiddling beyond the documented
  hosts-file entries, the slice is not done.
- Building infrastructure for slices that may change shape (e.g. pre-creating Kafka topics).

## Constraints

- Developer machine: Windows 11, Docker (Rancher Desktop, `docker` 29.x), Maven 3.9.16.
  Only JDK 21 is installed; **JDK 27 must be installed** (Temurin or Microsoft Build of OpenJDK) and
  used for the build (`JAVA_HOME` or Maven toolchains).
- Java 27 is a non-LTS release (support ends ~March 2027 when Java 28 ships). Accepted by the user.
- Library support for Java 27 may lag, notably **Lombok** and annotation processors / Maven plugins.
- Directory: `C:\DHNlearn\Java25\typical-microservice-java` is the repository root (currently empty,
  not yet a git repo — initialise one).

## Approaches considered (whole project)

| | Approach | Trade-off |
|---|---|---|
| A | Fork YAS, rename to shop_app, bump Java | Runs in a day; little learning, inherits all quirks |
| **B** | **Rebuild from scratch, same architecture, in vertical slices** | **Weeks of work; every part understood, runnable after each slice** |
| C | Stub all services at once, fill in later | Early full shape; nothing works end to end for a long time, stubs rot |

**Chosen: B** (user's pick), starting with the foundation.

## Decisions

Decided by the user:
- Architecture and features almost identical to YAS.
- Java 27 (instead of YAS's Java 25).
- Approach B, foundation first.

Calls made by Claude (stated to the user, open to correction):
- **Package root** `com.shopapp`; Maven `groupId` `com.shopapp`; library package `com.shopapp.commonlibrary`.
- **Kafka in KRaft mode** (no ZooKeeper). YAS uses ZooKeeper + `cp-kafka 7.7`; ZooKeeper is removed in
  Kafka 4. Use the official `apache/kafka` image (4.x) — or `confluentinc/cp-kafka` 8.x — single broker.
- **Hostnames via nginx** like YAS: `identity.shopapp.local`, `api.shopapp.local`,
  `pgadmin.shopapp.local`, `kafka.shopapp.local` (Kafka UI); later `storefront.shopapp.local`,
  `backoffice.shopapp.local`. Developer adds them to `C:\Windows\System32\drivers\etc\hosts` → `127.0.0.1`.
  `api.shopapp.local` exists in the nginx config now but routes nothing yet (returns 404 / placeholder).
- **Lombok**: keep it (as YAS) if it compiles cleanly on JDK 27; otherwise drop it and use records and
  plain classes. The first build task verifies this before any Lombok-dependent code is written.
  Same check for MapStruct.
- **YAS reference pinned** to commit `179e813568c345bad3fce985088b5535e57481aa` (main, 2026-06-12).
  "Mirror YAS" in this and later slices means this commit; record it in `NOTICE`. Moving the pin is a
  deliberate decision, not drift.
- **Java 27 compatibility rule.** The first build task is a compatibility spike: an empty parent POM +
  `common-library` with one test, built with `release 27` on JDK 27 through every plugin (compiler,
  Lombok, MapStruct, Checkstyle, Surefire/Failsafe, JaCoCo, Testcontainers). "Spring Boot 4.0.x" means
  **the newest stable Boot 4.x minor that supports Java 27** (with its matching Spring Cloud train), not a
  hard pin to 4.0. If something fails:
  1. upgrade that library/plugin to a version that supports Java 27;
  2. else, if it is non-essential (JaCoCo), disable it for now and note it in the README;
  3. Lombok/MapStruct → fallback described below;
  4. if Spring Boot / Spring Cloud / Hibernate itself cannot run on Java 27 → **stop and ask the user**
     (do not silently drop to `release 25`).
- **Naming kept in one place.** `com.shopapp` (groupId/package) and the `shopapp.local` domain were
  stated to the user before writing this doc. The domain lives only in `.env` (nginx template, Keycloak
  hostname and redirect URIs read it via env substitution where possible) so changing it stays cheap.
- **Versions**: latest stable at implementation time, pinned in the parent POM / compose file
  (no `latest` tags). Keycloak 26.x, Postgres 16 with logical replication (YAS uses `debezium/postgres:16`;
  the plain `postgres:16` image with `wal_level=logical` is equivalent and better maintained — builder's call).

## Design

### Repository layout

```
/
├── pom.xml                      # parent POM (aggregator + dependency management)
├── checkstyle/                  # checkstyle.xml + suppressions (adapted from YAS)
├── common-library/              # shared Java library (jar)
├── docker/
│   └── postgres/                # postgresql.conf overrides (wal_level=logical)
├── identity/
│   └── realm-export.json        # Keycloak realm "shopapp"
├── nginx/
│   └── templates/default.conf.template
├── postgres_init.sql            # creates per-service databases
├── docker-compose.yml
├── .env                         # compose variables (hostnames, ports, credentials for local dev)
├── NOTICE / README.md           # how to run; YAS credit
└── docs/superpowers/specs/      # design docs
```

### Parent POM

- `spring-boot-starter-parent` 4.x (per the compatibility rule above) as parent; `<java.version>27</java.version>`;
  `maven.compiler.release=27`.
- `<modules>`: `common-library` only (each slice adds its modules).
- `dependencyManagement`: Spring Cloud BOM matching Boot 4, Testcontainers BOM, springdoc-openapi,
  MapStruct, Lombok (if kept), Keycloak admin client, logstash-logback-encoder — mirror YAS's list,
  dropping entries no slice will use.
- Plugins: maven-checkstyle-plugin (fails build on violations, YAS config), surefire/failsafe,
  jacoco (report only, no gate yet).
- `revision` property for versioning (`1.0-SNAPSHOT`), as YAS does.

### common-library

Port these from YAS `common-library` (rewrite, don't copy blindly; adapt to `com.shopapp`):

- `exception/`: `NotFoundException`, `BadRequestException`, `DuplicatedException`, `AccessDeniedException`,
  `ForbiddenException`, `SignInRequiredException`, `InternalServerErrorException`,
  and `ApiExceptionHandler` (`@RestControllerAdvice`) returning `ErrorVm`. Service-specific exceptions
  from YAS (`StockExistingException`, `CreateGuestUserException`, etc.) are added by the slice that needs them.
- `viewmodel/error/ErrorVm` (record).
- `model/AbstractAuditEntity` + `listener/CustomAuditingEntityListener` (createdBy/createdOn/lastModifiedBy/
  lastModifiedOn, user from the JWT).
- `utils/AuthenticationUtils` (current user id from JWT), `MessagesUtils` (message bundle lookup),
  `DateTimeUtils`.
- `constants/ApiConstant`, `MessageCode`, `PageableConstant`.
- `config/CorsConfig`, `config/ServiceUrlConfig`.
- `resources/messages/messages.properties`, `logback-spring.xml` (console + JSON via logstash encoder;
  OTel appender comes in slice 6).
- **Deferred**: `kafka/cdc/*` (slice 4), `csv/*` (slice 3), `mapper/*` (with first service that uses MapStruct).

Tests: unit tests for `ApiExceptionHandler` (each exception → status + `ErrorVm`), `MessagesUtils`,
`AuthenticationUtils`, and the auditing listener.

### Docker Compose

Single network `shopapp-network`. Services:

| Service | Purpose | Notes |
|---|---|---|
| `postgres` | One server, one DB per service | `wal_level=logical`; `postgres_init.sql` creates `keycloak` plus DBs for **all** YAS services (product, cart, order, customer, media, rating, location, inventory, promotion, tax, payment, webhook, delivery…) so later slices just connect. Healthcheck `pg_isready`. Volume for data. |
| `pgadmin` | DB admin UI | Behind `pgadmin.shopapp.local`. |
| `identity` | Keycloak 26.x | Uses `keycloak` DB; `--import-realm` from `identity/realm-export.json`; hostname `identity.shopapp.local`; dev mode acceptable locally. Healthcheck via management port. |
| `kafka` | Single-broker KRaft | Internal listener `kafka:9092`, host listener `localhost:29092`. |
| `kafka-ui` | Kafka admin UI | Behind `kafka.shopapp.local`. |
| `nginx` | Entry point on port 80 | Virtual hosts per hostname above; `api.shopapp.local` placeholder. `depends_on` with health conditions. |

Credentials and hostnames live in `.env` (local-dev values only; documented as not for production).

### Keycloak realm `shopapp`

Mirror YAS's realm (`Yas`), renamed:
- Realm roles: `ADMIN`, `CUSTOMER`, `GUEST`.
- Clients: `storefront-bff`, `backoffice-bff` (confidential, standard flow, redirect URIs for the future
  BFF hosts), `customer-management` (service account, realm-management roles for user admin),
  `swagger-ui` (public, PKCE). Client secrets are local-dev values in the realm file.
- Users: `admin` (role ADMIN) and one test customer (role CUSTOMER), with documented dev passwords.
- Self-registration enabled (storefront sign-up in slice 2).

### Definition of done

1. `mvn verify` succeeds on JDK 27 (compile, Checkstyle, common-library tests).
2. `docker compose up -d` → all containers healthy within a few minutes on a clean machine
   (after hosts-file entries).
3. `POST http://identity.shopapp.local/realms/shopapp/protocol/openid-connect/token` with the test user
   and the `swagger-ui` client (password grant enabled for dev, or via browser PKCE) returns a token
   containing the realm role.
4. Kafka UI at `http://kafka.shopapp.local` shows the cluster; a topic can be created.
5. pgAdmin at `http://pgadmin.shopapp.local` connects to Postgres and lists the per-service databases.
6. `README.md` documents prerequisites (JDK 27, Docker, hosts entries), commands, URLs and dev credentials.

## Left to the builder

- Exact image and library versions (latest stable, pinned).
- `apache/kafka` vs `cp-kafka` 8.x; plain `postgres:16` vs `debezium/postgres`.
- Whether to use Maven toolchains or rely on `JAVA_HOME` for JDK 27.
- Checkstyle rule tweaks needed for Java 27 syntax.
- Fallback from Lombok to records if Lombok fails on JDK 27 (report back if it happens).
