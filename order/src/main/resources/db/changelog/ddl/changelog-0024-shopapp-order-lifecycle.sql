--liquibase formatted sql

--changeset shopapp:order-lifecycle
-- Order lifecycle (decisions.md 5-2): the stock reference order sent to product subtract-quantity when the order was
-- placed (3c-45; before slice 5 it was not kept), so a cancellation can give the units back; when the order was
-- asked to give its stock and coupon use back (cancelled / rejected / refunded before shipping) and when each of the
-- two was actually given back, so a retry never gives them back twice and a failed call is retried later.
alter table "order" add column if not exists stock_reference varchar(64);
alter table "order" add column if not exists goods_back_requested_on timestamp with time zone;
alter table "order" add column if not exists stock_restored_on timestamp with time zone;
alter table "order" add column if not exists promotion_usage_released_on timestamp with time zone;
-- admin list: newest first, filtered by status
create index if not exists idx_order_created_on on "order" (created_on);
create index if not exists idx_order_status on "order" (status);
-- the compensation sweep looks only at orders still owing something
create index if not exists idx_order_goods_back_pending on "order" (id)
    where goods_back_requested_on is not null and (stock_restored_on is null or promotion_usage_released_on is null);
