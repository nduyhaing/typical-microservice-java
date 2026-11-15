--liquibase formatted sql

--changeset shopapp:tax-0001
-- YAS issue-tax-001, with rate numeric(7,4) instead of float(53), plain bigint state/country ids (YAS: bigserial),
-- a unique tax class name and one rate per tax class and location.
create table tax_class
(
    id               bigserial    not null,
    name             varchar(255) not null,
    created_by       varchar(255),
    created_on       timestamp(6) with time zone,
    last_modified_by varchar(255),
    last_modified_on timestamp(6) with time zone,
    constraint tax_class_pkey primary key (id),
    constraint tax_class_name_key unique (name)
);

create table tax_rate
(
    id                   bigserial     not null,
    rate                 numeric(7, 4) not null,
    zip_code             varchar(25),
    tax_class_id         bigint        not null,
    state_or_province_id bigint,
    country_id           bigint        not null,
    created_by           varchar(255),
    created_on           timestamp(6) with time zone,
    last_modified_by     varchar(255),
    last_modified_on     timestamp(6) with time zone,
    constraint tax_rate_pkey primary key (id),
    constraint tax_rate_tax_class_fk foreign key (tax_class_id) references tax_class (id),
    constraint tax_rate_rate_check check (rate >= 0 and rate <= 100)
);

create unique index tax_rate_location_key
    on tax_rate (tax_class_id, country_id, coalesce(state_or_province_id, 0), lower(coalesce(zip_code, '')));
