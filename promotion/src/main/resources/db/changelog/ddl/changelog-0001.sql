--liquibase formatted sql

-- YAS promotion ddl 0001..0003 in one changeset, with its schema bugs fixed (decisions.md 4-40): money as
-- numeric(19,2) and the percentage as numeric(5,2) in (0, 100] instead of bigint, only the value of the chosen
-- discount type required, name 450 long as the entity, coupon code unique case-insensitively over all promotions
-- (YAS only checked active ones), dates required with end after start, usage count never above a limited usage limit,
-- bigint ids, exactly one target per promotion_apply row, and a usage reference for idempotent apply / release.

--changeset shopapp:promotion-0001
create table promotion (
    id bigserial primary key,
    name varchar(450) not null,
    slug varchar(450) not null,
    description varchar(1000),
    coupon_code varchar(100) not null,
    discount_type varchar(25) not null,
    discount_percentage numeric(5, 2),
    discount_amount numeric(19, 2),
    usage_type varchar(25) not null default 'UNLIMITED',
    usage_limit integer,
    usage_count integer not null default 0,
    apply_to varchar(25) not null,
    minimum_order_purchase_amount numeric(19, 2) not null default 0,
    is_active boolean not null,
    start_date timestamp with time zone not null,
    end_date timestamp with time zone not null,
    created_by varchar(255),
    created_on timestamp with time zone,
    last_modified_by varchar(255),
    last_modified_on timestamp with time zone,
    constraint ck_promotion_discount_type check (discount_type in ('PERCENTAGE', 'FIXED')),
    constraint ck_promotion_discount_value check (
        (discount_type = 'PERCENTAGE' and discount_percentage > 0 and discount_percentage <= 100)
        or (discount_type = 'FIXED' and discount_amount > 0)),
    constraint ck_promotion_usage_type check (usage_type in ('LIMITED', 'UNLIMITED')),
    constraint ck_promotion_usage_limit check (usage_type = 'UNLIMITED' or usage_limit > 0),
    constraint ck_promotion_usage_count check (
        usage_count >= 0 and (usage_type = 'UNLIMITED' or usage_count <= usage_limit)),
    constraint ck_promotion_apply_to check (apply_to in ('PRODUCT', 'CATEGORY', 'BRAND')),
    constraint ck_promotion_minimum_order check (minimum_order_purchase_amount >= 0),
    constraint ck_promotion_dates check (end_date > start_date)
);
create unique index ux_promotion_coupon_code on promotion (lower(coupon_code));
create unique index ux_promotion_slug on promotion (lower(slug));

create table promotion_apply (
    id bigserial primary key,
    promotion_id bigint not null references promotion (id) on delete cascade,
    product_id bigint,
    category_id bigint,
    brand_id bigint,
    created_by varchar(255),
    created_on timestamp with time zone,
    last_modified_by varchar(255),
    last_modified_on timestamp with time zone,
    constraint ck_promotion_apply_one_target check (num_nonnulls(product_id, category_id, brand_id) = 1)
);
create index ix_promotion_apply_promotion on promotion_apply (promotion_id);

create table promotion_usage (
    id bigserial primary key,
    promotion_id bigint not null references promotion (id),
    reference uuid not null,
    user_id varchar(255) not null,
    order_id bigint,
    product_id bigint,
    released_on timestamp with time zone,
    created_by varchar(255) default 'system',
    created_on timestamp with time zone default current_timestamp,
    last_modified_by varchar(255),
    last_modified_on timestamp with time zone,
    constraint ux_promotion_usage_reference unique (reference)
);
create index ix_promotion_usage_promotion on promotion_usage (promotion_id);
