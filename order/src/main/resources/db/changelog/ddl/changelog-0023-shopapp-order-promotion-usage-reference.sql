--liquibase formatted sql

--changeset shopapp:order-promotion-usage-reference
-- The coupon use promotion recorded for the order (decisions.md 4-53): the reference order sent to apply-usage, so
-- a later cancellation can give the use back with release-usage. The coupon code (promotion_code), the order discount
-- (total_discount_amount, numeric(19,2)) and the line discount (order_item.discount_amount, decimal(19,2)) already
-- exist from YAS and 3c-40.
alter table "order" add column if not exists promotion_usage_reference uuid;
