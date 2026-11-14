# shop_app — Slice 2a: Browse products — Design

Date: 2026-11-14
Status: Draft, awaiting approval
Builds on: `2026-11-14-shop-app-foundation-design.md` (slice 1, merged). Same reference: YAS @
`179e813568c345bad3fce985088b5535e57481aa`; "mirror YAS" means that commit.

## Intent and why

Slice 2 delivers the core shopping flow. Agreed with the user, at the end of slice 2 a person can, in
the browser: browse products and see details, log in through Keycloak, add items to a cart, and place an
order that is saved **without tax, discount or payment**, with status "pending payment". Product images
and saved addresses come in slice 3 (with the back office).

Slice 2 is split by browser step (user picked this approach, "A"):

- **2a — Browse** (this document): product service, storefront-bff routing, storefront catalog pages.
- **2b — Log in + cart**: Keycloak login through storefront-bff (+ Redis sessions), customer service
  (profile only), cart service, cart page.
- **2c — Checkout**: order service, checkout page, "my orders"; order saved as pending payment.

Each step ends runnable end to end in the browser and gets its own design and plan.

## Goals of 2a

At the end of 2a, at `http://storefront.shopapp.local`:

- The **home page** shows featured products and the category list.
- The **product list** (`/products`) pages through products, filtered the YAS way: by category (and YAS's
  other `/storefront/products` parameters such as name and price range). Brand filtering is **not**
  combined with it: a brand link uses YAS's separate `/storefront/brand/{brandSlug}/products` endpoint.
- The **product detail** page (`/products/[slug]`) shows name, brand, price, description, specification
  (attributes grouped by attribute group), options (e.g. color/size) with variation selection, and related
  products.
- Missing images show a placeholder (media service comes in slice 3).
- Everything flows the YAS way: browser → nginx (`storefront.shopapp.local`) → **storefront-bff**
  (Spring Cloud Gateway) → `/api/**` → nginx (`api.shopapp.local/product/...`) → **product** service;
  other paths → **storefront** (Next.js).

## Non-goals (later steps / slices)

- Login, sessions, Redis, token relay — 2b.
- Customer, cart, order services — 2b/2c.
- Back-office (admin) product endpoints (create/update/delete products, categories, brands, attributes,
  options, templates) — slice 3, with the back-office UI. 2a builds the **full product data model** but
  only the **storefront read endpoints**.
- Product images / media service — slice 3. `thumbnailUrl`/`imageUrls` fields exist and are empty/null.
- Search page and Elasticsearch (`/storefront/products-es/...`) — slice 4.
- Rating/reviews on the detail page — slice 4.
- Inventory/stock display — slice 3.
- YAS sampledata service — slice 5 (2a uses a dev-only seed instead, see Decisions).
- Storefront pages beyond the catalog (about, contact, profile, address, cart, checkout, orders).

## Anti-goals

- Copying YAS's admin endpoints "while we're in there" — scope creep into slice 3.
- A storefront that only works when every later service exists: absent services must degrade (placeholder
  image, no rating), never a broken page.
- Hand-written forwarding in the BFF instead of Spring Cloud Gateway (diverges from YAS for a temporary
  reason).

## Constraints

- Everything from slice 1 still holds: Java 27, Spring Boot 4.1.x, `com.shopapp`, Checkstyle fails on
  warnings, Lombok + MapStruct available, `common-library` for errors/auditing/CORS, builds use Maven
  Central (user-approved; the work mirror is unreachable).
- **Spring Cloud for Boot 4.1 is only a release candidate (2026.0.0-RC2).** storefront-bff uses it,
  pinned; switch to the GA release when it ships. If RC2 fails on Boot 4.1.2 / Java 27, stop and ask.
- Node 24 is installed locally; Next.js latest is 16.x.
- Commits use author `nduyhai@gmail.com`; pushes go to `github.com/nduyhaing/typical-microservice-java`
  with the personal gh account.
- Local machine: Windows 11, Docker (Rancher Desktop), hosts file already maps the slice-1 hostnames; 2a
  adds `storefront.shopapp.local` (user must add it, as Administrator).

## Approaches considered (for splitting slice 2)

| | Approach | Trade-off |
|---|---|---|
| **A** | **Split by browser step: 2a browse, 2b login + cart, 2c checkout** | **Clickable result after each step; integration problems (BFF, Keycloak, CORS) found early; BFF and storefront touched in every step** |
| B | Split by layer: all four backend services first, then BFF + storefront | Focused backend work; nothing clickable until the end; UI needs may force reworking finished APIs |
| C | All of slice 2 at once | ~3× slice 1 in one plan; hard to review |

**Chosen: A** (user's pick).

## Decisions

Decided by the user:
- Slice 2 end state as described under Intent.
- Approach A.
- storefront-bff on Spring Cloud Gateway with the pinned RC (proposed, not objected to).

Calls made by Claude after the builder check (follow YAS; open to correction):
- Home and list pages load data in the browser; the detail page is server-rendered (YAS parity).
- Seed enabled through `.env` (`PRODUCT_LIQUIBASE_CONTEXTS`), not Compose profiles.
- Category and brand filters are separate (YAS parity), not combined.

Calls made by Claude (stated to the user, open to correction):
- **2a builds storefront read endpoints only**; admin endpoints wait for slice 3.
- **Dev seed data** via a separate Liquibase changelog in context `sample-data`. The compose `product`
  service sets `SPRING_LIQUIBASE_CONTEXTS=${PRODUCT_LIQUIBASE_CONTEXTS}`, and `.env` (already local-dev
  only) sets `PRODUCT_LIQUIBASE_CONTEXTS=sample-data`, so a plain `docker compose up -d --wait` seeds.
  Tests never set the context, so they run without the seed. No Compose profiles. ~5 categories, ~4 brands, ~20 products (some with
  options/variations, attributes and related products), enough to exercise every 2a page.
- **No Redis in 2a**; it arrives with sessions in 2b.
- **Storefront: Next.js 16, React 19, pages router**, mirroring YAS's `storefront/` folder structure
  (`pages/`, `modules/`, `common/`, `asset/`) and its Bootstrap styling, scoped to the catalog pages.
- **Containers**: each Java service has a YAS-style Dockerfile on `eclipse-temurin:27-jre`; the storefront
  a Node 24 multi-stage Dockerfile. All run in the slice-1 compose file.
- **Ports**: new services are not published on the host; only nginx is (loopback, from slice 1).

## Design

### New modules and files

```
product/                     Spring Boot service (Maven module, added to parent <modules>)
storefront-bff/              Spring Boot service, Spring Cloud Gateway (WebFlux)
storefront/                  Next.js app (not a Maven module)
docker-compose.yml           + product, storefront-bff, storefront
nginx/templates/...          + api.${DOMAIN} /product/ route, + storefront.${DOMAIN} → storefront-bff
.env                         + any new ports/URLs
scripts/verify-foundation.sh renamed/extended → covers 2a checks (or a new verify-slice2a.sh)
```

The parent POM gets the **Spring Cloud BOM** (2026.0.0-RC2) in `dependencyManagement`.

### product service

- Port 8080 in-container; context path `/product` behind nginx (`api.${DOMAIN}/product/...`), as YAS.
- Postgres DB `product` (exists since slice 1). **Liquibase** owns the schema: port YAS's
  `db/changelog/ddl/*.sql` changesets (renamed `com.yas`→`com.shopapp` where relevant), plus the 2a
  `sample-data` changelog.
- **Data model** — port YAS's entities: `Brand`, `Category`, `Product`, `ProductCategory`, `ProductImage`,
  `ProductOption`, `ProductOptionCombination`, `ProductOptionValue`, `ProductRelated`, and
  `attribute/ProductAttribute`, `ProductAttributeGroup`, `ProductAttributeTemplate`,
  `ProductAttributeValue`, `ProductTemplate`; enums `DimensionUnit`. All auditable entities extend
  `common-library`'s `AbstractAuditEntity`.
- **Storefront endpoints** (public, no auth), ported from YAS's controllers with the same paths and view
  models so the storefront matches YAS:
  - `GET /storefront/products/featured` (paged)
  - `GET /storefront/products` (paged, filter by product name, category slug; YAS signature)
  - `GET /storefront/category/{categorySlug}/products`
  - `GET /storefront/brand/{brandSlug}/products`
  - `GET /storefront/product/{slug}` (product detail incl. attributes grouped, options, variations)
  - `GET /storefront/product-variations/{id}`
  - `GET /storefront/products/related-products/{id}`
  - `GET /storefront/categories`, `GET /storefront/brands`
  - Skipped in 2a: `products-es`, `list-featured` (check YAS usage; port only if the 2a pages use it),
    `productions/{id}/slug`, `products/detail/{productId}` (used by cart/order — added in 2b/2c).
- **Media**: YAS's `MediaService` calls the media service for image URLs. 2a keeps the interface but the
  implementation returns empty URLs when media is unavailable/not configured (YAS-style fallback), so
  view models carry `null`/empty image fields.
- **Security**: resource server with JWT validation against `${KEYCLOAK_URL}/realms/shopapp` (issuer via
  the nginx alias from slice 1); `/storefront/**` and actuator health permitted; everything else
  authenticated (nothing else exists yet).
- Uses `common-library` (`ApiExceptionHandler`, `NotFoundException`, auditing, `CorsConfig`).
- OpenAPI via springdoc (`/product/v3/api-docs`, `/product/swagger-ui`).
- Healthcheck in compose on `/product/actuator/health`.

### storefront-bff

- Spring Cloud Gateway (WebFlux), port 8087 in-container, as YAS.
- Routes (YAS dev profile shape, adapted):
  - `/api/**` → `http://api.${DOMAIN}` with `StripPrefix=1` (so `/api/product/storefront/...` →
    `api.${DOMAIN}/product/storefront/...`).
  - `/**` → `http://storefront:3000` (Next.js).
- No security, no session, no TokenRelay in 2a (added in 2b).
- nginx: `storefront.${DOMAIN}` → `storefront-bff:8087`.

### storefront (Next.js)

- Pages: `/` (home: featured products, categories), `/products` (list with category/brand filters,
  pagination), `/products/[slug]` (detail: info, specification, options → variation, related products),
  `404`.
- **Data loading mirrors YAS**: the home page and `/products` fetch in the browser (`useEffect`) through
  the BFF same-origin (`/api/product/storefront/...`); the **detail page renders on the server**
  (`getServerSideProps`, returning `notFound: true` for an unknown slug → real HTTP 404), as YAS's
  `pages/products/[slug].tsx` does. Server-side calls use their own base URL from env
  (`API_BASE_PATH_SERVER=http://storefront-bff:8087/api`), so the storefront container talks to the BFF on
  the compose network (→ nginx alias `api.${DOMAIN}` → product). The storefront therefore starts after the
  BFF (`depends_on`).
- Placeholder image for products without images.
- Build: `npm run build` must pass; lint (`next lint` or eslint) clean; type-check clean.
- Tests: component/unit tests for the data-mapping and the option→variation selection logic (Jest or
  Vitest — builder's call), not a full UI test suite.

### Definition of done

1. `mvn verify` green (product with unit + Testcontainers/Postgres integration tests for repositories and
   storefront endpoints; storefront-bff with a route test).
2. `npm run build` and the storefront tests green.
3. `docker compose up -d --wait` → all containers healthy, including product, storefront-bff, storefront.
4. Smoke script (extended) passes all slice-1 checks plus:
   - `GET http://storefront.${DOMAIN}/` → 200 HTML (the page shell; home data loads in the browser);
   - `GET http://storefront.${DOMAIN}/api/product/storefront/products/featured` → 200 JSON containing a
     seeded featured product;
   - `GET http://storefront.${DOMAIN}/api/product/storefront/categories` → 200 JSON with the seeded
     categories (proves BFF → nginx → product);
   - `GET http://storefront.${DOMAIN}/products/<seeded-slug>` → 200 with product name and price;
   - unknown product slug → storefront returns HTTP 404 (SSR `notFound`), API returns 404 `ErrorVm`.
5. Manual browser check by the user: home → category → product detail → pick an option → variation price
   updates → related product link works.
6. README updated (new hostname, services, how to run the storefront locally with `npm run dev`).

## Left to the builder

- Exact versions (latest stable, pinned) for springdoc, Testcontainers usage, Next.js 16.x, React 19.x,
  react-bootstrap, test runner.
- Which YAS changesets map cleanly vs need consolidating (keep the resulting schema identical to YAS's).
- Seed data contents (realistic but small; no images).
- Whether the smoke script is extended or split per slice.
- Storefront styling details beyond "YAS look, Bootstrap".
