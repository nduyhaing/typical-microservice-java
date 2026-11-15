--liquibase formatted sql

--changeset shopapp:order-payment-in-progress
-- Slice 5 review (decisions.md 5-60, 5-62): the payment service marks an order "payment in progress" just before it
-- captures money at PayPal; until the marker expires neither the customer nor an admin can cancel or reject the order
-- (a captured payment could otherwise land on a cancelled order). Cleared when the order is marked paid.
alter table "order" add column if not exists payment_in_progress_until timestamp with time zone;
-- goods-back sweep (5-65): attempts and the last attempt, so an order that keeps failing is retried with a back-off
-- and cannot starve the others (the sweep orders by the last attempt, never-tried first)
alter table "order" add column if not exists goods_back_attempts integer not null default 0;
alter table "order" add column if not exists goods_back_last_attempt_on timestamp with time zone;
