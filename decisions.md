# Decisions

Every decision taken while building shop_app, newest slice last. Each entry says what was decided,
why, and what it costs if it turns out wrong. "User" = decided by the project owner; "Claude" = decided
during implementation (the owner asked for automatic implementation with decisions recorded here).

## Project-wide

| # | Decision | By | Why | Cost if wrong |
|---|---|---|---|---|
| P1 | Rebuild the YAS microservices shop from scratch, architecture and features almost identical | User | Learning; every part understood | — |
| P2 | Java 27 (YAS uses 25) | User | Newest Java; non-LTS accepted | Upgrade churn; some libraries lag |
| P3 | Build in vertical slices (foundation → shopping flow → back office → events/search → payments → ops) | User | Something runs end to end after each slice | — |
| P4 | YAS reference pinned at commit `179e813568c345bad3fce985088b5535e57481aa`; "mirror YAS" means that commit | Claude | Stable target across slices | Miss later YAS fixes |
| P5 | Package root and groupId `com.shopapp`; domain `shopapp.local`, kept only in `.env` | Claude | One place to rename | Rename touches every module |
| P6 | Maven builds use Maven Central via a scratch settings file; `~/.m2/settings.xml` (work mirror, unreachable off VPN) untouched | User | Mirror unreachable | On VPN, plain `mvn` works |
| P7 | Commits authored as `nduyhai@gmail.com`, pushed to `github.com/nduyhaing/typical-microservice-java` (public) with the personal gh account; earlier commits rewritten before first push | User | Personal project | — |
| P8 | YAS mentions removed from README, POM and code; `NOTICE` keeps only the MIT license text legally required for derived files; design docs keep the YAS reference | User + Claude | Owner's request; MIT requires the notice | — |
| P9 | Implementation runs automatically; decisions recorded in this file instead of asked | User | Owner's request | Owner reviews decisions after the fact |
| P10 | Commit directly on `master` (no feature branches) | User | Owner's request | No branch to review or discard as a unit |

## Slice 1 — Foundation (merged)

| # | Decision | By | Why | Cost if wrong |
|---|---|---|---|---|
| F1 | Spring Boot 4.1.2 (newest 4.x); predates JDK 27 GA but builds and tests pass on 27 | Claude | Newest stable Boot | Untested runtime corner on 27 |
| F2 | Lombok 1.18.48, MapStruct 1.6.3, JaCoCo 0.8.16 kept (Lombok prints a harmless `sun.misc.Unsafe` warning) | Claude | Probe test passed on JDK 27 | Drop Lombok if a later JDK breaks it |
| F3 | Checkstyle (YAS config) fails the build on any warning (YAS doesn't fail) | Claude | Plan required a failing gate | Stricter than YAS |
| F4 | Kafka 4.4 in KRaft mode, no ZooKeeper (YAS uses ZooKeeper) | Claude | ZooKeeper removed in Kafka 4 | — |
| F5 | kafbat/kafka-ui instead of provectuslabs/kafka-ui | Claude | Maintained fork | — |
| F6 | Databases match YAS `postgres_init.sql` exactly (14 incl. keycloak); no `delivery` DB | Claude | YAS delivery has no datasource | One line to add later |
| F7 | Postgres `wal_level=logical` from the start | Claude | Debezium CDC in slice 4 without DB rebuild | — |
| F8 | Keycloak realm `shopapp` adapted from YAS realm (theme removed, `${DOMAIN}` placeholders, new random dev client secrets, dev users `admin`/`admin` ADMIN and `customer`/`customer` CUSTOMER, password grant on `swagger-ui` for local testing) | Claude | YAS secrets are public; domain in one place | Dev-only values, never reuse |
| F9 | pgAdmin allows the `.local` email domain (`PGADMIN_CONFIG_ALLOW_SPECIAL_EMAIL_DOMAINS`) | Claude | `.local` is reserved; keep `admin@shopapp.local` | — |
| F10 | All published ports bind to `127.0.0.1` only | Claude (review fix) | Admin tools with dev credentials must not reach the LAN | — |
| F11 | nginx listens on `NGINX_HTTP_PORT` inside and out; Keycloak public URL from `KEYCLOAK_URL`; `Host` forwarded with port | Claude (review fix) | Ports other than 80 broke Keycloak redirects and the in-network issuer | Two `.env` keys must agree (smoke script checks) |
| F12 | Error handler keeps Spring's own 4xx statuses, maps malformed bodies/type mismatches to 400, Spring Security denials to 403, never sends SQL or exception internals to clients | Claude (review fix) | Catch-all had turned 4xx into 500 and leaked SQL | — |
| F13 | Auditing listener always records the current user on update (YAS kept the creator forever) | Claude (review fix) | YAS bug | — |
| F14 | Validation errors use `<field> <message>` without Java class names (YAS includes the bean class) | Claude | Don't leak internals | — |
| F15 | Smoke-test CRLF check counts CR bytes with `tr` (a `$'\r'` grep pattern is stripped by Git Bash) | Claude | False failures on Windows | — |
| F16 | Spring Cloud BOM deferred to slice 2 (its Boot 4.1 train was only a release candidate) | Claude | Stable pins; nothing in slice 1 used it | — |

Deferred minors from the slice-1 review: smoke script can be fooled by a foreign 404 when nginx is down;
`--restart` hides compose errors; missing Python is misreported as hosts problem; CRLF check passes outside
a git checkout; expected 4xx logged at ERROR; a service's own `messages.properties` shadows the library's
(decide in slice 2); Testcontainers not yet probed on JDK 27 (first used in slice 2a).

## Slice 2 — Core shopping flow

| # | Decision | By | Why | Cost if wrong |
|---|---|---|---|---|
| S2-1 | End state: browse products, log in, cart, place an order saved without tax/discount/payment ("pending payment"); images and addresses in slice 3 | User | Agreed scope | — |
| S2-2 | Split by browser step: 2a browse, 2b login + cart, 2c checkout | User | Clickable result each step | BFF/storefront touched three times |
| S2-3 | storefront-bff on Spring Cloud Gateway, Spring Cloud 2026.0.0-RC2 pinned until GA | User (proposed by Claude) | Stay on YAS's design | RC bugs |
| S2-4 | Missing later-slice services handled with fallbacks (e.g. placeholder image) | Claude | Keep slices independent | — |

### 2a — Browse products

| # | Decision | By | Why | Cost if wrong |
|---|---|---|---|---|
| 2a-1 | Product service gets the full YAS data model but only storefront read endpoints; admin endpoints in slice 3 | Claude | Scope | — |
| 2a-2 | Dev seed via Liquibase context `sample-data`, switched on by `.env` (`PRODUCT_LIQUIBASE_CONTEXTS`), tests never seed; YAS sampledata service in slice 5 | Claude | Plain `docker compose up` seeds | — |
| 2a-3 | No Redis/login in 2a; arrives in 2b | Claude | Scope | — |
| 2a-4 | Storefront: Next.js 16, React 19, pages router, YAS folder structure and Bootstrap look, catalog pages only | Claude | Current versions, YAS parity | — |
| 2a-5 | Home and list pages fetch in the browser; product detail is server-rendered with a real 404 (YAS parity) | Claude | YAS does exactly this | — |
| 2a-6 | Category and brand filters stay separate (YAS parity) | Claude | YAS endpoints | — |
| 2a-7 | Java services in Docker on `eclipse-temurin:27-jre`; storefront on `node:24-alpine`; none published on host ports | Claude | YAS style; loopback-only rule | — |
