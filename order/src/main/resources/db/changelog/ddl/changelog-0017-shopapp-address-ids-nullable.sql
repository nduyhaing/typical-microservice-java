--liquibase formatted sql

--changeset shopapp:order-address-ids-nullable
-- YAS added these as bigserial, i.e. NOT NULL with a sequence default, so an address without location ids could not
-- be stored. Until the location service exists (slice 3) checkout addresses are typed in: ids stay NULL
-- (decisions.md 2c-6).
alter table order_address
    alter column district_id drop not null,
    alter column district_id drop default,
    alter column state_or_province_id drop not null,
    alter column state_or_province_id drop default,
    alter column country_id drop not null,
    alter column country_id drop default;
