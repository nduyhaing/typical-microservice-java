# shop_app Slice 4 (CDC, search, rating, promotion) Implementation Plan

> Executed by implementer subagents (P13). Global Constraints = those of
> `docs/superpowers/plans/2026-11-14-shop-app-slice3a-backoffice.md`; decisions in a new `### 4` section with ids
> 4-N (each task gets its own id range in its brief). New services use the newest modules (`tax`, `location`,
> `inventory`) as the template.

**Spec:** `docs/superpowers/specs/2026-11-15-shop-app-slice4-search-rating-promotion-design.md`

## Review Focus
1. CDC robustness: connector registration idempotent across restarts; search consumer idempotent and ordered per
   product; poison messages don't block the partition; deletes/unpublish remove from the index; initial snapshot
   fills an empty index.
2. Search never returns unpublished/hidden products; query params bounded (size, page, price) and safe (no query
   injection through keyword/attribute).
3. Rating eligibility: only buyers rate; one per product; another user's rating can't be edited/deleted except by
   ADMIN; product page works when rating is down.
4. Promotion: verify and usage are server-side; usage limit never exceeded under concurrency; dates/active/min
   amount enforced; discount never makes a line or total negative; tax after discount consistent between preview
   and order; failed order releases usage.
5. New infrastructure (Connect, Elasticsearch) down → the rest of the shop keeps working; smoke reports it clearly.

### Task 1: CDC — Kafka Connect + Debezium in compose, replication user, idempotent connector registration, smoke
check that a product change lands on the topic. (decisions 4-1..9)
### Task 2: search service — module, Elasticsearch in compose, Kafka consumer (retry + DLT), endpoints, product ES
detail endpoint if needed, nginx `/search/`. (4-10..19)
### Task 3: storefront search — header search box with suggestions, `/search` results page with facets/sort/paging.
(4-20..24)
### Task 4: rating service + order `completed?productId` endpoint + storefront reviews section + back-office reviews
page and dashboard latest ratings. (4-30..39)
### Task 5: promotion service + back-office promotions pages. (4-40..49)
### Task 6: order + storefront checkout coupon (verify, discount on eligible lines, tax after discount, usage with
compensation). (4-50..59)
### Task 7: smoke, README, decisions, definition of done. (4-60..)

Task 1 → 2 → 3 in sequence; 4 and 5 are independent of 1–3 and of each other (4 touches order only for the new
endpoint — coordinate with 6); 6 needs 5; 7 needs all.
