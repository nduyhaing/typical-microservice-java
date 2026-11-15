# shop_app — Slice 3b: Media (product images) — Design

Date: 2026-11-15 · Status: written for automatic implementation (P9) · YAS @ `179e8135`.

## Intent

Products get real images. An admin uploads a thumbnail and gallery images for a product (and an image for a
category) in the back office; the storefront shows them in product cards, the product page (thumbnail +
gallery, variation thumbnail when a variation is selected) and categories. The placeholder stays for products
without images.

## Design

### media service (new module `media`, port of YAS `media`)

- Postgres DB `media` (exists since slice 1); Liquibase ddl from YAS; entity `Media` (id, caption, fileName,
  filePath, mediaType); files on a Docker volume (`file.directory=/images/`, compose volume `media-data`).
- Endpoints (YAS, context path `/media`):
  - `POST /medias` (multipart: `multipartFile`, `caption`, `fileNameOverride`) → `NoFileMediaVm` — **ADMIN only**
    (YAS: authenticated); allowed types jpeg/png/gif (YAS `ValidFileType`, content checked by reading the image, not
    trusting the extension); max size 10 MB (Spring multipart limit) → 400 above it.
  - `GET /medias/{id}` → `MediaVm` with `url` = public file URL — **public**.
  - `GET /medias/{id}/file/{fileName}` → the file bytes with its content type — **public**, cacheable
    (`Cache-Control: public, max-age=86400`), `X-Content-Type-Options: nosniff`, never serves anything outside the
    media directory (path traversal guard), 404 when `fileName` doesn't match the stored one.
  - `GET /medias?ids=` → list (used by product), `DELETE /medias/{id}` — ADMIN.
- Public URL base from config: `shopapp.public-url.media=http://api.${DOMAIN}/media` so URLs work from the browser.

### product (integration)

- `MediaService` calls the real media service (`shopapp.services.media` in-network) with connect/read timeouts
  (closes the 2a deferred "MediaService needs timeouts") and keeps the empty-URL fallback on errors (2a-11).
- Back-office create/update already accept `thumbnailMediaId`, `productImageIds`, category `imageId`: keep them;
  admin detail returns their URLs.
- Avoid N calls per list: resolve thumbnails for a page with one `GET /medias?ids=` call (YAS does one per product;
  bounded page sizes keep it acceptable, but batch where easy — builder's call, record it).

### storefront / backoffice

- Storefront: `next/image` stays `unoptimized`; image URLs are absolute (`http://api.shopapp.local/media/...`).
  Product page gallery (thumbnails under the main image, click to switch); variation thumbnail when selected.
- Back office: upload control on the product form (thumbnail + multiple gallery images) and category form; uploads go
  `POST /api/media/medias` (multipart through backoffice-bff, admin token relayed); preview; remove image.
- nginx: `client_max_body_size 10m` for api and backoffice hosts.

### Definition of done

1. `mvn verify`: media ITs (upload png as admin → 201 + metadata; customer → 403; anonymous → 401; text file renamed
   `.png` → 400; > 10 MB → 400; get metadata/file public with correct content type; wrong fileName → 404; path
   traversal attempt → 404/400); product ITs: thumbnail URL from a stubbed media service, media down → empty URL
   quickly.
2. App tests: back-office upload builds multipart and stores returned id in the form; storefront gallery switching.
3. Compose healthy incl. media + volume; smoke: admin uploads an image via backoffice-bff, sets it as a product
   thumbnail, storefront product page HTML contains the media URL and the file URL returns 200 image/png.
4. README + decisions 3b-*.
