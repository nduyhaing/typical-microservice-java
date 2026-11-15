# shop_app Slice 3c (Location, tax, inventory) Implementation Plan

> Executed by implementer subagents (P13). Global Constraints = those of
> `docs/superpowers/plans/2026-11-14-shop-app-slice3a-backoffice.md`; decisions rows `### 3c` ids 3c-N (each task
> gets its own id range given in its brief). New services use the `cart` module as the template.

**Spec:** `docs/superpowers/specs/2026-11-15-shop-app-slice3c-inventory-tax-location-design.md`

## Review Focus
1. Address ownership: one customer reading/using another's saved address (customer service, order placement) → 403/404.
2. Tax: rate resolution (zip > state > country > 0), money as BigDecimal end to end, browser never computes tax.
3. Stock: concurrent orders for the last units never oversell (atomic subtract); failed order gives stock back;
   untracked products unaffected.
4. Admin-only writes on location/tax/inventory; storefront lookups public where YAS has them public.
5. Any service down (location/tax/inventory/product) → clear 5xx, nothing half-saved; storefront degrades.

### Task 1: location service (+ storefront/admin endpoints, seed countries) — new module, compose, nginx `/location/`.
### Task 2: tax service — new module, compose, nginx `/tax/`.
### Task 3: inventory service + product update/subtract-quantity — new module, compose, nginx `/inventory/`.
### Task 4: customer saved addresses (customer ↔ location) + storefront address book page.
### Task 5: order integration (BigDecimal migration, tax at order, stock subtract + compensation, saved address) +
storefront checkout address picker, tax summary, stock display; cart stock check.
### Task 6: back-office pages: countries, states, tax classes, tax rates, warehouses, stock + history, product tax class.
### Task 7: smoke, README, decisions, definition of done.

Tasks 1–3 are independent (different modules) and may run in parallel; 4 needs 1; 5 needs 1–4; 6 needs 1–3.
