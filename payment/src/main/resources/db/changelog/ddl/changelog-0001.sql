--liquibase formatted sql

--changeset shopapp:payment-0001
-- YAS issue-440-2, #1171-1 and later changesets in one: money as numeric(19,2), audit columns timestamptz,
-- payment_provider.enabled (YAS renamed is_enabled), plus shop_app columns: the order's owner (customer_id),
-- payment_provider_checkout_id (PayPal order id) and gateway_transaction_id (capture id) unique, so a replayed
-- capture can never be recorded twice, and order_confirmed (the order service accepted the payment).
create table payment_provider
(
    id                          varchar(32)  not null,
    enabled                     boolean      not null default false,
    name                        varchar(255) not null,
    configure_url               varchar(255),
    landing_view_component_name varchar(255),
    additional_settings         varchar(2000),
    media_id                    bigint,
    version                     integer      not null default 0,
    created_by                  varchar(255),
    created_on                  timestamp(6) with time zone,
    last_modified_by            varchar(255),
    last_modified_on            timestamp(6) with time zone,
    constraint payment_provider_pkey primary key (id)
);

create table payment
(
    id                           bigserial      not null,
    order_id                     bigint         not null,
    checkout_id                  varchar(255),
    customer_id                  varchar(255)   not null,
    amount                       numeric(19, 2) not null,
    payment_fee                  numeric(19, 2),
    payment_method               varchar(32)    not null,
    payment_status               varchar(32)    not null,
    gateway_transaction_id       varchar(255),
    payment_provider_checkout_id varchar(255),
    failure_message              varchar(1000),
    order_confirmed              boolean        not null default false,
    created_by                   varchar(255),
    created_on                   timestamp(6) with time zone,
    last_modified_by             varchar(255),
    last_modified_on             timestamp(6) with time zone,
    constraint payment_pkey primary key (id),
    constraint payment_gateway_transaction_id_key unique (gateway_transaction_id),
    constraint payment_provider_checkout_id_key unique (payment_provider_checkout_id)
);

create index payment_order_id_idx on payment (order_id);
create index payment_status_created_on_idx on payment (payment_status, created_on desc);
