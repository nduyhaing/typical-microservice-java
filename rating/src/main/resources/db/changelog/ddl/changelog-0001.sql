--liquibase formatted sql

--changeset shopapp:rating-0001
-- YAS issue-335-1 + changelog-0002 (product_name) in their final shape, with: required content of at most 1000
-- characters (YAS varchar(255), nullable), a star between 1 and 5, required product and author, one rating per
-- customer per product (YAS allows many, decisions.md 4-32), timestamptz audit columns, and indexes for the product
-- page (newest first) and the back office's latest ratings.
create table rating
(
    id               bigserial     not null,
    content          varchar(1000) not null,
    rating_star      integer       not null,
    product_id       bigint        not null,
    product_name     varchar(450),
    first_name       varchar(255),
    last_name        varchar(255),
    created_by       varchar(255)  not null,
    created_on       timestamp(6) with time zone,
    last_modified_by varchar(255),
    last_modified_on timestamp(6) with time zone,
    constraint rating_pkey primary key (id),
    constraint rating_star_check check (rating_star between 1 and 5),
    constraint rating_created_by_product_key unique (created_by, product_id)
);

create index rating_product_created_on_idx on rating (product_id, created_on desc, id desc);
create index rating_created_on_idx on rating (created_on desc, id desc);
