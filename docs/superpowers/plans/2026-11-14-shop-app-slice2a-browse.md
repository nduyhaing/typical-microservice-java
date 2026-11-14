# shop_app Slice 2a (Browse products) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A person opens `http://storefront.shopapp.local`, sees featured products and categories, pages through products by category, opens a product, picks options to see the matching variation's price, and follows related products. Data comes from a new **product** service through **storefront-bff**.

**Architecture:** New Maven modules `product` (Spring MVC + JPA + Liquibase, ported from YAS, storefront read endpoints only) and `storefront-bff` (Spring Cloud Gateway). A new Next.js app `storefront/` (pages router, YAS structure). All three run in the slice-1 compose stack behind nginx: browser → nginx → storefront-bff → `/api/**` → nginx `api.${DOMAIN}/product/` → product; everything else → storefront.

**Tech Stack:** Java 27, Spring Boot 4.1.2, Spring Cloud 2026.0.0-RC2 (gateway), Spring Data JPA, Liquibase, PostgreSQL 16, Testcontainers, springdoc-openapi, Next.js 16 / React 19 / react-bootstrap, Node 24.

**Spec:** `docs/superpowers/specs/2026-11-14-shop-app-slice2a-browse-design.md` (builds on `docs/superpowers/specs/2026-11-14-shop-app-foundation-design.md`).

## Global Constraints

- Everything in the slice-1 plan's Global Constraints still holds (Java 27, `com.shopapp`, Checkstyle fails on warnings, pinned versions, LF endings for container files, `.env` holds the domain).
- YAS reference: `nashtech-garage/yas` @ `179e813568c345bad3fce985088b5535e57481aa`. "Port X" = read X at that commit (`gh api "repos/nashtech-garage/yas/contents/<path>?ref=179e8135…"`), rewrite into `com.shopapp.*`, keep names, paths, view-model shapes and JSON field names identical unless a ruling says otherwise.
- **Storefront read endpoints only.** No `/backoffice/**` handlers in product (a YAS mapping that lists both paths keeps only the `/storefront/...` one).
- Spring Cloud BOM `2026.0.0-RC2`, pinned. If it fails on Boot 4.1.2 / Java 27: stop and ask the user.
- Maven builds: `JAVA_HOME=~/.jdks/openjdk-27 mvn -B -s <scratchpad>/maven-central-settings.xml …` (user-approved; work mirror unreachable). Docker needs Testcontainers to reach the Rancher Desktop daemon.
- Git: author `nduyhai@gmail.com` (repo-local config already set); push only with the personal gh account; commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Product images: none in 2a. Image fields are `null`/empty; the storefront shows a placeholder.
- New services are not published on host ports; only nginx is (loopback).

## Review Focus

1. **Product with no options, no variations, no attributes, no related products, no brand, no category:** detail API and page render cleanly with those sections hidden — Task 5 test `getProductDetail_bareProduct_returnsEmptyCollections`, Task 9 test `ProductDetails renders a bare product`.
2. **Unpublished or not-individually-visible products and variations' parents** must never appear on storefront lists or detail (YAS filters on `isPublished` / `isVisibleIndividually`) — Task 4 test `storefrontLists_excludeUnpublishedAndInvisible`, Task 5 test `getProductDetail_unpublished_returns404`.
3. **Paging edge input** (`pageNo` past the end, `pageSize=0`, negative values): empty page or 400 `ErrorVm`, never 500 — Task 4 test `featured_pagePastEnd_returnsEmptyList` and `products_negativePageSize_returns400`.
4. **Product service down:** the BFF returns a 5xx quickly (no hang) and the storefront shows an error message instead of crashing; SSR detail returns 502/500 page not an unhandled exception — Task 7 test `apiRoute_backendDown_returns5xxQuickly`, Task 9 test `useFetch error state shows message`.
5. **Unknown or odd slugs** (`/products/does-not-exist`, URL-encoded/uppercase): API 404 `ErrorVm`, SSR real 404 — Task 5 test `getProductDetail_unknownSlug_returns404`, Task 10 smoke check.

---

## File Structure

```
pom.xml                                      + Spring Cloud BOM, + modules product, storefront-bff
product/pom.xml
product/Dockerfile
product/src/main/java/com/shopapp/product/
  ProductApplication.java
  config/SecurityConfig.java  DatabaseAutoConfig.java (auditing: AuditorAware from AuthenticationUtils)
  model/…            (entities ported from YAS model/ and model/attribute/, enumeration/DimensionUnit)
  repository/…       (ported repositories, only methods the storefront endpoints use)
  service/ProductService.java  CategoryService.java  BrandService.java  MediaService.java
  controller/ProductController.java  CategoryController.java  BrandController.java  (storefront GETs only)
  viewmodel/…        (ported records used by those endpoints)
product/src/main/resources/application.properties
product/src/main/resources/db/changelog/db.changelog-master.yaml   (includeAll ddl/ + sample-data file)
product/src/main/resources/db/changelog/ddl/changelog-*.sql         (ported verbatim order)
product/src/main/resources/db/changelog/data/sample-data.sql        (context: sample-data)
product/src/test/java/com/shopapp/product/…  (unit tests + Testcontainers ITs)
storefront-bff/pom.xml  storefront-bff/Dockerfile
storefront-bff/src/main/java/com/shopapp/storefrontbff/StorefrontBffApplication.java
storefront-bff/src/main/resources/application.yaml
storefront-bff/src/test/java/com/shopapp/storefrontbff/GatewayRoutesTest.java
storefront/  package.json  next.config.ts  tsconfig.json  Dockerfile  .dockerignore  jest/vitest config
storefront/pages/_app.tsx  index.tsx  404.tsx  products/index.tsx  products/[slug].tsx
storefront/modules/catalog/{models,services,components}/…   (ported from YAS storefront/modules/catalog)
storefront/common/{components,services}/…                     (layout, ApiClientService, placeholder image)
docker-compose.yml  nginx/templates/default.conf.template  .env
scripts/verify-foundation.sh                  extended with 2a checks
README.md
```

---

### Task 1: product module skeleton, Spring Cloud BOM, Liquibase schema, Testcontainers on Java 27

**Files:**
- Modify: `pom.xml` (add `<spring-cloud.version>2026.0.0-RC2</spring-cloud.version>`, import `spring-cloud-dependencies` BOM, add `<module>product</module>`)
- Create: `product/pom.xml`, `product/src/main/java/com/shopapp/product/ProductApplication.java`, `product/src/main/resources/application.properties`, `product/src/main/resources/db/changelog/db.changelog-master.yaml`, `product/src/main/resources/db/changelog/ddl/*.sql`
- Test: `product/src/test/java/com/shopapp/product/LiquibaseSchemaIT.java`, `product/src/test/java/com/shopapp/product/PostgresContainerSupport.java`

**Interfaces:**
- Produces: `PostgresContainerSupport` — an abstract base (or `@ImportTestcontainers` config) that starts `postgres:16.16` once per JVM with `@ServiceConnection`; every later product IT extends/imports it. Liquibase context in tests: none (no seed).
- Produces: `application.properties` keys ported from YAS `product/src/main/resources/application.properties` with `yas.*` → `shopapp.*`, issuer `${KEYCLOAK_URL:http://identity.shopapp.local}/realms/shopapp`, `spring.liquibase.contexts=${SPRING_LIQUIBASE_CONTEXTS:}`, SQL logging off (YAS's `show_sql=true` dropped).

- [ ] **Step 1:** `product/pom.xml` — dependencies as YAS `product/pom.xml` (webmvc, oauth2-resource-server, validation, data-jpa, postgresql, liquibase, aspectj if used by ported code, `common-library`, springdoc-openapi-starter-webmvc-ui, actuator) minus devtools/h2; test: `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, `testcontainers-postgresql`. `spring-boot-maven-plugin` repackage.
- [ ] **Step 2:** Port YAS `product/src/main/resources/db/changelog/ddl/*.sql` verbatim (same file names → same `includeAll` order). Master changelog: `includeAll: path: db/changelog/ddl/` then `include: file: db/changelog/data/sample-data.sql` (file created empty with a `--liquibase formatted sql` header and `context:sample-data` on its changeset in Task 6).
- [ ] **Step 3: Write the failing test** `LiquibaseSchemaIT`:

```java
@SpringBootTest @Import(PostgresContainerSupport.class)
class LiquibaseSchemaIT {
    @Autowired JdbcTemplate jdbc;
    @Test void schemaHasYasProductTables() {
        assertThat(jdbc.queryForList("select table_name from information_schema.tables where table_schema='public'", String.class))
            .contains("product", "brand", "category", "product_category", "product_image", "product_option",
                      "product_option_value", "product_option_combination", "product_related",
                      "product_attribute", "product_attribute_group", "product_attribute_value",
                      "product_template", "product_attribute_template");
    }
    @Test void noSampleDataWithoutContext() { assertThat(jdbc.queryForObject("select count(*) from product", Long.class)).isZero(); }
}
```

- [ ] **Step 4: Run** `mvn -pl product -am verify -Dit.test=LiquibaseSchemaIT` (configure failsafe for `*IT`). Expected: FAIL (no application class / context fails) — then add `ProductApplication` (`@SpringBootApplication(scanBasePackages = {"com.shopapp.product", "com.shopapp.commonlibrary"})`, `@ConfigurationPropertiesScan`).
- [ ] **Step 5: Run** again. Expected: PASS. If Testcontainers cannot reach Docker on Windows/Rancher Desktop, fix via `~/.testcontainers.properties` or `DOCKER_HOST` and ledger the ruling. If the table list differs from YAS's final schema, the port is wrong — fix the changesets, not the test.
- [ ] **Step 6: Run** `mvn verify` from root. Expected: BUILD SUCCESS (proves Spring Cloud BOM import resolves).
- [ ] **Step 7: Commit** `feat(product): service skeleton with Liquibase schema ported from YAS`.

---

### Task 2: product entities and repositories

**Files:**
- Create: `product/src/main/java/com/shopapp/product/model/**` (port YAS `model/` incl. `attribute/`, `enumeration/DimensionUnit`; skip `ProductOptionValueSaveVm`, `ProductVariationSaveVm` and `enumeration/FilterExistInWhSelection` — they belong to admin/inventory), `repository/**`, `config/DatabaseAutoConfig.java`
- Test: `product/src/test/java/com/shopapp/product/repository/ProductRepositoryIT.java`

**Interfaces:**
- Produces entities with YAS field names (e.g. `Product`: `id, name, shortDescription, description, specification, sku, gtin, slug, price, hasOptions, isAllowedToOrder, isPublished, isFeatured, isVisibleIndividually, stockTrackingEnabled, stockQuantity, taxClassId, metaTitle, metaKeyword, metaDescription, thumbnailMediaId, weight, dimensionUnit, length, width, height, brand, productCategories, attributeValues, productImages, parent, products, taxIncluded`). Auditable ones extend `AbstractAuditEntity`.
- Produces repository methods the later tasks call — port only these from YAS repositories (same names/signatures): `ProductRepository.findBySlugAndIsPublishedTrue(String)`, `getFeaturedProduct(Pageable)` (YAS name), `findByProductNameAndCategorySlugAndPriceBetween(...)` (the query behind `/storefront/products`), `findAllByProductCategoriesIn(List<ProductCategory>, Pageable)`, `findAllByBrandAndIsPublishedTrueOrderByIdAsc(Brand)`, `findAllByParentIdAndIsPublishedTrue(...)` (variations); `CategoryRepository.findBySlug`, `findByNameContainingIgnoreCase`; `BrandRepository.findBySlug`, `findByNameContainingIgnoreCase`; `ProductCategoryRepository.findAllByCategory(...)`; `ProductRelatedRepository.findAllByProduct(Product, Pageable)`; `ProductOptionCombinationRepository.findAllByProduct(Product)`; `ProductAttributeValueRepository.findAllByProduct(Product)`. Exact names come from the YAS files; if YAS names differ from these, YAS wins and the ledger notes it.
- `DatabaseAutoConfig`: `@EnableJpaAuditing(auditorAwareRef = "auditorAware")` with an `AuditorAware<String>` returning the JWT subject via `AuthenticationUtils`, or `Optional.empty()` when unauthenticated (storefront reads are anonymous; must not throw).

- [ ] **Step 1: Write the failing test** `ProductRepositoryIT` (Testcontainers, `@DataJpaTest` + `PostgresContainerSupport`, data built in the test):
  - `findBySlugAndIsPublishedTrue_findsPublishedOnly` — published `iphone-15` found; unpublished `draft-phone` → empty.
  - `getFeaturedProduct_returnsOnlyFeaturedPublishedVisible` — featured+published+visible returned; featured but unpublished not.
  - `findAllByParentId_returnsVariations` — parent with two children → 2.
  - `auditing_anonymousSave_doesNotThrow` — saving a `Brand` with no security context succeeds; `createdBy` is null.
- [ ] **Step 2: Run** `mvn -pl product -am verify -Dit.test=ProductRepositoryIT`. Expected: compilation FAIL.
- [ ] **Step 3: Port** entities, repositories and `DatabaseAutoConfig`.
- [ ] **Step 4: Run** the IT, then `mvn -pl product -am verify`. Expected: PASS, Checkstyle clean.
- [ ] **Step 5: Commit** `feat(product): entities and repositories ported from YAS`.

---

### Task 3: storefront category and brand endpoints

**Files:**
- Create: `service/CategoryService.java`, `service/BrandService.java`, `controller/CategoryController.java`, `controller/BrandController.java`, `viewmodel/category/CategoryGetVm.java`, `viewmodel/brand/BrandVm.java`, `viewmodel/brand/BrandListGetVm.java`, `config/SecurityConfig.java`
- Test: `controller/CategoryControllerIT.java`, `controller/BrandControllerIT.java`

**Interfaces:**
- `GET /storefront/categories?categoryName=` → `List<CategoryGetVm>`; `GET /storefront/brands?brandName=` → `List<BrandVm>`; `GET /storefront/brands/paging?pageNo&pageSize` → `BrandListGetVm`. Shapes ported from YAS records. (`/storefront/categories/suggestions` skipped — not used by 2a pages.)
- `SecurityConfig` (ported shape from YAS product `SecurityConfig`): permit `/storefront/**`, `/actuator/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`; any other request authenticated; JWT resource server; CORS via `common-library` `CorsConfig`.

- [ ] **Step 1: Write the failing tests** (`@SpringBootTest(webEnvironment=MOCK)` + `@AutoConfigureMockMvc` + Testcontainers; data inserted per test):
  - `listCategories_returnsAll_anonymous` — 2 categories → 200, JSON array size 2 with `name`, `slug`; no auth header needed.
  - `listCategories_filtersByName` — `?categoryName=pho` returns only "Phones".
  - `listBrands_returnsAll`, `brandsPaging_returnsPage` — `pageNo=0,pageSize=1` → 1 item, `totalPages` 2 (YAS field names).
  - `nonStorefrontPath_requiresAuth` — `GET /backoffice/brands` → 401.
- [ ] **Step 2: Run** `mvn -pl product -am verify -Dit.test='CategoryControllerIT,BrandControllerIT'`. Expected: FAIL.
- [ ] **Step 3: Port** services, controllers (storefront mappings only), view models, `SecurityConfig`.
- [ ] **Step 4: Run** the ITs, then `mvn -pl product -am verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(product): storefront category and brand endpoints`.

---

### Task 4: storefront product list endpoints

**Files:**
- Create/extend: `service/ProductService.java` (list methods), `service/MediaService.java`, `controller/ProductController.java`, `viewmodel/product/{ProductFeatureGetVm, ProductThumbnailVm, ProductThumbnailGetVm, ProductListGetFromCategoryVm, ProductsGetVm}.java`
- Test: `controller/ProductListControllerIT.java`, `service/MediaServiceTest.java`

**Interfaces:**
- `GET /storefront/products/featured?pageNo=0&pageSize=10` → `ProductFeatureGetVm(List<ProductThumbnailGetVm> productList, int totalPage)`
- `GET /storefront/products?pageNo=0&pageSize=5&productName=&categorySlug=&startPrice=&endPrice=` → `ProductsGetVm` (YAS shape)
- `GET /storefront/category/{categorySlug}/products?pageNo=0&pageSize=2` → `ProductListGetFromCategoryVm`
- `GET /storefront/brand/{brandSlug}/products` → `List<ProductThumbnailVm>`
- `GET /storefront/products/related-products/{id}?pageNo=0&pageSize=5` → `ProductsGetVm`
- `MediaService.getMedia(Long mediaId) : NoFileMediaVm`-style call as in YAS, but in 2a: when `mediaId` is null **or** `shopapp.services.media` is blank, return a VM with `url = ""` without any HTTP call. Thumbnail URL fields are `""` → storefront treats empty as "no image". (Real HTTP call + circuit breaker come with the media service in slice 3.)
- Unknown category/brand slug → `NotFoundException` → 404 `ErrorVm` (YAS behaviour).

- [ ] **Step 1: Write the failing tests:**
  - `featured_returnsFeaturedPublishedVisibleOnly` — fixtures: A featured, B not featured, C featured+unpublished → `productList` names `["A"]`, `totalPage` 1, `thumbnailUrl` `""`.
  - `storefrontLists_excludeUnpublishedAndInvisible` — unpublished product and a variation child (not visible individually) absent from `/storefront/products`, category and brand lists.
  - `products_filterByCategorySlugAndPrice` — `categorySlug=phones&startPrice=100&endPrice=500` returns only in-range phones.
  - `categoryProducts_unknownSlug_returns404` — `$.statusCode` `"404 NOT_FOUND"`.
  - `brandProducts_returnsThumbnails`.
  - `relatedProducts_returnsRelated` — A related to B,C → names B, C.
  - `featured_pagePastEnd_returnsEmptyList` — `pageNo=99` → 200, empty `productList`.
  - `products_negativePageSize_returns400` — `pageSize=-1` → 400 `ErrorVm` (add validation `@Min(0)`/`@Positive` or catch `IllegalArgumentException` from `PageRequest.of` and map to `BadRequestException`; not 500).
  - `MediaServiceTest.nullMediaId_returnsEmptyUrlWithoutHttp`, `blankMediaServiceUrl_returnsEmptyUrl`.
- [ ] **Step 2: Run** `mvn -pl product -am verify -Dit.test=ProductListControllerIT -Dtest=MediaServiceTest`. Expected: FAIL.
- [ ] **Step 3: Port** the list methods of YAS `ProductService`, `MediaService` (with the 2a fallback), controller mappings, view models.
- [ ] **Step 4: Run** the tests, then `mvn -pl product -am verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(product): storefront product list endpoints`.

---

### Task 5: product detail and variations

**Files:**
- Create/extend: `service/ProductDetailService.java` or `ProductService` (as YAS splits it), `controller/ProductController.java`, `viewmodel/product/{ProductDetailGetVm, ProductVariationGetVm, ProductOptionValueGetVm, …attribute group VMs used by ProductDetailGetVm}.java`
- Test: `controller/ProductDetailControllerIT.java`

**Interfaces:**
- `GET /storefront/product/{slug}` → `ProductDetailGetVm` (YAS shape: id, name, brandName, productCategories, productAttributeGroups (grouped attribute values), shortDescription, description, specification, isAllowedToOrder, isPublished, isFeatured, hasOptions, price, thumbnailMediaUrl, productImageMediaUrls).
- `GET /storefront/product-variations/{id}` → `List<ProductVariationGetVm>` (each with `options` map optionId→value, price, sku…), from the parent's published children.
- Unknown or unpublished slug → 404 `ErrorVm`.

- [ ] **Step 1: Write the failing tests:**
  - `getProductDetail_returnsGroupedAttributes` — product with group "Display" {Size: 6.1"} and group "Battery" {Capacity: 3000mAh} → `productAttributeGroups` has 2 groups with those values; `brandName` set; `productCategories` contains "Phones".
  - `getProductDetail_bareProduct_returnsEmptyCollections` — no brand/category/attributes/images → 200, `brandName` null, collections `[]`, `thumbnailMediaUrl` `""`.
  - `getProductDetail_unknownSlug_returns404`, `getProductDetail_unpublished_returns404`.
  - `getVariations_returnsChildrenWithOptions` — parent with options Color{Red,Blue} → 2 variations, each `options` maps the Color option id to its value, prices 699.0 / 709.0.
  - `getVariations_productWithoutOptions_returnsEmpty`.
- [ ] **Step 2: Run** `mvn -pl product -am verify -Dit.test=ProductDetailControllerIT`. Expected: FAIL.
- [ ] **Step 3: Port** the detail and variation code paths from YAS.
- [ ] **Step 4: Run**, then `mvn -pl product -am verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(product): storefront product detail and variations`.

---

### Task 6: sample data, container, compose and nginx route for product

**Files:**
- Create: `product/src/main/resources/db/changelog/data/sample-data.sql`, `product/Dockerfile`
- Modify: `docker-compose.yml` (+ `product`), `nginx/templates/default.conf.template` (`api.${DOMAIN}` gets `location /product/ { proxy_pass http://product:8080; … }` replacing the placeholder only for that path — `/` keeps the 404 JSON), `.env` (+ `PRODUCT_LIQUIBASE_CONTEXTS=sample-data`)
- Test: `product/src/test/java/com/shopapp/product/SampleDataIT.java`

**Interfaces:**
- Produces seeded slugs later tasks and the smoke script use: categories `phones`, `laptops`, `tablets`, `audio`, `accessories`; brands `apple`, `samsung`, `sony`, `dell`; ≥20 products; at least one featured product named **"Galaxy S24"** slug **`galaxy-s24`**, price `799.0`, with options Color{Black, Violet} → 2 variations (799.0 / 819.0), attribute groups Display + Battery, related to ≥2 products; one bare product `usb-c-cable` (no options/attributes/related); one unpublished `prototype-phone`.
- Compose `product`: build `./product`, image `shopapp/product:dev`, env `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/product`, DB creds from `.env`, `KEYCLOAK_URL`, `SPRING_LIQUIBASE_CONTEXTS=${PRODUCT_LIQUIBASE_CONTEXTS}`, `SHOPAPP_SERVICES_MEDIA=` (blank), healthcheck `wget -qO- http://localhost:8080/product/actuator/health | grep -q UP` (use an image with wget or curl, or `eclipse-temurin:27-jre` + `HEALTHCHECK` via a tiny Java-free check — builder's call), `depends_on` postgres healthy and identity healthy. Not published on the host.
- Dockerfile: YAS style — `FROM eclipse-temurin:27-jre`, `COPY target/product-*.jar app.jar`, `ENTRYPOINT ["java","-jar","/app.jar"]` (build the jar with Maven first; README documents `mvn -DskipTests package` before `docker compose build`).

- [ ] **Step 1: Write the failing test** `SampleDataIT` (`spring.liquibase.contexts=sample-data`): `galaxy-s24` exists, featured, price 799.0, 2 variations; ≥20 published products; `prototype-phone` unpublished; categories contain the 5 slugs.
- [ ] **Step 2: Run** `mvn -pl product -am verify -Dit.test=SampleDataIT`. Expected: FAIL.
- [ ] **Step 3: Write** `sample-data.sql` (Liquibase formatted SQL, one changeset, `context:sample-data`).
- [ ] **Step 4: Run** the IT, then `mvn verify`. Expected: PASS; `LiquibaseSchemaIT.noSampleDataWithoutContext` still passes.
- [ ] **Step 5: Add** Dockerfile, compose service, nginx route, `.env` key.
- [ ] **Step 6: Verify:** `mvn -B -DskipTests package && docker compose up -d --build --wait product nginx` then `curl -s http://api.shopapp.local/product/storefront/product/galaxy-s24` → JSON with `"name":"Galaxy S24"`; `curl -s -o /dev/null -w '%{http_code}' http://api.shopapp.local/product/storefront/product/nope` → `404`; `curl -s http://api.shopapp.local/` → still the `no services yet` JSON 404.
- [ ] **Step 7: Commit** `feat(product): sample data, container and api route`.

---

### Task 7: storefront-bff gateway

**Files:**
- Modify: `pom.xml` (+ `<module>storefront-bff</module>`), `docker-compose.yml` (+ `storefront-bff`), `nginx/templates/default.conf.template` (+ `server_name storefront.${DOMAIN}` → `storefront-bff:8087`, same proxy headers/`$http_host` as identity)
- Create: `storefront-bff/pom.xml`, `storefront-bff/Dockerfile`, `storefront-bff/src/main/java/com/shopapp/storefrontbff/StorefrontBffApplication.java`, `storefront-bff/src/main/resources/application.yaml`
- Test: `storefront-bff/src/test/java/com/shopapp/storefrontbff/GatewayRoutesTest.java`

**Interfaces:**
- Dependencies: `spring-cloud-starter-gateway-server-webflux`, actuator; test: `spring-boot-starter-test`, a mock HTTP backend (WireMock or OkHttp MockWebServer — builder's call). **No** security/oauth2/redis/session in 2a.
- `application.yaml`: port 8087; routes in order: `api` — `Path=/api/**` → `${API_BASE_URL:http://api.shopapp.local}`, `StripPrefix=1`; `storefront` — `Path=/**` → `${STOREFRONT_URL:http://storefront:3000}`. Gateway HTTP client connect timeout 2s, response timeout 10s.
- Compose: env `API_BASE_URL=http://api.${DOMAIN}${port-suffix if not 80}` (in-network via nginx alias), `STOREFRONT_URL=http://storefront:3000`; healthcheck on `/actuator/health`; not published.

- [ ] **Step 1: Write the failing tests** (`@SpringBootTest(webEnvironment=RANDOM_PORT)`, `WebTestClient`, two mock backends whose URLs are injected as `API_BASE_URL`/`STOREFRONT_URL`):
  - `apiRoute_stripsPrefix` — `GET /api/product/storefront/categories` reaches the API mock as `/product/storefront/categories`; body relayed; status 200.
  - `otherPaths_goToStorefront` — `GET /products/galaxy-s24` reaches the storefront mock at the same path.
  - `apiRoute_relays404` — mock returns 404 JSON → client gets 404 with same body.
  - `apiRoute_backendDown_returns5xxQuickly` — API URL pointing at a closed port → 5xx within 5s.
- [ ] **Step 2: Run** `mvn -pl storefront-bff -am verify -Dtest=GatewayRoutesTest`. Expected: FAIL. (If the RC2 gateway fails to start on Boot 4.1.2/Java 27 for reasons other than missing code: stop and ask.)
- [ ] **Step 3: Implement** application class and `application.yaml`.
- [ ] **Step 4: Run**, then `mvn verify`. Expected: PASS.
- [ ] **Step 5: Add** Dockerfile (`eclipse-temurin:27-jre`), compose service, nginx `storefront.${DOMAIN}` server.
- [ ] **Step 6: Verify:** `mvn -B -DskipTests package && docker compose up -d --build --wait storefront-bff nginx`; `curl -s --resolve storefront.shopapp.local:80:127.0.0.1 http://storefront.shopapp.local/api/product/storefront/categories` → JSON with `phones`. (The `/**` route fails until Task 9 — expected.)
- [ ] **Step 7: Commit** `feat(storefront-bff): gateway routes to api and storefront`.

---

### Task 8: storefront app foundation (Next.js, API client, models, option→variation logic)

**Files:**
- Create: `storefront/package.json` (Next.js 16.x, React 19.x, react-bootstrap, bootstrap, test runner), `next.config.ts`, `tsconfig.json`, `.eslintrc`/eslint config, test config, `.gitignore` (`node_modules`, `.next`)
- Create: `storefront/common/services/ApiClientService.ts`, `storefront/modules/catalog/models/*.ts` (port YAS `modules/catalog/models` used by 2a: `Category, Product, ProductAttribute, ProductDetail, ProductFeature, ProductOptionValueGet, ProductOptions, ProductThumbnail, ProductVariation, ProductsGet`), `storefront/modules/catalog/services/{ProductService,CategoryService}.ts`, `storefront/modules/catalog/utils/variation.ts`, `storefront/common/utils/image.ts`
- Test: `storefront/modules/catalog/utils/variation.test.ts`, `storefront/common/utils/image.test.ts`, `storefront/common/services/ApiClientService.test.ts`

**Interfaces:**
- `ApiClientService`: `get<T>(path: string): Promise<T>` — in the browser uses base `/api`; on the server uses `process.env.API_BASE_PATH_SERVER` (e.g. `http://storefront-bff:8087/api`). Non-2xx → throws `ApiError { status: number; body: unknown }`.
- Services (YAS names/paths): `getFeaturedProducts(pageNo)`, `getProductByMultiParams(query)`, `getProductsByCategory(slug, pageNo)`, `getProductsByBrand(slug)`, `getProductDetail(slug)`, `getProductVariations(productId)`, `getRelatedProductsByProductId(id)`, `getCategories()`, `getBrands()` → `/product/storefront/...` with the Task 3–5 signatures.
- `findVariation(variations: ProductVariation[], selected: Record<number, string>): ProductVariation | undefined` — exact match on every option of the variation.
- `imageOrPlaceholder(url?: string | null): string` — empty/null → `/static/images/default.png` (placeholder asset added in Task 9).

- [ ] **Step 1: Scaffold** with `npm create next-app@16` (TypeScript, ESLint, pages router, no Tailwind) or by hand; pin exact versions in `package.json` (no `^`).
- [ ] **Step 2: Write the failing tests:**
  - `findVariation` — Color{1:Black,2:Violet} variations; `{1:"Violet"}` → the 819.0 one; `{1:"Red"}` → undefined; product with two options Color+Storage requires both to match; empty selection → undefined.
  - `imageOrPlaceholder("")`, `(null)`, `(undefined)` → placeholder; `"http://x/a.png"` → unchanged.
  - `ApiClientService.get` — server side (`typeof window === 'undefined'`) with `API_BASE_PATH_SERVER=http://bff/api` calls `http://bff/api/product/x`; non-2xx rejects with `ApiError` carrying `status` 404 (mock `fetch`).
- [ ] **Step 3: Run** `npm test`. Expected: FAIL.
- [ ] **Step 4: Implement** the client, models, services, utils.
- [ ] **Step 5: Run** `npm test && npm run lint && npx tsc --noEmit`. Expected: all pass.
- [ ] **Step 6: Commit** `feat(storefront): Next.js app, API client and catalog models`.

---

### Task 9: storefront pages and container

**Files:**
- Create: `storefront/pages/{_app.tsx, index.tsx, 404.tsx, products/index.tsx, products/[slug].tsx}`, `storefront/common/components/{Layout,Header,Footer,ProductCard,Pagination,ErrorMessage}.tsx`, `storefront/modules/catalog/components/{ProductDetails,DetailHeader,RelatedProducts}.tsx` (port YAS components, drop cart/rating/inventory bits), `storefront/common/hooks/useFetch.ts`, `storefront/public/static/images/default.png`, `storefront/Dockerfile`, `storefront/.dockerignore`
- Modify: `docker-compose.yml` (+ `storefront`)
- Test: `storefront/modules/catalog/components/ProductDetails.test.tsx`, `storefront/common/hooks/useFetch.test.ts`, `storefront/pages/products/slug.test.ts` (getServerSideProps)

**Interfaces:**
- Home: featured products grid (client fetch `getFeaturedProducts(0)`) + category list linking to `/products?categorySlug=<slug>`.
- `/products`: client fetch `getProductByMultiParams` with `categorySlug`, `pageNo` from the URL query; category filter sidebar; pagination; a brand link goes to `/products?brandSlug=<slug>` which uses `getProductsByBrand` instead (separate, not combined — spec).
- `/products/[slug]`: `getServerSideProps` → `getProductDetail(slug)`; `ApiError` 404 → `{ notFound: true }`; other errors rethrown (Next renders 500 page). Client side: fetch variations when `hasOptions`; option buttons; `findVariation` → shown price; related products via `getRelatedProductsByProductId`.
- `useFetch<T>(fn): { data?: T; error?: ApiError | Error; loading: boolean }`; pages render `<ErrorMessage>` when `error`.
- Dockerfile: multi-stage `node:24-alpine` (`npm ci && npm run build` → `next start -p 3000`, or `output: 'standalone'`); compose env `API_BASE_PATH_SERVER=http://storefront-bff:8087/api`; healthcheck `wget -qO- http://localhost:3000/ >/dev/null`; `depends_on` storefront-bff healthy. Note the cycle: bff routes `/**` to storefront and storefront SSR calls bff `/api/**` — start order is bff then storefront; bff does not need storefront to be healthy.

- [ ] **Step 1: Write the failing tests** (React Testing Library):
  - `ProductDetails renders name, brand, price and grouped specification` (fixture of `galaxy-s24`).
  - `ProductDetails renders a bare product` — no brand/attributes/options/related → no "Specification"/"Options" headings, no crash.
  - `ProductDetails selecting Violet shows 819` — variations fixture; click "Violet" → price text contains `819`.
  - `ProductDetails without image shows placeholder` — `img` src ends with `/static/images/default.png`.
  - `useFetch error state shows message` — fn rejects → `error` set, `loading` false.
  - `getServerSideProps unknown slug returns notFound` — service mock throws `ApiError(404)` → `{ notFound: true }`; `ApiError(500)` → rejects.
- [ ] **Step 2: Run** `npm test`. Expected: FAIL.
- [ ] **Step 3: Implement** pages, components, hook, placeholder image.
- [ ] **Step 4: Run** `npm test && npm run lint && npx tsc --noEmit && npm run build`. Expected: all pass.
- [ ] **Step 5: Add** Dockerfile, compose service. **Verify:** `docker compose up -d --build --wait storefront`; `curl -s --resolve storefront.shopapp.local:80:127.0.0.1 http://storefront.shopapp.local/products/galaxy-s24` → 200 HTML containing `Galaxy S24` and `799`; `/products/nope` → 404.
- [ ] **Step 6: Commit** `feat(storefront): home, product list and product detail pages`.

---

### Task 10: smoke checks, README, hosts entry, full definition of done

**Files:**
- Modify: `scripts/verify-foundation.sh` (add `storefront` to `HOSTS` and the hosts hint; add `product`, `storefront-bff`, `storefront` to `SERVICES`; add 2a checks), `README.md`

**Interfaces:**
- New smoke checks (all through nginx with `url storefront …`):
  1. `GET /` → 200 and HTML contains `<div id="__next"` (page shell).
  2. `GET /api/product/storefront/products/featured` → 200 JSON whose `productList[].name` contains `Galaxy S24`.
  3. `GET /api/product/storefront/categories` → 200 JSON containing slug `phones`.
  4. `GET /products/galaxy-s24` → 200 HTML containing `Galaxy S24` and `799`.
  5. `GET /products/does-not-exist` → 404; `GET /api/product/storefront/product/does-not-exist` → 404 JSON with `"statusCode":"404 NOT_FOUND"`.
  6. `GET /api/product/storefront/product/prototype-phone` → 404 (unpublished never exposed).
- README: new hostname `storefront.shopapp.local` in the hosts line and URL table; build steps (`mvn -DskipTests package` before `docker compose up -d --build --wait`); running the storefront locally (`cd storefront && npm ci && npm run dev`, pointing at the BFF); Spring Cloud RC note in Build notes; services list.

- [ ] **Step 1: Add** the checks to the script (they fail until the user adds the hosts entry — the script already says how).
- [ ] **Step 2: Ask the user** to add `storefront.shopapp.local` to the hosts line (Administrator). Stop until confirmed.
- [ ] **Step 3: Run** `mvn -B verify && (cd storefront && npm test && npm run build) && mvn -B -DskipTests package && docker compose up -d --build --wait && bash scripts/verify-foundation.sh --restart`. Expected: BUILD SUCCESS, storefront tests and build pass, all smoke checks PASS (slice 1 + 2a), including after restart.
- [ ] **Step 4: Update** README.
- [ ] **Step 5: Ask the user** for the manual browser check: home → category → `Galaxy S24` → pick Violet → price 819 → related product link opens.
- [ ] **Step 6: Commit** `feat: slice 2a smoke checks and README`.
