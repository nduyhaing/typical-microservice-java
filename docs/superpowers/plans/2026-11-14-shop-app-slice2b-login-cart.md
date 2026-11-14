# shop_app Slice 2b (Log in + cart) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** At `http://storefront.shopapp.local` a person logs in with Keycloak, sees their profile, adds products (or a selected variation) to a cart, edits quantities, removes items and logs out; the cart persists per user.

**Architecture:** storefront-bff becomes the OAuth2 client (Keycloak login, sessions in Redis, `TokenRelay` to the API). New services `cart` (Postgres, YAS port) and `customer` (profile from the Keycloak admin API). The storefront never sees tokens.

**Tech Stack:** as slice 2a, plus Redis 7.4, Spring Security OAuth2 client (WebFlux), Spring Session Data Redis, keycloak-admin-client.

**Spec:** `docs/superpowers/specs/2026-11-14-shop-app-slice2b-login-cart-design.md`

## Global Constraints

- Everything in the 2a plan's Global Constraints holds (Java 27, Boot 4.1.2, Spring Cloud 2025.1.4 with verifier off in the BFF, `com.shopapp`, Checkstyle, pinned versions, LF endings, Maven Central settings file, commit + push to `master` after each task, decisions in `decisions.md`).
- YAS reference @ `179e813568c345bad3fce985088b5535e57481aa`; port names, paths, view models.
- Tokens never reach the browser.
- Java service containers: `eclipse-temurin:27-jre`, healthcheck `bash -c` + `/dev/tcp` (decisions 2a-13); not published on host ports.

## Review Focus

1. **Anonymous cart calls** (no session / no token): cart API answers 401/403 with `ErrorVm`, never 500; storefront cart page shows a login prompt — Task 3 `anonymous_returns401`, Task 8 `CartPage shows login prompt when anonymous`.
2. **Bad quantities** (0, negative, missing) on add/update: 400, item unchanged — Task 3 `add_zeroQuantity_returns400`, `update_negativeQuantity_returns400`.
3. **Unknown or unpublished product id** added to the cart: 404, nothing stored — Task 3 `add_unknownProduct_returns404`.
4. **Product service down while adding**: cart answers 5xx/503 quickly, not a hang, nothing stored — Task 3 `add_productServiceDown_returns5xxWithoutSaving`.
5. **Session expiry / logout mid-use**: after logout, `/authentication` is anonymous and cart calls are rejected; storefront header switches to "Log in" — Task 9 smoke check.

---

### Task 1: product `list-featured` endpoint (used by cart and the cart page)

**Files:** Modify `product/.../controller/ProductController.java`, `ProductService.java`; Test `ProductListControllerIT` (extend).
**Interfaces:** `GET /storefront/products/list-featured?productId=1&productId=2` → `List<ProductThumbnailGetVm>` (YAS name/shape), published products only, unknown ids ignored, order as requested.
- [ ] Test `listFeatured_returnsPublishedRequestedProducts` (A, B published, C unpublished; request A,C,999 → only A), RED.
- [ ] Implement via `productRepository.findAllByIdIn` + published filter. GREEN, `mvn -pl product verify`, commit, push.

### Task 2: cart module skeleton, schema, entity, repository

**Files:** Create `cart/pom.xml` (as product: webmvc, resource server, validation, data-jpa, liquibase, actuator, postgresql, springdoc, common-library, lombok, mapstruct; tests incl. Testcontainers, webmvc-test, security-test), `CartApplication`, `application.properties` (port 8080, context `/cart`, `shopapp.services.product`, issuer, liquibase contexts default `default`), port YAS `db/changelog/**` (ddl 0001–0004 verbatim; YAS `data/changelog-0001.sql` only if non-empty), `model/CartItem`, `model/CartItemId`, `repository/CartItemRepository`, `config/DatabaseAutoConfig`, `messages/messages.properties`, `utils/Constants`. Add `<module>cart</module>`.
- [ ] `CartSchemaIT`: table `cart_item` with PK `(customer_id, product_id)` exists; `CartItemRepositoryIT`: save/find by customer, `findByCustomerIdOrderByCreatedOnDesc`, `deleteByCustomerIdAndProductId`. RED → port → GREEN, commit, push.

### Task 3: cart API

**Files:** `controller/CartItemController`, `service/CartItemService`, `service/ProductService` (RestClient to `shopapp.services.product` `/storefront/products/list-featured`), `mapper/CartItemMapper` (MapStruct 1.7 beta — it's the first real MapStruct use; fall back to hand-written if it fails, record it), `viewmodel/*` (YAS records), `config/SecurityConfig` (YAS: `/storefront/**` permitAll at filter level, controller extracts the user → anonymous gets `AccessDeniedException` 403; ruling: also 401 is acceptable — tests assert 4xx `ErrorVm`, not 500).
- [ ] `CartItemControllerIT` (Testcontainers + MockMvc with `jwt()` post-processor; product service stubbed with a JDK `HttpServer` bound via `shopapp.services.product`):
  `add_createsItem`, `add_existing_mergesQuantity`, `add_zeroQuantity_returns400`, `add_unknownProduct_returns404`, `add_productServiceDown_returns5xxWithoutSaving`, `list_returnsOnlyCurrentUsersItems`, `update_setsQuantity`, `update_negativeQuantity_returns400`, `delete_removesItem`, `removeMany_adjustsOrDeletes`, `anonymous_returns401or403`.
- [ ] RED → port → GREEN, commit, push.

### Task 4: cart container, compose, nginx

- [ ] Dockerfile, `.dockerignore`, compose `cart` (env DB, `KEYCLOAK_URL`, `SHOPAPP_SERVICES_PRODUCT=http://api.${DOMAIN}:${NGINX_HTTP_PORT}/product`), nginx `api.${DOMAIN}` `/cart/` → `cart:8080`, nginx depends on cart healthy.
- [ ] Verify anonymous `GET http://api.shopapp.local/cart/storefront/cart/items` → 401/403 JSON; with a password-grant token for `customer` (swagger-ui client) add + list works. Commit, push.

### Task 5: customer service (profile)

**Files:** `customer` module: port YAS `CustomerController.getCustomerProfile` only, `CustomerService.getCustomerProfile`, `KeycloakClientConfig`, `KeycloakPropsConfig` (`keycloak.auth-server-url`, `realm`, `resource`, `credentials.secret`), `CustomerVm`, `SecurityConfig`. Datasource: port YAS customer ddl (user_address table, unused until slice 3) so Boot/Liquibase config matches YAS.
- [ ] `CustomerControllerIT` with a mocked `Keycloak` bean: `profile_returnsCurrentUser` (jwt sub `u-1` → mocked user representation → `username`, `email`, `firstName`, `lastName`), `profile_anonymous_returns401or403`, `profile_keycloakForbidden_returns403` (YAS maps to AccessDenied).
- [ ] Container, compose (`KEYCLOAK_*` env incl. `customer-management` secret from the realm file), nginx `/customer/`. Verify with a real token: profile returns `customer`. Check the realm's `service-account-customer-management` has `realm-management: view-users`. Commit, push.

### Task 6: storefront-bff login, Redis sessions, token relay

**Files:** BFF pom (+ `spring-boot-starter-security-oauth2-client`, `spring-boot-starter-data-redis-reactive` or as Boot 4 names it, `spring-session-data-redis`), `config/SecurityConfig` (YAS port), `controller/AuthenticationController`, `viewmodel/AuthenticationInfoVm`, `AuthenticatedUserVm`, `application.yaml` (registration `keycloak`, provider issuer `${KEYCLOAK_URL}/realms/shopapp`, client secret `${STOREFRONT_BFF_CLIENT_SECRET}`, `SaveSession` default filter, `TokenRelay=` on `api` route, Redis host). Compose: `redis` service + BFF env + depends_on redis/identity. `.env`: `STOREFRONT_BFF_CLIENT_SECRET` (value from the realm file).
- [ ] Tests: `AuthenticationControllerTest` (WebTestClient + `mockOidcLogin()` → `isAuthenticated:true`, username; anonymous → false), `GatewayRoutesTest` extended: `apiRoute_loggedIn_relaysBearerToken` (`mockOAuth2Login()` with an authorized client → backend sees `Authorization: Bearer …`), `apiRoute_anonymous_sendsNoAuthorization`, existing route tests still green (Redis: tests use an in-memory session or a Testcontainers Redis — builder's call, ledger it).
- [ ] Verify in the stack: `/oauth2/authorization/keycloak` redirects to `http://identity.shopapp.local/realms/shopapp/protocol/openid-connect/auth?...client_id=storefront-bff...`. Commit, push.

### Task 7: storefront auth header and profile page

**Files:** `common/hooks/useAuth.ts` (GET `/authentication` same-origin, not under `/api`), `Layout` header (Log in link → `/oauth2/authorization/keycloak`; logged in: username, Profile, Cart, Log out → `POST /logout` form), `pages/profile.tsx` (GET `/api/customer/storefront/customer/profile`; 401/403 → login prompt), `modules/customer/...`.
- [ ] Tests: `Header shows Log in when anonymous`, `Header shows username and Log out when logged in`, `Profile page renders profile`, `Profile page shows login prompt on 401`. RED → GREEN, lint/typecheck/build, commit, push.

### Task 8: add to cart and cart page

**Files:** `modules/cart/{models,services,components}`, `ProductDetails` (quantity input + Add to cart: requires a selected variation when `hasOptions`; anonymous → `window.location = /oauth2/authorization/keycloak`), `pages/cart.tsx` (items joined with `list-featured`, +/−, remove, line totals, total, empty state, login prompt when anonymous), header cart count.
- [ ] Tests: `Add to cart disabled until a variation is selected`, `Add to cart posts the variation id and quantity`, `Add to cart when anonymous redirects to login`, `CartPage shows items with totals`, `CartPage shows empty state`, `CartPage shows login prompt when anonymous`, `quantity minus at 1 removes the item`. RED → GREEN, lint/typecheck/build, Docker rebuild, commit, push.

### Task 9: smoke checks, README, decisions, full definition of done

- [ ] Smoke script: `/authentication` anonymous → `"isAuthenticated":false`; scripted login (curl cookie jar: GET `/oauth2/authorization/keycloak` → follow to Keycloak form → POST `customer`/`customer` to the form action → follow back) → `/authentication` shows `customer`; add `galaxy-s24-violet` (lookup id via product API) → cart lists it; anonymous cart call → 401/403; logout → anonymous.
- [ ] `mvn verify`, storefront tests/lint/build, `docker compose up -d --build --wait`, smoke `--restart` all PASS. README (login, dev user, cart, Redis), decisions 2b-*. Commit, push.
