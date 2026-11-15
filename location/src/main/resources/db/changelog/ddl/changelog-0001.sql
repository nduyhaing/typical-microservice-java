--liquibase formatted sql

-- Final shape of YAS location's ddl (changesets issue-474-1/-2/-5) written as one changeset, with fixes
-- (decisions.md 3c): code2/code3 varchar(3) (YAS char(3) pads 'VN' to 'VN '), foreign key columns bigint
-- (YAS bigserial created sequences), address state/district optional (seed data has them for Viet Nam only),
-- address created_by = owner (Keycloak user id), unique indexes for the duplicate checks.

--changeset shopapp:location-0001
create table country (
    id bigserial primary key,
    name varchar(450) not null,
    code2 varchar(3) not null,
    code3 varchar(3),
    is_billing_enabled boolean,
    is_shipping_enabled boolean,
    is_city_enabled boolean,
    is_zip_code_enabled boolean,
    is_district_enabled boolean,
    created_by varchar(255),
    created_on timestamp(6) with time zone,
    last_modified_by varchar(255),
    last_modified_on timestamp(6) with time zone
);
create unique index ux_country_name on country (lower(name));
create unique index ux_country_code2 on country (lower(code2));

create table state_or_province (
    id bigserial primary key,
    name varchar(450) not null,
    code varchar(255),
    type varchar(255),
    country_id bigint not null references country (id),
    created_by varchar(255),
    created_on timestamp(6) with time zone,
    last_modified_by varchar(255),
    last_modified_on timestamp(6) with time zone
);
create unique index ux_state_or_province_country_name on state_or_province (country_id, lower(name));

create table district (
    id bigserial primary key,
    name varchar(450) not null,
    type varchar(450),
    location varchar(450),
    state_or_province_id bigint not null references state_or_province (id),
    created_by varchar(255),
    created_on timestamp(6) with time zone,
    last_modified_by varchar(255),
    last_modified_on timestamp(6) with time zone
);
create index ix_district_state_or_province on district (state_or_province_id);

create table address (
    id bigserial primary key,
    contact_name varchar(450),
    phone varchar(25),
    address_line_1 varchar(450),
    address_line_2 varchar(450),
    city varchar(450),
    zip_code varchar(25),
    district_id bigint references district (id),
    state_or_province_id bigint references state_or_province (id),
    country_id bigint not null references country (id),
    created_by varchar(255),
    created_on timestamp(6) with time zone,
    last_modified_by varchar(255),
    last_modified_on timestamp(6) with time zone
);
create index ix_address_country on address (country_id);
create index ix_address_state_or_province on address (state_or_province_id);
create index ix_address_district on address (district_id);
