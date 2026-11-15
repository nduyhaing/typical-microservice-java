# shop_app Slice 5 (Payment, delivery, webhooks, recommendation, sample data) Implementation Plan

> Executed by implementer subagents (P13). Global Constraints = those of
> `docs/superpowers/plans/2026-11-14-shop-app-slice3a-backoffice.md` plus the common agent rules; decisions in a new
> `### 5` section with ids 5-N. Docker is memory-constrained: one agent at a time on Docker (coordinator says
> "docker go").

**Spec:** `docs/superpowers/specs/2026-11-15-shop-app-slice5-payment-webhook-recommendation-design.md`

## Review Focus
1. Money: the paid amount always comes from the order; PayPal capture verified (amount, currency, order id) before
   PAID; double capture/replay can't pay twice or mark another order paid.
2. Order status machine: illegal transitions refused; cancel restores stock and coupon exactly once; customers act
   only on their own orders; internal endpoints unreachable from the edge.
3. Webhooks: SSRF (private/loopback/metadata addresses, redirects to them, DNS rebinding at send time), signature
   correct and secret not leaked, slow/failing receivers can't stall the consumer.
4. Recommendation: memory within budget; unpublished products never recommended; service down → product page fine.
5. Sample data: admin only, idempotent, never touches orders/customers.

### Task 1: order lifecycle — status machine, admin order endpoints, cancel with stock/coupon compensation, rating
eligibility narrowed, order CDC connector; back-office orders pages; storefront "cancel order" on my orders. (5-1..9)
### Task 2: payment service + COD flow + payment providers admin; storefront pay step after placing an order. (5-10..19)
### Task 3: payment-paypal (sandbox, stubbed ITs), storefront PayPal button when enabled. (5-20..29)
### Task 4: webhook service (CDC consumers, signed delivery, SSRF guard, log) + back-office webhooks pages. (5-30..39)
### Task 5: recommendation (local embeddings or fallback, pgvector) + storefront similar products. (5-40..49)
### Task 6: sampledata service + back-office button. (5-50..54)
### Task 7: smoke, README, decisions, definition of done. (5-60..)

Order: 1 first (2–4 depend on order statuses/CDC); 2 → 3; 4 after 1; 5 and 6 independent. Code may be written in
parallel; Docker use is serialized.
