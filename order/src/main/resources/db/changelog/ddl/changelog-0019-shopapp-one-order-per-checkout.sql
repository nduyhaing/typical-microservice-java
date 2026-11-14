--liquibase formatted sql

--changeset shopapp:one-order-per-checkout
-- One checkout produces at most one order, even when "Place order" is sent twice at the same time
-- (decisions.md 2c-8). The service also locks the checkout row; this index is the guarantee.
create unique index if not exists uk_order_checkout_id on "order" (checkout_id);
