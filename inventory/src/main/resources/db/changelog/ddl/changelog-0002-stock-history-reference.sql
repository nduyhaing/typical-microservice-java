--liquibase formatted sql

--changeset shopapp:inventory-0002-stock-history-reference
-- The reference under which an adjustment's delta was sent to product adjust-quantity (decisions.md 3c-60); rows
-- written before the 3c review have none.
alter table stock_history add column reference varchar(64);
