# shop_app — Slice 3a: Back office foundation + catalog admin — Design

Date: 2026-11-14 · Status: written for automatic implementation (decisions.md P9) · YAS @ `179e8135`.

## Intent

Slice 3 = back office. Split into: **3a** admin login + admin web app + catalog admin (this document), **3b** media
(images), **3c** inventory, tax, location (+ saved addresses at checkout). At the end of 3a an ADMIN user opens
`http://backoffice.shopapp.local`, logs in through Keycloak (non-admins are refused), and manages **brands,
categories and products** (list, create, edit, delete, publish/feature flags, price, brand, categories; products
with options get variations). Changes show up in the storefront immediately.

## Non-goals (later steps)

Product attributes/attribute groups/templates admin, product images (3b), stock (3c), tax classes (3c), orders,
customers, promotions, reviews, webhooks, payment providers admin (slices 3c–5), CSV export.

## Design

### product service — admin endpoints (`/backoffice/**`, role ADMIN — already enforced by product SecurityConfig)

Port from YAS (same paths and view models), adapting to the 2a code (keep its fixes; admin code goes in separate
classes `BrandAdminService`/`CategoryAdminService`/`ProductAdminService` or the existing services, builder's call):
- Brands: `GET /backoffice/brands` (+ `/paging`), `GET /backoffice/brands/{id}`, `POST`, `PUT /{id}`,
  `DELETE /{id}` (refuse when products use it — YAS `MAKE_SURE_BRAND_DONT_CONTAINS_ANY_PRODUCT`). Duplicate
  name/slug → 400 (YAS `NAME_ALREADY_EXITED` / slug check).
- Categories: `GET /backoffice/categories`, `GET /{id}`, `POST`, `PUT /{id}` (parent cannot be itself), `DELETE /{id}`
  (refuse with children or products).
- Products: `GET /backoffice/products?pageNo&pageSize&product-name&brand-name` (`ProductListGetVm`),
  `GET /backoffice/products/{id}` (`ProductDetailVm`), `POST /backoffice/products` (`ProductPostVm` incl.
  `variations` + `productOptionValues` — YAS create flow: product, categories, option values, variation products,
  option combinations), `PUT /backoffice/products/{id}` (`ProductPutVm`, YAS update incl. variations),
  `DELETE /backoffice/products/{id}` (YAS soft delete: unpublish), `GET /backoffice/product-variations/{id}`,
  `GET /backoffice/product-options` (list options for the form) and `POST /backoffice/product-options` (create
  an option such as "Color").
- Validation as YAS: unique slug/sku/gtin across products, price ≥ 0, length ≥ width (YAS rule), bounded paging
  (2a-10). Media ids accepted but unused until 3b.

### backoffice-bff (new module, port of YAS `backoffice-bff`)

Same shape as storefront-bff (reuse its classes by copying: explicit Keycloak registration 2b-6, Redis sessions,
TokenRelay, `ApiAuthorizationFailureFilter` 2b-13, `CrossSiteRequestFilter` 2b-7): client `backoffice-bff`
(secret from the realm via `.env` `BACKOFFICE_BFF_CLIENT_SECRET`), **every path requires role ADMIN** (YAS) except
health; a logged-in non-admin gets 403. Routes: `/api/**` → `api.${DOMAIN}` (TokenRelay, StripPrefix=1), `/**` →
`backoffice:3000`. `GET /authentication` like storefront-bff. Realm: ensure `backoffice-bff` redirect URIs
include `http://backoffice.${DOMAIN}/*` and post-logout `+`; ensure ADMIN realm role reaches the BFF (userinfo /
id token `realm_access`); apply to the running Keycloak with an idempotent kcadm script like 2b-4 if the realm file
changes.

### backoffice (new Next.js app `backoffice/`, YAS structure, same stack/tooling as `storefront/`)

Pages: `/` dashboard (links), `/catalog/brands` (list/create/edit/delete), `/catalog/categories` (same, parent
select), `/catalog/products` (list with search + paging, create/edit form: name, slug, sku, gtin, price,
short description, description, brand, categories, flags; options + variations editor for products with options),
logout. Calls `/api/product/backoffice/...` same-origin. 401/403 → message (BFF already forces login).

### compose / nginx

Services `backoffice-bff` (8088 internal) and `backoffice`; nginx `backoffice.${DOMAIN}` → backoffice-bff (same
headers rules incl. 2b-12). Hosts entry already present.

### Definition of done

1. `mvn verify` green incl. product admin ITs (admin role required: customer token → 403; CRUD; duplicate slug →
   400; delete refusals; create product with 2 variations → storefront detail shows options; update price →
   storefront shows new price; delete → hidden from storefront) and backoffice-bff tests (non-admin 403, admin ok,
   routes, token relay).
2. Backoffice app tests + lint + typecheck + build.
3. Compose healthy; smoke adds: admin login through backoffice-bff (admin/admin) → create brand → list shows it →
   delete; customer login to backoffice → 403.
4. README + decisions 3a-*.
