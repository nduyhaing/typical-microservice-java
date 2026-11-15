--liquibase formatted sql

--changeset shopapp:webhook-0001
-- YAS issue-002/003 with: timestamps with time zone, is_active not null, a unique event name, one subscription per
-- webhook and event, subscriptions and deliveries removed with their webhook (YAS: the FK refused the delete).
create table webhook
(
    id               bigserial     not null,
    payload_url      varchar(2048) not null,
    content_type     varchar(128)  not null,
    secret           varchar(255)  not null,
    is_active        boolean       not null default true,
    created_by       varchar(255),
    created_on       timestamp(6) with time zone,
    last_modified_by varchar(255),
    last_modified_on timestamp(6) with time zone,
    constraint webhook_pkey primary key (id)
);

create table event
(
    id          bigserial   not null,
    name        varchar(64) not null,
    description text,
    constraint event_pkey primary key (id),
    constraint event_name_key unique (name)
);

create table webhook_event
(
    id         bigserial not null,
    webhook_id bigint    not null,
    event_id   bigint    not null,
    constraint webhook_event_pkey primary key (id),
    constraint webhook_event_webhook_fk foreign key (webhook_id) references webhook (id) on delete cascade,
    constraint webhook_event_event_fk foreign key (event_id) references event (id),
    constraint webhook_event_key unique (webhook_id, event_id)
);

-- The delivery log and the retry queue in one (decisions.md 5-34). YAS: webhook_event_id + payload jsonb + status.
-- payload is text: the exact bytes that are signed and sent (jsonb would reorder keys).
-- source_key (topic-partition-offset of the CDC message) makes a redelivered message enqueue nothing twice.
create table webhook_event_notification
(
    id               bigserial    not null,
    webhook_id       bigint       not null,
    event_name       varchar(64)  not null,
    delivery_id      uuid         not null,
    source_key       varchar(255) not null,
    payload          text         not null,
    payload_sha256   varchar(64)  not null,
    status           varchar(32)  not null,
    attempts         integer      not null default 0,
    response_code    integer,
    last_error       varchar(1000),
    created_on       timestamp(6) with time zone not null,
    next_attempt_on  timestamp(6) with time zone,
    last_attempt_on  timestamp(6) with time zone,
    delivered_on     timestamp(6) with time zone,
    constraint webhook_event_notification_pkey primary key (id),
    constraint webhook_event_notification_webhook_fk foreign key (webhook_id) references webhook (id)
        on delete cascade,
    constraint webhook_event_notification_delivery_key unique (delivery_id),
    constraint webhook_event_notification_source_key unique (webhook_id, source_key)
);

create index webhook_event_notification_due_idx on webhook_event_notification (status, next_attempt_on);
create index webhook_event_notification_webhook_idx on webhook_event_notification (webhook_id, id desc);

-- The last order status webhook saw per order: tells a status change from another update when Debezium sends no
-- before image (default replica identity), decisions.md 5-35.
create table order_status_seen
(
    order_id   bigint      not null,
    status     varchar(64),
    updated_on timestamp(6) with time zone not null,
    constraint order_status_seen_pkey primary key (order_id)
);
