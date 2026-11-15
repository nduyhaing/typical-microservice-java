--liquibase formatted sql

--changeset shopapp:order-money-numeric
-- Money is exact from slice 3c on (decisions.md 3c-40): YAS kept the order's total tax and discount as float(53).
-- Existing values are rounded half-up to cents. Tax percentages get the tax service's precision (numeric(7,4),
-- 3c-10) instead of two decimals. Checkout lines keep the product's tax class, priced at order time with the address.
alter table "order"
    alter column total_tax type numeric(19,2) using round(total_tax::numeric, 2),
    alter column total_discount_amount type numeric(19,2) using round(total_discount_amount::numeric, 2);
alter table order_item alter column tax_percent type numeric(7,4);
alter table checkout_item alter column tax_percent type numeric(7,4);
alter table checkout_item add column tax_class_id bigint;
