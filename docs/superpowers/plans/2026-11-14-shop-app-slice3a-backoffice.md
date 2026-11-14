# shop_app Slice 3a (Back office foundation + catalog admin) Implementation Plan

> Executed by implementer subagents, one task each (decisions.md P13). Steps use `- [ ]`.

**Spec:** `docs/superpowers/specs/2026-11-14-shop-app-slice3a-backoffice-design.md`

## Global Constraints (every task)

- Java 27 (`JAVA_HOME=~/.jdks/openjdk-27`), Spring Boot 4.1.2, Spring Cloud 2025.1.4 (verifier off in BFFs), package
  `com.shopapp.*`, Checkstyle fails on warnings (run `python <scratchpad>/fix_imports.py <module>/src` after porting
  YAS files — YAS violates its own import order), Lombok/MapStruct available.
- Maven: `bash <scratchpad>/mvnc <args>` (runs mvn with JDK 27 + Maven Central settings; prints a summary; full log
  in `<scratchpad>/last-build.log`). `<scratchpad>` = `C:/Users/dhn/AppData/Local/Temp/claude/d04713e85aa46c39/4b9fc58f-fb83-4707-83bb-d8cb8285943a/scratchpad`.
  Install changed upstream modules (`-pl common-library -am install -DskipTests`) before `-pl <module>` runs.
- YAS sources at the pinned commit are downloaded under `<scratchpad>/yas-product`, `yas-cart`, `yas-order`; any other
  file: `gh api "repos/nashtech-garage/yas/contents/<path>?ref=179e813568c345bad3fce985088b5535e57481aa" --jq .content | base64 -d`.
- TDD: failing test first, watch it fail, implement, watch it pass, then the module's full `verify`.
- Write Java/TS files with the file-writing tool, not bash heredocs containing quotes (Git Bash breaks on them).
- Commit to `master` (author already `nduyhai@gmail.com`), message ends with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, then push: `bash <scratchpad>/gpush`.
- Every deviation from YAS or this plan → a row in `decisions.md` (section "3a", ids 3a-N) with why and cost.
- Docker: Java containers `eclipse-temurin:27-jre`, healthcheck `["CMD","bash","-c", "...\/dev/tcp..."]`; nginx
  upstreams via `set $upstream_x host:port; proxy_pass http://$upstream_x;` and blank `Forwarded`/`X-Forwarded-Prefix`.
  Git Bash + docker exec with absolute container paths needs `MSYS_NO_PATHCONV=1`.
- Existing behaviour must stay green: `bash scripts/verify-foundation.sh` passes before and after.

## Review Focus

1. Non-admin (customer token / customer session) on any admin endpoint or the back office → 403, never data.
2. Duplicate slug/sku/gtin, deleting a brand/category in use, a category as its own parent → 400 with message.
3. Product with options created/edited in admin → storefront detail shows options and variation prices; unpublish →
   gone from storefront lists and detail (2a-8, 2a-15 still hold).
4. Admin session expired → 401 JSON from the BFF (2b-13), cross-site writes refused (2b-7).
5. Paging/search edge input on admin lists → 400 not 500.

---

### Task 1: product admin — brands and categories
Port YAS brand/category backoffice endpoints + services + VMs (see spec). Tests (`BrandAdminIT`, `CategoryAdminIT`
on `AbstractStorefrontIT` with `jwt().authorities(ROLE_ADMIN)`): CRUD happy paths, customer role → 403, anonymous →
401, duplicate name/slug → 400, delete brand/category in use → 400, category parent = itself → 400, category with
children delete → 400. Commit, push.

### Task 2: product admin — products, options, variations
Port YAS product backoffice list/get/create/update/delete, `product-variations`, `product-options` list/create.
Tests (`ProductAdminIT`): list with name filter + paging bounds; create simple product → storefront detail 200;
create product with option Color + 2 variations → `/storefront/product-variations/{id}` returns 2 with prices and
`/storefront/product-option-combinations/{id}/values` returns both values; duplicate slug/sku → 400; update price →
storefront shows it; delete → storefront 404; customer → 403. Commit, push.

### Task 3: backoffice-bff
New module (copy storefront-bff, adapt per spec): ADMIN required everywhere except health; tests: anonymous →
redirect to login, logged-in non-admin (`mockOidcLogin` without ROLE_ADMIN) → 403, admin → `/authentication` ok,
routes + TokenRelay, expired-token 401, cross-site 403. Dockerfile, compose (`backoffice-bff`, depends redis +
identity), nginx `backoffice.${DOMAIN}`, `.env` secret, realm check/script for redirect URIs + roles. Verify live:
`/oauth2/authorization/keycloak` on backoffice host redirects with `client_id=backoffice-bff`. Commit, push.

### Task 4: backoffice Next.js app
New app `backoffice/` (copy storefront tooling: package.json versions, tsconfig, eslint, vitest, Dockerfile, api
client). Pages per spec with tests (lists render, create/edit forms submit the YAS view-model shapes, delete
confirm, variation editor builds `variations` + `productOptionValues`, error/403 states). Compose `backoffice`
service, smoke-ready. lint/typecheck/build. Commit, push.

### Task 5: smoke, README, decisions, definition of done
Smoke: `scripts/lib/storefront-login.sh` generalised or a sibling for the back office: admin login → create brand via
`/api/product/backoffice/brands` → list contains it → delete; customer login → 403. `mvn verify`, both apps'
tests/build, compose `--build --wait`, smoke `--restart` all PASS; README section for the back office. Commit, push.
