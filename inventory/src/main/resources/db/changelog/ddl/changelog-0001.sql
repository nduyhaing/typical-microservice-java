--liquibase formatted sql

--changeset shopapp:inventory-0001
-- YAS issue-483-2 / issue-629-1 in their final shape, with plain bigint columns (YAS: bigserial ids, quantities and
-- address id), not-null quantities that never go below 0, one stock row per product and warehouse, a unique warehouse
-- name ignoring case, and timestamptz audit columns (decisions.md 3c-20).
create table warehouse
(
    id               bigserial    not null,
    name             varchar(450) not null,
    address_id       bigint       not null,
    created_by       varchar(255),
    created_on       timestamp(6) with time zone,
    last_modified_by varchar(255),
    last_modified_on timestamp(6) with time zone,
    constraint warehouse_pkey primary key (id)
);

create unique index warehouse_name_key on warehouse (lower(name));

create table stock
(
    id                bigserial not null,
    product_id        bigint    not null,
    warehouse_id      bigint    not null,
    quantity          bigint    not null default 0,
    reserved_quantity bigint    not null default 0,
    created_by        varchar(255),
    created_on        timestamp(6) with time zone,
    last_modified_by  varchar(255),
    last_modified_on  timestamp(6) with time zone,
    constraint stock_pkey primary key (id),
    constraint stock_warehouse_fk foreign key (warehouse_id) references warehouse (id),
    constraint stock_product_warehouse_key unique (product_id, warehouse_id),
    constraint stock_quantity_check check (quantity >= 0 and reserved_quantity >= 0)
);

create index stock_warehouse_idx on stock (warehouse_id);

create table stock_history
(
    id                bigserial not null,
    product_id        bigint    not null,
    warehouse_id      bigint    not null,
    adjusted_quantity bigint    not null,
    note              varchar(450),
    created_by        varchar(255),
    created_on        timestamp(6) with time zone,
    last_modified_by  varchar(255),
    last_modified_on  timestamp(6) with time zone,
    constraint stock_history_pkey primary key (id),
    constraint stock_history_warehouse_fk foreign key (warehouse_id) references warehouse (id)
);

create index stock_history_product_warehouse_idx on stock_history (product_id, warehouse_id);
