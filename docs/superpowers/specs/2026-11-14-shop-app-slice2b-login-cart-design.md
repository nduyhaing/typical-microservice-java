# shop_app — Slice 2b: Log in + cart — Design

Date: 2026-11-14
Status: Written for automatic implementation (owner asked to implement automatically and record decisions in
`decisions.md`; no approval round).
Builds on: slice 1 (foundation) and slice 2a (browse). YAS reference @ `179e813568c345bad3fce985088b5535e57481aa`.

## Intent

Second step of slice 2 (agreed end state: browse → log in → cart → order saved as "pending payment").
At the end of 2b, at `http://storefront.shopapp.local`, a person can:

- **log in** with Keycloak (the `customer`/`customer` dev user, or self-registration) and **log out**;
- see their name in the header and a **profile** page (name, email from Keycloak);
- **add a product (or the selected variation) to the cart** from the product page;
- open the **cart page**: see items with name, price, quantity and line total, change quantities, remove items,
  see the cart total. The cart persists across sessions (stored per Keycloak user id).

Anonymous visitors still browse freely; "Add to cart" sends them to the login page and back.

## Non-goals

- Guest carts / guest checkout (YAS's `/storefront/customer/guest-user`) — not needed for the agreed flow.
- Addresses (`/storefront/user-address/**`) — slice 3 with the location service.
- Checkout and orders — 2c.
- Back-office customer management (`/backoffice/customers/**`) — slice 3.
- Stock checks (inventory) — slice 3.

## Anti-goals

- Tokens in the browser. The storefront never sees an access token: the BFF holds it in the server session and
  relays it (YAS's BFF pattern). Breaking this is a failure even if it works.
- A cart that silently accepts unknown or unpublished products, or quantities ≤ 0.

## Design

### Infrastructure

- **Redis** (`redis:7.4.x` pinned, as YAS) in compose, not published; healthcheck `redis-cli ping`.
- Keycloak realm already has the confidential `storefront-bff` client with redirect `http://storefront.${DOMAIN}/*`
  and the `customer-management` service-account client (slice 1). Verify both still work; adjust redirect /
  post-logout URIs if needed (port-aware via `${DOMAIN}`; the realm file is only read on first start, so a change
  means a documented `docker compose down -v` or a kcadm update script).

### storefront-bff (extend 2a)

Port YAS `storefront-bff` security:
- `spring-boot-starter-security-oauth2-client`, `spring-session-data-redis`, `spring-boot-starter-data-redis`.
- OAuth2 login with registration `keycloak` → client `storefront-bff`, secret from env (`.env`), scopes
  `openid, profile, email, roles`, issuer `${KEYCLOAK_URL}/realms/shopapp` (in-network via the nginx alias,
  same issuer as the browser sees).
- `SecurityConfig` as YAS: `anyExchange().permitAll()` except `/profile/**` authenticated; oauth2Login;
  CSRF disabled (YAS; same-site cookie session); OIDC logout redirecting to `{baseUrl}`; Keycloak realm roles →
  `ROLE_*` authorities.
- Gateway default filter `SaveSession`; `api` route adds `TokenRelay=` (forwards the user's access token when
  logged in, nothing when anonymous).
- `GET /authentication` → `AuthenticationInfoVm(boolean isAuthenticated, AuthenticatedUserVm authenticatedUser)`
  with `preferred_username` (YAS).
- Sessions in Redis (`spring.session.store-type` / Boot 4 equivalent), so BFF restarts keep users logged in.
- Login entry point: `/oauth2/authorization/keycloak` (Spring default). After login → back to the storefront.

### cart service (new module `cart`)

Port YAS `cart`:
- Postgres DB `cart` (exists), Liquibase ddl ported (final table `cart_item(customer_id, product_id, quantity,
  audit columns)`, composite key), entity `CartItem` + `CartItemId`.
- Endpoints (YAS paths, view models `CartItemPostVm`, `CartItemPutVm`, `CartItemGetVm`, `CartItemDeleteVm`):
  - `POST /storefront/cart/items` — add (merge quantity if present)
  - `PUT /storefront/cart/items/{productId}` — set quantity
  - `GET /storefront/cart/items` — list current user's items
  - `POST /storefront/cart/items/remove` — remove several (YAS)
  - `DELETE /storefront/cart/items/{productId}` — remove one
- All require an authenticated user (customer id = JWT `sub` via `AuthenticationUtils.extractUserId()`);
  anonymous → 401 (resource-server) / 403 per YAS security config.
- Validation: quantity ≥ 1 (400), product must exist and be published (calls product service; unknown → 404).
- Product lookup: YAS `ProductService` calls product `GET /storefront/products/list-featured?productId=...`.
  Product service gains that endpoint (port from YAS; published products only).
- Cart only stores ids and quantities (YAS); the storefront joins product names/prices from the product API.

### customer service (new module `customer`)

Port YAS `customer`, profile only:
- `GET /storefront/customer/profile` — current user's profile from Keycloak admin API
  (`keycloak-admin-client`, client-credentials with `customer-management`), view model `CustomerVm`
  (id, username, email, firstName, lastName).
- No DB use in 2b beyond what Boot requires (YAS customer's DB holds addresses → slice 3). If the module needs a
  datasource for Liquibase, port YAS's customer ddl now (table unused until slice 3).
- Authenticated only.

### nginx

`api.${DOMAIN}`: add `/cart/` → cart, `/customer/` → customer (YAS routes).

### storefront (extend 2a)

- `useAuth` / header: calls `/authentication`; shows "Log in" (link to `/oauth2/authorization/keycloak`) or the
  username + "Profile" + "Log out" (POST/GET `/logout` per Spring Security WebFlux config).
- Product page: quantity input + "Add to cart" (adds the selected variation id when the product has options and a
  variation is selected; disabled until one is). Not logged in → redirect to login. Success → toast/alert.
- `/cart`: list items (join product details via `list-featured`), quantity +/- (PUT), remove (DELETE), totals,
  empty state. Not logged in → login prompt.
- `/profile`: name, email, username.
- Header cart link with item count.

### Definition of done

1. `mvn verify` green: cart (unit + Testcontainers ITs incl. 401 anonymous, add/merge, quantity validation,
   unknown product 404, list, update, remove), customer (profile with a mocked Keycloak client), product
   (`list-featured`), storefront-bff (route + `/authentication` + token relay tests).
2. Storefront tests green (header auth states, add-to-cart flows incl. variation requirement, cart page totals
   and empty state), lint, typecheck, build.
3. `docker compose up -d --build --wait` → all healthy incl. redis, cart, customer.
4. Smoke script adds: `/authentication` anonymous → `isAuthenticated:false`; full login flow scripted with curl
   (follow Keycloak login form with `customer`/`customer`, keep cookies) → `/authentication` shows `customer`;
   add `galaxy-s24-violet` to cart → cart lists it with quantity; anonymous `GET /api/cart/storefront/cart/items`
   → 401/403; logout → anonymous again.
5. README: login, dev user, cart.

## Decisions (by Claude, recorded in decisions.md as 2b-*)

- No guest cart; cart requires login (YAS cart is customer-keyed; guest flow not in agreed scope).
- Customer service: profile only in 2b.
- BFF keeps tokens server-side in Redis sessions; storefront never handles tokens (YAS pattern).
- CSRF disabled in the BFF as in YAS (session cookie `SameSite=Lax` default) — revisit if a cross-site form is ever
  added.
- Variation add-to-cart uses the variation's product id (YAS cart stores product ids; variations are products).

## Left to the builder

- Exact Redis image tag, Spring Session config keys for Boot 4.
- How the smoke script drives the Keycloak login form (curl with cookie jar + parsing the form action).
- Toast library vs Bootstrap alert.
