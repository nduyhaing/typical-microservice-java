--liquibase formatted sql

--changeset shopapp:product-0013-stock-adjustment
-- References of inventory's stock adjustments already applied by adjust-quantity, so a retried call is applied once
-- (decisions.md 3c-60).
create table stock_adjustment
(
    reference  varchar(64)                 not null,
    created_on timestamp(6) with time zone not null,
    constraint stock_adjustment_pkey primary key (reference)
);
