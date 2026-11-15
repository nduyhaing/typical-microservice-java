# shop_app Slice 3b (Media) Implementation Plan

> Executed by implementer subagents (P13). Global Constraints = those of
> `docs/superpowers/plans/2026-11-14-shop-app-slice3a-backoffice.md` (same tooling, helpers, commit/push rules,
> Docker/nginx conventions, decisions rows — here ids 3b-N).

**Spec:** `docs/superpowers/specs/2026-11-15-shop-app-slice3b-media-design.md`

## Review Focus

1. Upload abuse: non-image content with an image extension, oversized files, path traversal in file names/URLs,
   customer or anonymous upload/delete → 400/403/401, never stored or served.
2. Media service down or slow → product pages still render (placeholder) within the timeouts.
3. Image URLs work from the browser (public host), not only in-network.
4. Product update keeps images unless the admin removes them (3a-19 replaces lists: the form must send current ids).
5. Large lists don't make one media call per product (or the page sizes bound it — recorded).

### Task 1: media service
New module from the cart template + YAS media sources (`gh api ... media/...`): ddl, entity, repository,
FileSystemRepository with traversal guard, content-based type validation, endpoints per spec, security (POST/DELETE
ADMIN, GETs public), ITs per spec DoD 1 (use a temp directory for files). Dockerfile, compose `media` (+ volume
`media-data` mounted at /images, env DB + KEYCLOAK_URL + `SHOPAPP_PUBLIC_URL_MEDIA=http://api.${DOMAIN}${port}/media`),
nginx `/media/` location + `client_max_body_size 10m`. Live: admin token upload of a small png → GET file 200
image/png via http://api.shopapp.local/media/... Commit, push.

### Task 2: product + apps integration
Product `MediaService` real calls with timeouts and fallback (ITs with a JDK HttpServer stub), compose
`SHOPAPP_SERVICES_MEDIA`; back-office upload UI (product thumbnail + gallery, category image) with tests; storefront
gallery + variation thumbnail with tests; rebuild containers. Commit, push.

### Task 3: smoke, README, decisions, definition of done
Smoke per spec DoD 3 (upload via backoffice-bff with cookie jar + Origin header, attach to a seeded product, check
storefront HTML and file URL), full builds/tests, compose `--build --wait`, smoke `--restart`. Commit, push.
