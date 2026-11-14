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
| P11 | Push `master` to GitHub after each commit, automatically | User | Owner's request | Unreviewed work becomes public immediately |
| P12 | Latest Lombok (1.18.48), MapStruct (1.7.0.Beta2 — a beta; newest final is 1.6.3) and JaCoCo (0.8.16), versions pinned in the parent POM | User | Owner's request | MapStruct beta bugs; fall back to 1.6.3 if one bites |

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
| S2-3 | storefront-bff on Spring Cloud Gateway. Spring Cloud has **no train for Boot 4.1** (2025.1 GA → Boot 4.0; 2026.0 RC → Boot 4.2), so: Spring Cloud **2025.1.4 (GA)** on Boot 4.1.2 with the Spring Cloud compatibility verifier disabled in storefront-bff; gateway tests prove the routes work. Move to Boot 4.2 + Spring Cloud 2026.0 when both are GA. (First choice 2026.0.0-RC2 refused to start on Boot 4.1.) | User (gateway) + Claude (versions) | Stay on YAS's design with final releases | A Boot 4.1 incompatibility in a Spring Cloud path not covered by tests |
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
| 2a-8 | Storefront category and brand product lists hide unpublished products and variations (YAS shows unpublished products in category lists and variations in both) | Claude | Shoppers must only see sellable, individually listed products | — |
| 2a-9 | `common-library` messages renamed to `messages/common-messages.properties`; lookups try the service's `messages/messages.properties` first, then the library's (closes slice-1 deferred minor) | Claude | A service bundle used to hide the library's codes | — |
| 2a-10 | Storefront paging parameters validated (`0 <= pageNo <= 10,000,000`, `1 <= pageSize <= 100`) → 400 instead of 500 (huge page numbers overflowed the SQL offset; huge page sizes would fan out to media calls) | Claude (+ review fix) | Bad input must not look like a server failure | Clients needing > 100 per page must page |
| 2a-11 | Media lookups in 2a: empty URL when the media id is null, the media URL is not configured, or the media service fails; no retry/circuit breaker until slice 3 | Claude | Media service doesn't exist yet | — |
| 2a-12 | Product Liquibase context defaults to `default`, never empty: an empty context list makes Liquibase run every changeset, including the sample data | Claude | Found by `LiquibaseSchemaIT` once the seed existed | — |
| 2a-13 | Healthchecks for Java service containers run `bash -c` with `/dev/tcp` (the JRE image has no curl/wget and its `sh` is dash) | Claude | Container stayed "unhealthy" while the app was up | — |
| 2a-14 | Storefront toolchain: TypeScript 6.0.3 (TypeScript 7 is the new native compiler; Next.js type-checking relies on the JavaScript compiler API), ESLint 9.39.5 (Next's import/jsx-a11y plugins don't declare ESLint 10 support), Vitest for tests; all versions pinned exactly | Claude | Newest versions that work together | Upgrade when the plugins catch up |
| 2a-15 | Variations (not listed on their own) have no detail page, and id-based storefront endpoints (variations, option values, related) 404 for unpublished products (YAS serves both) | Claude (review fix) | Hidden products must not be reachable by URL or id | — |
| 2a-16 | "All products" uses `SELECT DISTINCT` so a product in two categories is listed once with correct totals (YAS lists it once per category) | Claude (review fix) | YAS bug | — |
| 2a-17 | Related products filtered in the query (published and listed on their own), so pages and totals are right and variations never show (YAS filters published in memory after paging) | Claude (review fix) | YAS bug | — |
| 2a-18 | Storefront: any 4xx for a product slug renders the 404 page; the API client tolerates non-JSON error bodies (proxy/Tomcat HTML) | Claude (review fix) | Odd slugs (encoded `/` or `%`) crashed the page with a 500 | — |

Deferred from the 2a review (minor): `productName` filter treats `%`/`_` as wildcards; MediaService needs connect/read
timeouts when media is wired in (slice 3); storefront container runs as root; free-text `specification` not shown on
the product page; a failing variations/option-values call fails the whole product page instead of hiding options.

