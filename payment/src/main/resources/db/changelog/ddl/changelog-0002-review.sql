--liquibase formatted sql

--changeset shopapp:payment-0002-review
-- Slice 5 review (decisions.md 5-60, 5-61, 5-67):
-- version: optimistic locking of every JPA update (a stale copy can no longer overwrite a newer state);
-- paypal_request_id: the payment's own random PayPal-Request-Id for creating its PayPal order (was "create-payment-<id>",
-- which a database reset would reuse);
-- order_refused / order_confirm_attempts / next_order_confirm_on: the reconciler retries telling order about a
-- completed payment it has not accepted yet, with a back-off; a refusal stops the retries and flags it for a refund.
alter table payment add column version bigint not null default 0;
alter table payment add column paypal_request_id uuid;
alter table payment add column order_refused boolean not null default false;
alter table payment add column order_confirm_attempts integer not null default 0;
alter table payment add column next_order_confirm_on timestamp(6) with time zone;
create index payment_unconfirmed_idx on payment (next_order_confirm_on)
    where payment_status = 'COMPLETED' and not order_confirmed and not order_refused;
