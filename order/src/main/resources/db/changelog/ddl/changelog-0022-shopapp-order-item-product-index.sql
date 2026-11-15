--liquibase formatted sql

--changeset shopapp:order-item-product-index
-- "Did this customer buy the product?" (rating eligibility, decisions.md 4-30) looks order lines up by product and
-- orders by customer.
create index if not exists idx_order_item_product_id on order_item (product_id);
create index if not exists idx_order_customer_id on "order" (customer_id);
