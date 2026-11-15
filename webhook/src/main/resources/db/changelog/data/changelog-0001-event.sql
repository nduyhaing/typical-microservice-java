--liquibase formatted sql

--changeset shopapp:webhook-data-0001
-- YAS issue-001
INSERT INTO event(name, description) VALUES ('ON_PRODUCT_UPDATED', 'The product is updated.');
INSERT INTO event(name, description) VALUES ('ON_ORDER_CREATED', 'The order is created.');
INSERT INTO event(name, description) VALUES ('ON_ORDER_STATUS_UPDATED', 'The order status is updated.');
