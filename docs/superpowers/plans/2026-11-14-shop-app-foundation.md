# shop_app Foundation (Slice 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Java 27 / Spring Boot 4 Maven build with a tested `common-library`, plus a Docker Compose stack (Postgres, pgAdmin, Keycloak, Kafka, Kafka UI, nginx) that comes up healthy and is reachable through `*.shopapp.local` hostnames.

**Architecture:** Maven multi-module repo (parent POM + `common-library`), ported from YAS at a pinned commit and renamed to `com.shopapp`. Shared infrastructure runs in one Compose network behind nginx; local-dev settings live in `.env`. A bash smoke script checks the definition of done.

**Tech Stack:** Java 27, Spring Boot 4.x (newest minor supporting Java 27) + matching Spring Cloud, JUnit 5, Mockito, AssertJ, Checkstyle, JaCoCo, PostgreSQL 16 (`wal_level=logical`), Keycloak 26.x, Apache Kafka 4.x (KRaft), kafbat/kafka-ui, pgAdmin 4, nginx.

**Spec:** `docs/superpowers/specs/2026-11-14-shop-app-foundation-design.md`

## Global Constraints

- Java: `<java.version>27</java.version>`, `maven.compiler.release=27`, built on JDK 27. Never drop to `release 25` silently. If Spring Boot / Spring Cloud / Hibernate cannot run on 27, stop and ask the user.
- Spring Boot: the newest stable **4.x** minor that supports Java 27, plus its matching Spring Cloud train.
- groupId `com.shopapp`; library package `com.shopapp.commonlibrary`; version via `${revision}` = `1.0-SNAPSHOT`.
- YAS reference: `nashtech-garage/yas` @ `179e813568c345bad3fce985088b5535e57481aa`. "Port from YAS" always means this commit (`gh api "repos/nashtech-garage/yas/contents/<path>?ref=179e8135…"`).
- Domain `shopapp.local` appears only in `.env`. Hostnames: `identity.`, `api.`, `pgadmin.`, `kafka.` + `${DOMAIN}`.
- Keycloak realm name `shopapp`; realm roles `ADMIN`, `CUSTOMER`, `GUEST`; clients `storefront-bff`, `backoffice-bff`, `customer-management`, `swagger-ui`.
- Every image and library version is pinned (no `latest` tags).
- Lombok and MapStruct only if they pass the Task 1 probe. Otherwise use records and plain Java (SLF4J `LoggerFactory`, explicit getters/setters).
- Text files used by containers (`*.sh`, `*.sql`, `*.conf*`, `*.json`, `.env`) must have LF line endings, enforced by `.gitattributes`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Port 80 already in use** (IIS, Skype, another nginx): nginx must fail with an obvious cause, not a mystery. The host port comes from `NGINX_HTTP_PORT` in `.env` (default 80), and the README says how to change it. Task 10 smoke script prints the port it uses.
2. **Missing hosts-file entries:** the smoke script checks each hostname resolves to 127.0.0.1 *before* any HTTP call and prints the exact line to add. Task 10.
3. **Keycloak issuer mismatch:** a token fetched via `http://identity.shopapp.local` must carry `iss = http://identity.shopapp.local/realms/shopapp`, and containers on the network must resolve `identity.shopapp.local` (nginx network alias) so later services validate the same issuer. Task 8 smoke check asserts the `iss` claim. Task 10 asserts in-network resolution.
4. **Restart on existing volumes:** `docker compose down && docker compose up -d` (no `-v`) must come back healthy. That means the realm import must not fail on an existing realm, and the Postgres init script only runs on an empty volume (documented). Task 10 smoke runs a restart cycle.
5. **CRLF line endings on Windows** breaking the shell scripts or SQL inside containers: `.gitattributes` forces LF (Task 1). Task 10 smoke fails if any tracked container file contains `\r`.

---

## File Structure

```
pom.xml                                   parent: modules, versions, dependencyManagement, plugins
.gitignore  .gitattributes  .env
checkstyle/checkstyle.xml  checkstyle/suppressions.xml   (from YAS checkstyle/)
common-library/pom.xml
common-library/src/main/java/com/shopapp/commonlibrary/
  constants/ApiConstant.java  MessageCode.java  PageableConstant.java
  viewmodel/error/ErrorVm.java
  exception/  NotFoundException BadRequestException DuplicatedException AccessDeniedException
              ForbiddenException SignInRequiredException InternalServerErrorException
              ResourceExistedException ApiExceptionHandler
  utils/MessagesUtils.java  AuthenticationUtils.java  DateTimeUtils.java
  model/AbstractAuditEntity.java  model/listener/CustomAuditingEntityListener.java
  config/CorsConfig.java  ServiceUrlConfig.java
common-library/src/main/resources/messages/messages.properties  logback-spring.xml
common-library/src/test/java/com/shopapp/commonlibrary/...   (mirrors main)
docker-compose.yml
docker/postgres/postgresql.conf           wal_level=logical etc.
docker/pgadmin/servers.json               pre-registered server
postgres_init.sql
identity/realm-export.json
nginx/templates/default.conf.template
scripts/verify-foundation.sh              smoke test for the definition of done
README.md  NOTICE
```

---

### Task 1: Toolchain and Java 27 compatibility spike

**Files:**
- Create: `pom.xml`, `common-library/pom.xml`, `.gitignore`, `.gitattributes`, `checkstyle/checkstyle.xml`, `checkstyle/suppressions.xml`
- Test: `common-library/src/test/java/com/shopapp/commonlibrary/ToolchainProbeTest.java`

**Interfaces:**
- Produces: a parent POM that every later module inherits, and a recorded decision on Lombok and MapStruct (written into the README's "Build notes" section in Task 10; keep the note in the commit message for now).

- [ ] **Step 1: Verify JDK 27 is available.** Run `"$JAVA_HOME/bin/java" -version` (or the path of the installed JDK 27). Expected: `openjdk version "27…"`. If it's missing, stop and ask the user to install JDK 27 (Temurin or Microsoft Build of OpenJDK). Then either set `JAVA_HOME` for Maven, or add `~/.m2/toolchains.xml` with the `maven-toolchains-plugin` (builder's choice; document it in the README).
- [ ] **Step 2: Look up versions.** Find the newest Spring Boot 4.x whose system requirements list Java 27, and its Spring Cloud train (spring.io release notes / the Spring Cloud supported-versions table). Also find the latest Lombok, MapStruct, JaCoCo, Checkstyle and maven-checkstyle-plugin versions that state Java 27 support.
- [ ] **Step 3: Write the parent `pom.xml`.**
  - Parent: `spring-boot-starter-parent` (version from Step 2). `groupId com.shopapp`, `artifactId shop-app`, `packaging pom`, `<version>${revision}</version>`, `<revision>1.0-SNAPSHOT</revision>`, `<modules><module>common-library</module></modules>`.
  - `dependencyManagement`: Spring Cloud BOM, Testcontainers BOM, springdoc-openapi-starter-webmvc-ui, mapstruct, lombok, keycloak-admin-client, logstash-logback-encoder.
  - Plugins: maven-checkstyle-plugin bound to `validate` (`failOnViolation=true`, config `checkstyle/checkstyle.xml`), jacoco (`prepare-agent` + `report`, no gate), flatten-maven-plugin for `${revision}`.
- [ ] **Step 4: Port the Checkstyle config.** Copy YAS `checkstyle/` from the pinned commit. Change any `com.yas` references to `com.shopapp`.
- [ ] **Step 5: Write `common-library/pom.xml`.** Packaging `jar`. Dependencies:
  - Compile: `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `spring-boot-starter-oauth2-resource-server`, `logstash-logback-encoder`.
  - Provisional, removed in Step 9 if the probe fails: lombok (`provided`), mapstruct + `mapstruct-processor` via `annotationProcessorPaths`.
  - Test: `spring-boot-starter-test`.
  - No `spring-boot-maven-plugin` repackage (it's a library).
- [ ] **Step 6: Write the probe test.** It proves that annotation processing works on 27.

```java
class ToolchainProbeTest {
    @lombok.Value static class LombokProbe { String name; }
    @org.mapstruct.Mapper interface ProbeMapper { Target map(Source s); }
    record Source(String name) {}
    record Target(String name) {}

    @Test void runsOnJava27() { assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(27); }
    @Test void lombokGeneratesAccessors() { assertThat(new LombokProbe("x").getName()).isEqualTo("x"); }
    @Test void mapstructGeneratesMapper() {
        assertThat(org.mapstruct.factory.Mappers.getMapper(ProbeMapper.class).map(new Source("x")).name())
            .isEqualTo("x");
    }
}
```

- [ ] **Step 7: Write `.gitignore` and `.gitattributes`.**
  - `.gitignore`: `target/`, `.idea/`, `*.iml`, `.vscode/`, `.flattened-pom.xml`.
  - `.gitattributes`: `* text=auto` plus `eol=lf` for `*.sh *.sql *.conf *.template *.json *.yml *.yaml .env`.
- [ ] **Step 8: Run `mvn -B verify`.** Expected: BUILD SUCCESS with 3 tests passing, Checkstyle clean, and a JaCoCo report in `common-library/target/site/jacoco/`.
- [ ] **Step 9: Apply the compatibility rule to any failure.**
  1. Upgrade the failing plugin or library.
  2. If it's JaCoCo, disable it and note that for the README.
  3. If Lombok or MapStruct fails, remove that dependency and its probe test, and record "Lombok: dropped" / "MapStruct: dropped".
  4. If Spring itself fails, stop and ask the user.

  Re-run until `mvn -B verify` is green.
- [ ] **Step 10: Commit.** Commit message: `build: parent POM and common-library skeleton on Java 27` plus a body line `Lombok: kept|dropped, MapStruct: kept|dropped, JaCoCo: kept|disabled`.

---

### Task 2: Messages, constants, ErrorVm and the exception types

**Files:**
- Create: `constants/ApiConstant.java`, `constants/MessageCode.java`, `constants/PageableConstant.java`, `viewmodel/error/ErrorVm.java`, `utils/MessagesUtils.java`, the eight exception classes listed in File Structure (not the handler), `resources/messages/messages.properties`
- Test: `utils/MessagesUtilsTest.java`, `exception/ExceptionMessageTest.java`

**Interfaces:**
- Produces:
  - `record ErrorVm(String statusCode, String title, String detail, List<String> fieldErrors)` with a 3-arg constructor that sets `fieldErrors = List.of()` (immutable, unlike YAS's `ArrayList`; nothing mutates it).
  - `MessagesUtils.getMessage(String errorCode, Object... args) : String`. Looks the code up in bundle `messages.messages`. A missing code falls back to the code itself. `{}` placeholders are filled via SLF4J `MessageFormatter.arrayFormat`.
  - Every exception extends `RuntimeException`, has the constructor `(String errorCode, Object... args)`, and returns `MessagesUtils.getMessage(errorCode, args)` from `getMessage()`. Exception: `InternalServerErrorException` takes `(String message)`. `AccessDeniedException` lives in `com.shopapp.commonlibrary.exception` and must not be confused with Spring Security's class.
  - `ApiConstant`: the YAS constants `CODE_200…CODE_500`, their text constants and `ACCESS_DENIED = "ACCESS_DENIED"`. Drop YAS's `WAREHOUSE_URL` and `STOCK_HISTORY_URL` (inventory-specific).
  - `PageableConstant.DEFAULT_PAGE_SIZE = "10"`, `DEFAULT_PAGE_NUMBER = "0"`.
  - `MessageCode`: start with `ACCESS_DENIED`, `SIGN_IN_REQUIRED`, `RESOURCE_NOT_FOUND`. Services add their own codes.

- [ ] **Step 1: Write the failing tests.**

```java
// MessagesUtilsTest  (messages.properties contains: RESOURCE_NOT_FOUND=Resource {} with id {} not found)
@Test void resolvesCodeAndFillsPlaceholders() {
    assertThat(MessagesUtils.getMessage("RESOURCE_NOT_FOUND", "Product", 7))
        .isEqualTo("Resource Product with id 7 not found");
}
@Test void unknownCodeFallsBackToCode() { assertThat(MessagesUtils.getMessage("NO_SUCH_CODE")).isEqualTo("NO_SUCH_CODE"); }
@Test void tooFewArgsLeavesPlaceholder() {
    assertThat(MessagesUtils.getMessage("RESOURCE_NOT_FOUND", "Product")).isEqualTo("Resource Product with id {} not found");
}

// ExceptionMessageTest
@Test void notFoundResolvesMessage() {
    assertThat(new NotFoundException("RESOURCE_NOT_FOUND", "Product", 7).getMessage())
        .isEqualTo("Resource Product with id 7 not found");
}
@Test void errorVmShortConstructorHasEmptyFieldErrors() {
    assertThat(new ErrorVm("404 NOT_FOUND", "Not Found", "x").fieldErrors()).isEmpty();
}
```

- [ ] **Step 2: Run** `mvn -B -pl common-library test`. Expected: compilation FAIL (classes missing).
- [ ] **Step 3: Implement** the classes per the Interfaces block. Write `messages.properties` with `ACCESS_DENIED=Access denied`, `SIGN_IN_REQUIRED=Authentication required`, `RESOURCE_NOT_FOUND=Resource {} with id {} not found`. Load the bundle with `Locale.ROOT` (not `Locale.getDefault()` as YAS does), so tests don't depend on the machine's locale.
- [ ] **Step 4: Run** `mvn -B -pl common-library verify`. Expected: PASS, Checkstyle clean.
- [ ] **Step 5: Commit** `feat(common): messages, constants, ErrorVm and exception types`.

---

### Task 3: ApiExceptionHandler

**Files:**
- Create: `exception/ApiExceptionHandler.java`
- Test: `exception/ApiExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `ErrorVm` and the exceptions from Task 2.
- Produces: `@RestControllerAdvice public class ApiExceptionHandler`. Status mapping, as in YAS:

| Exception | Status | `detail` | `fieldErrors` |
|---|---|---|---|
| `NotFoundException` | 404 | ex message | empty |
| `BadRequestException`, `DuplicatedException`, `DataIntegrityViolationException`, `MissingServletRequestParameterException` | 400 | ex message | empty |
| `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `ConstraintViolationException` | 400 | `"Request information is not valid"` | `"<field> <message>"` per error |
| `ResourceExistedException` | 409 | ex message | empty |
| `AccessDeniedException` (ours), `ForbiddenException`, `SignInRequiredException` | 403 | ex message | empty |
| `InternalServerErrorException`, any other `Exception` | 500 | ex message | empty |

`statusCode` = `HttpStatus.toString()` (e.g. `"404 NOT_FOUND"`), `title` = reason phrase.

Deliberate fixes to YAS quirks:
- YAS's `handleForbidden` declares a `NotFoundException` parameter for `Forbidden.class`, which breaks at runtime. We have one `ForbiddenException` and no `Forbidden`.
- Never put a stack trace or class name in `detail` for 500s from unexpected exceptions. Use the reason phrase as `detail` and log the exception.

- [ ] **Step 1: Write the failing tests.**
  - Use `MockMvcBuilders.standaloneSetup(new ThrowingController()).setControllerAdvice(new ApiExceptionHandler())` with a test controller that has one endpoint per exception, plus a `@Valid @RequestBody` endpoint for field errors.
  - Tests: `notFound_returns404WithMessage`, `badRequest_returns400`, `duplicated_returns400`, `resourceExisted_returns409`, `accessDenied_returns403`, `forbidden_returns403`, `signInRequired_returns403`, `validation_returns400WithFieldErrors`, `missingParam_returns400`, `unexpected_returns500WithoutInternals`.
  - Each test asserts:
    - the HTTP status;
    - `$.statusCode`, for example `"404 NOT_FOUND"`;
    - `$.title`;
    - `$.detail`, using the exact message, or `"Request information is not valid"` for the validation case;
    - for the validation test, `$.fieldErrors[0]` = `"name must not be blank"`;
    - for the 500 test, `$.detail` = `"Internal Server Error"` and the body has no `"java."` substring.
- [ ] **Step 2: Run** `mvn -B -pl common-library test -Dtest=ApiExceptionHandlerTest`. Expected: FAIL.
- [ ] **Step 3: Implement `ApiExceptionHandler`.** Use a single private `build(HttpStatus, String detail, List<String> fieldErrors, Exception ex) : ResponseEntity<ErrorVm>` that logs at `error` and builds the body.
- [ ] **Step 4: Run** the test and then `mvn -B -pl common-library verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(common): global ApiExceptionHandler`.

---

### Task 4: AuthenticationUtils and DateTimeUtils

**Files:**
- Create: `utils/AuthenticationUtils.java`, `utils/DateTimeUtils.java`
- Test: `utils/AuthenticationUtilsTest.java`, `utils/DateTimeUtilsTest.java`

**Interfaces:**
- Produces:
  - `AuthenticationUtils.extractUserId() : String`. Returns the JWT `sub`. Throws our `AccessDeniedException(ApiConstant.ACCESS_DENIED)` when the authentication is null, anonymous, or not a `JwtAuthenticationToken`. YAS throws `ClassCastException` or `NullPointerException` in those cases; ours throws the defined exception.
  - `AuthenticationUtils.extractJwt() : String`. Returns the token value under the same rules.
  - `AuthenticationUtils.getAuthentication() : Authentication`.
  - `DateTimeUtils.format(LocalDateTime) : String` with the default pattern `"dd-MM-yyyy_HH-mm-ss"`, and `DateTimeUtils.format(LocalDateTime, String pattern) : String`.

- [ ] **Step 1: Write the failing tests.**
  - `extractUserId_returnsSubject`: set the `SecurityContextHolder` to a `JwtAuthenticationToken` built from `Jwt.withTokenValue("t").header("alg","none").subject("u-1").build()`, then expect `"u-1"`.
  - `extractUserId_anonymous_throwsAccessDenied`
  - `extractUserId_noAuthentication_throwsAccessDenied`
  - `extractJwt_returnsTokenValue`, expecting `"t"`.
  - `format_defaultPattern`: `LocalDateTime.of(2026,11,14,9,5,3)` gives `"14-11-2026_09-05-03"`.
  - `format_customPattern`: `"yyyy/MM/dd"` gives `"2026/11/14"`.
  - Clear the context in `@AfterEach`.
- [ ] **Step 2: Run** `mvn -B -pl common-library test -Dtest='AuthenticationUtilsTest,DateTimeUtilsTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** both classes as `final` with private constructors.
- [ ] **Step 4: Run** `mvn -B -pl common-library verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(common): authentication and date-time utilities`.

---

### Task 5: Auditing base entity and listener

**Files:**
- Create: `model/AbstractAuditEntity.java`, `model/listener/CustomAuditingEntityListener.java`
- Test: `model/listener/CustomAuditingEntityListenerTest.java`

**Interfaces:**
- Produces:
  - `@MappedSuperclass @EntityListeners(CustomAuditingEntityListener.class) public abstract class AbstractAuditEntity`.
  - Fields:
    - `ZonedDateTime createdOn` (`@CreationTimestamp`)
    - `String createdBy` (`@CreatedBy`)
    - `ZonedDateTime lastModifiedOn` (`@UpdateTimestamp`)
    - `String lastModifiedBy` (`@LastModifiedBy`)
  - Getters and setters, either via Lombok or explicit, per Task 1.
  - `CustomAuditingEntityListener extends AuditingEntityListener`, constructor `(ObjectFactory<AuditingHandler> handler)`.
    - `touchForCreate(Object)`: if `createdBy` is already set, keep it, and set `lastModifiedBy = createdBy` when it's null. Otherwise delegate to super.
    - `touchForUpdate(Object)`: if `lastModifiedBy` is already set, keep it. Otherwise delegate to super.
  - Services wire the `AuditorAware<String>` (reading `AuthenticationUtils`) themselves in slice 2. It is not part of this task.

- [ ] **Step 1: Write the failing tests.** Use a test subclass `class Sample extends AbstractAuditEntity {}` and a Mockito-mocked `AuditingHandler` behind an `ObjectFactory`.
  - `create_withoutCreatedBy_delegatesToHandler`: verify `handler.markCreated(entity)` is called.
  - `create_withPresetCreatedBy_keepsItAndCopiesToLastModifiedBy`: `createdBy="seed"` gives `lastModifiedBy="seed"`, and the handler is never called.
  - `create_withPresetBoth_keepsBoth`
  - `update_withoutLastModifiedBy_delegates`: verify `handler.markModified(entity)`.
  - `update_withPresetLastModifiedBy_keepsIt`: the handler is never called.
- [ ] **Step 2: Run** `mvn -B -pl common-library test -Dtest=CustomAuditingEntityListenerTest`. Expected: FAIL.
- [ ] **Step 3: Implement** both classes. `AbstractAuditEntity` is `abstract` (YAS's isn't). It's never instantiated directly.
- [ ] **Step 4: Run** `mvn -B -pl common-library verify`. Expected: PASS.
- [ ] **Step 5: Commit** `feat(common): auditing base entity and listener`.

---

### Task 6: CorsConfig, ServiceUrlConfig and logging config

**Files:**
- Create: `config/CorsConfig.java`, `config/ServiceUrlConfig.java`, `resources/logback-spring.xml`
- Test: `config/CorsConfigTest.java`, `config/ServiceUrlConfigTest.java`

**Interfaces:**
- Produces:
  - `@Configuration public class CorsConfig`, bean `corsConfigurer() : WebMvcConfigurer`. It maps `/**` with `allowedOrigins` from property `cors.allowed-origins` (comma-separated, default `*`), `allowedMethods("*")` and `allowedHeaders("*")`. Fix to a YAS quirk: YAS passes the origins string as methods and headers too.
  - `@ConfigurationProperties(prefix = "shopapp.services") public record ServiceUrlConfig(String media, String product)`.
  - `logback-spring.xml`: console appender with the Spring Boot default pattern under profile `!json`, and logstash JSON encoder under profile `json`. No OTel appender (slice 6).

- [ ] **Step 1: Write the failing tests.** Use `WebApplicationContextRunner` / `ApplicationContextRunner`.
  - `cors_allowsConfiguredOrigin`: with `cors.allowed-origins=http://storefront.shopapp.local`, a MockMvc preflight `OPTIONS /x` with `Origin` set to that value returns `Access-Control-Allow-Origin: http://storefront.shopapp.local`.
  - `cors_rejectsOtherOrigin`: origin `http://evil.test` returns 403.
  - `serviceUrls_bindFromProperties`: with `shopapp.services.media=http://media` and `shopapp.services.product=http://product`, the record has those values.
- [ ] **Step 2: Run** `mvn -B -pl common-library test -Dtest='CorsConfigTest,ServiceUrlConfigTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** the two classes and `logback-spring.xml`.
- [ ] **Step 4: Run** `mvn -B verify` from the root. Expected: PASS, all common-library tests green, Checkstyle clean.
- [ ] **Step 5: Commit** `feat(common): CORS, service URL config and logging`.

---

### Task 7: PostgreSQL and pgAdmin

**Files:**
- Create: `.env`, `docker-compose.yml` (postgres and pgadmin only), `docker/postgres/postgresql.conf`, `postgres_init.sql`, `docker/pgadmin/servers.json`

**Interfaces:**
- Produces:
  - Compose network `shopapp-network`, service `postgres` (in-network host `postgres:5432`), named volume `postgres-data`.
  - `.env` keys:
    - `DOMAIN=shopapp.local`
    - `NGINX_HTTP_PORT=80`
    - `POSTGRES_USER=admin`
    - `POSTGRES_PASSWORD=admin`
    - `POSTGRES_HOST_PORT=5432`
    - `PGADMIN_EMAIL=admin@shopapp.local`
    - `PGADMIN_PASSWORD=admin`
  - Later tasks add their own keys.

- [ ] **Step 1: Write `postgres_init.sql`.**
  - Create the database `keycloak`, plus one database per YAS service: `product`, `cart`, `"order"`, `customer`, `media`, `rating`, `location`, `inventory`, `promotion`, `tax`, `payment`, `webhook`, `delivery`, `recommendation`.
  - Owner `admin`, `UTF8`.
  - Check the list against YAS `postgres_init.sql` and the service `application.properties` files at the pinned commit, and add any database YAS uses that's missing here.
- [ ] **Step 2: Write `docker/postgres/postgresql.conf`.** Include `listen_addresses='*'`, `wal_level=logical`, `max_wal_senders=10`, `max_replication_slots=10`.
- [ ] **Step 3: Add the `postgres` service to `docker-compose.yml`.**
  - Image `postgres:16.x` (pinned).
  - Mounts the conf and `postgres_init.sql` → `/docker-entrypoint-initdb.d/`.
  - `command: postgres -c config_file=/etc/postgresql/postgresql.conf`.
  - Port `${POSTGRES_HOST_PORT}:5432`.
  - Healthcheck: `pg_isready -U ${POSTGRES_USER}`.
- [ ] **Step 4: Add the `pgadmin` service.**
  - Image `dpage/pgadmin4` (pinned).
  - `servers.json` pre-registers `postgres:5432` with user `admin`.
  - No host port; it's reached via nginx in Task 10.
- [ ] **Step 5: Verify.** Run `docker compose up -d postgres pgadmin`, then `docker compose exec postgres psql -U admin -tAc "SHOW wal_level; SELECT count(*) FROM pg_database WHERE datname IN ('keycloak','product','order');"`. Expected: `logical` then `3`. `docker compose ps` shows postgres `healthy`.
- [ ] **Step 6: Commit** `infra: postgres with logical replication and pgadmin`.

---

### Task 8: Keycloak with the `shopapp` realm

**Files:**
- Create: `identity/realm-export.json`
- Modify: `docker-compose.yml` (add `identity`), `.env` (add `KEYCLOAK_ADMIN=admin`, `KEYCLOAK_ADMIN_PASSWORD=admin`)

**Interfaces:**
- Consumes: `postgres` (database `keycloak`).
- Produces:
  - Service `identity` (in-network `identity:8080`) with issuer `http://identity.${DOMAIN}/realms/shopapp`.
  - Clients and secrets as below.
  - Dev users `admin` / `admin` (role `ADMIN`) and `customer` / `customer` (role `CUSTOMER`), passwords documented in the README.

- [ ] **Step 1: Build `realm-export.json` from YAS `identity/realm-export.json` at the pinned commit.**
  - Rename realm `Yas` → `shopapp` (realm and `displayName`) and `default-roles-yas` → `default-roles-shopapp`.
  - Remove `loginTheme: "yas"` (theme deferred).
  - Replace `yas.local` hostnames in `redirectUris`, `webOrigins` and `rootUrl` with `shopapp.local`.
  - Keep clients `storefront-bff`, `backoffice-bff`, `customer-management` and `swagger-ui`, their roles, mappers and service accounts.
  - On `swagger-ui`, set `directAccessGrantsEnabled: true`. This is dev only and is how the smoke script gets a token.
  - Keep `registrationAllowed: true`.
  - Replace the YAS users with `admin` and `customer`, keeping the `service-account-*` users. Use `credentials: [{type: password, value: <dev password>, temporary: false}]`.
  - Strip YAS-specific secrets and set new local-dev client secrets.
- [ ] **Step 2: Add the `identity` service.**
  - Image `quay.io/keycloak/keycloak:26.x` (pinned).
  - `command: start-dev --import-realm`.
  - Mount `identity/realm-export.json` → `/opt/keycloak/data/import/realm-export.json`.
  - Environment:
    - `KC_DB=postgres`
    - `KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak` and DB credentials
    - `KC_HOSTNAME=http://identity.${DOMAIN}`
    - `KC_PROXY_HEADERS=xforwarded`
    - `KC_HTTP_ENABLED=true`
    - `KC_HEALTH_ENABLED=true`
    - `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD` from `.env`
  - `depends_on: postgres: condition: service_healthy`.
  - Healthcheck on management port 9000 `/health/ready`. The image has no curl, so use a bash `/dev/tcp` request.
- [ ] **Step 3: Verify the import before nginx exists.**
  - Run `docker compose up -d identity`.
  - Wait for `healthy`.
  - Run `docker compose exec identity /opt/keycloak/bin/kcadm.sh get realms/shopapp/roles --server http://localhost:8080 --realm master --user admin --password admin`.
  - Expected: the JSON lists `ADMIN`, `CUSTOMER` and `GUEST`.
- [ ] **Step 4: Verify the restart path.** Run `docker compose restart identity`. Expected: it becomes healthy again, and the log shows the realm import skipped (realm exists) and no error.
- [ ] **Step 5: Commit** `infra: keycloak with shopapp realm`.

---

### Task 9: Kafka (KRaft) and Kafka UI

**Files:**
- Modify: `docker-compose.yml` (add `kafka`, `kafka-ui`), `.env` (add `KAFKA_HOST_PORT=29092`)

**Interfaces:**
- Produces: in-network bootstrap `kafka:9092`, host bootstrap `localhost:29092`, service `kafka-ui` (in-network `kafka-ui:8080`).

- [ ] **Step 1: Add the `kafka` service.**
  - Image `apache/kafka:4.x` (pinned).
  - Single node:
    - `KAFKA_NODE_ID=1`
    - `KAFKA_PROCESS_ROLES=broker,controller`
    - `KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093`
    - Listeners `PLAINTEXT://:9092,CONTROLLER://:9093,PLAINTEXT_HOST://:29092`
    - Advertised `PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:${KAFKA_HOST_PORT}`
    - Replication factors 1 for the offsets and transaction topics
  - Named volume `kafka-data`.
  - Healthcheck: `/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092`.
- [ ] **Step 2: Add the `kafka-ui` service.**
  - Image `ghcr.io/kafbat/kafka-ui` (pinned). It's the maintained fork of provectuslabs/kafka-ui, which YAS uses.
  - `KAFKA_CLUSTERS_0_NAME=shopapp`, `KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=kafka:9092`, `DYNAMIC_CONFIG_ENABLED=true`.
  - `depends_on: kafka: condition: service_healthy`.
- [ ] **Step 3: Verify.**
  - `docker compose up -d kafka kafka-ui`
  - `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic smoke --partitions 1 --replication-factor 1`
  - `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list`

  Expected: `smoke` is listed. Then delete the topic.
- [ ] **Step 4: Commit** `infra: kafka in kraft mode and kafka-ui`.

---

### Task 10: nginx entry point, smoke script, README and NOTICE

**Files:**
- Create: `nginx/templates/default.conf.template`, `scripts/verify-foundation.sh`, `README.md`, `NOTICE`
- Modify: `docker-compose.yml` (add `nginx`)

**Interfaces:**
- Consumes: the services `identity:8080`, `pgadmin:80` and `kafka-ui:8080` from Tasks 7–9, and the `.env` keys.
- Produces: the hosts `identity.${DOMAIN}`, `api.${DOMAIN}`, `pgadmin.${DOMAIN}` and `kafka.${DOMAIN}` on `${NGINX_HTTP_PORT}`.

- [ ] **Step 1: Write `nginx/templates/default.conf.template`.**
  - One `server` block per hostname, using `${DOMAIN}`, which the official nginx image's envsubst templating substitutes.
  - `proxy_pass` to the upstreams above.
  - Set `Host`, `X-Forwarded-For`, `X-Forwarded-Proto` and `X-Forwarded-Host`.
  - `proxy_buffer_size 128k` and `proxy_buffers 4 256k` (Keycloak's large headers).
  - `api.${DOMAIN}` returns `404` with body `{"detail":"no services yet"}`.
  - Add a default server that returns 444.
- [ ] **Step 2: Add the `nginx` service.**
  - Image `nginx:1.x` (pinned).
  - Port `${NGINX_HTTP_PORT}:80`.
  - Mount the templates to `/etc/nginx/templates`.
  - Environment `DOMAIN=${DOMAIN}`.
  - `depends_on` identity, pgadmin and kafka-ui with `service_healthy` (pgadmin uses `service_started` if it has no healthcheck).
  - Network aliases `identity.${DOMAIN}` and `api.${DOMAIN}` on `shopapp-network`, so containers resolve the public issuer host to nginx.
- [ ] **Step 3: Write `scripts/verify-foundation.sh`.** Bash with `set -euo pipefail`. It uses curl, plus `python -c` for JSON (jq isn't installed on the dev machine). It reads `.env` and checks, printing `PASS`/`FAIL <reason>` per check and exiting non-zero on any failure:
  1. **No CRLF** in tracked files matching `*.sh *.sql *.conf *.template *.json .env` (`git ls-files` + `grep -l $'\r'`).
  2. **Hostnames resolve to 127.0.0.1.** Check each of the four (`python -c "import socket;print(socket.gethostbyname(h))"`). On failure, print `add to C:\Windows\System32\drivers\etc\hosts: 127.0.0.1 identity.shopapp.local api.shopapp.local pgadmin.shopapp.local kafka.shopapp.local`.
  3. **All compose services healthy or running** (`docker compose ps --format json`).
  4. **Token:**
     - Password grant for `customer`/`customer` on client `swagger-ui` at `http://identity.${DOMAIN}:${NGINX_HTTP_PORT}/realms/shopapp/protocol/openid-connect/token` returns 200.
     - The decoded payload has `iss == "http://identity.${DOMAIN}/realms/shopapp"`.
     - `realm_access.roles` contains `CUSTOMER`.
  5. **In-network issuer:** `docker compose exec kafka-ui wget -qO- http://identity.${DOMAIN}/realms/shopapp/.well-known/openid-configuration` (or another container with an HTTP client) contains the same issuer.
  6. **UIs reachable:** `http://kafka.${DOMAIN}` returns 200 and `http://pgadmin.${DOMAIN}/misc/ping` returns 200.
  7. **API placeholder:** `http://api.${DOMAIN}/` returns 404.
  8. **Database count:** Postgres has the `keycloak` database plus every service database from `postgres_init.sql`.
  9. **Restart** (only when run with `--restart`): `docker compose down && docker compose up -d`, wait for healthy, re-run checks 3–4.

  Print the port in use at the start: `Using nginx port ${NGINX_HTTP_PORT}`.
- [ ] **Step 4: Run the full check.** Run `docker compose up -d && bash scripts/verify-foundation.sh --restart`. Expected: every check `PASS`, exit 0. Fix configuration until it is.
- [ ] **Step 5: Write `README.md`.**
  - What shop_app is (a YAS rebuild) and the slice roadmap (copy the list from the spec).
  - Prerequisites:
    - JDK 27 and how Maven finds it
    - Docker
    - Python, used by the smoke script
    - the hosts line
  - Commands: `mvn verify`, `docker compose up -d`, `bash scripts/verify-foundation.sh`, `docker compose down -v`.
  - Notes:
    - The init SQL only runs on an empty volume.
    - How to change `NGINX_HTTP_PORT` if port 80 is taken.
  - A URL and dev-credentials table, marked "local development only".
  - **Build notes:** the Lombok/MapStruct/JaCoCo outcome from Task 1, and the Spring Boot and Spring Cloud versions in use.
- [ ] **Step 6: Write `NOTICE`.** Credit YAS (`https://github.com/nashtech-garage/yas`, MIT, reference commit `179e813568c345bad3fce985088b5535e57481aa`) and include the YAS copyright line from its LICENSE.
- [ ] **Step 7: Run the final check.** Run `mvn -B verify && bash scripts/verify-foundation.sh`. Expected: BUILD SUCCESS and all checks PASS.
- [ ] **Step 8: Commit** `infra: nginx entry point, smoke script, README and NOTICE`.
