--liquibase formatted sql

--changeset shopapp:order-customer-id-varchar
-- YAS changeset 0016 turned checkout.customer_id into VARCHAR for Keycloak user ids but left order.customer_id a
-- BIGSERIAL (with a sequence default). shop_app stores the Keycloak user id on orders to list "my orders"
-- (decisions.md 2c-6).
alter table "order" alter column customer_id drop default;
alter table "order" alter column customer_id type varchar(255) using customer_id::varchar;
