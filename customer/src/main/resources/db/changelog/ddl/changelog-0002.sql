--liquibase formatted sql

-- Saved addresses (decisions.md 3c-30): one link per (user, address), at most one default per user, address_id a
-- plain bigint (YAS bigserial created a sequence and a default), not-null default flag, timestamptz audit columns.

--changeset shopapp:customer-0002-user-address-constraints
alter table user_address alter column address_id drop default;
drop sequence if exists user_address_address_id_seq;
alter table user_address alter column address_id type bigint;
update user_address set is_active = false where is_active is null;
alter table user_address alter column is_active set default false;
alter table user_address alter column is_active set not null;
alter table user_address alter column created_on type timestamp(6) with time zone;
alter table user_address alter column last_modified_on type timestamp(6) with time zone;
alter table user_address add constraint uk_user_address_user_id_address_id unique (user_id, address_id);
create unique index uk_user_address_one_default on user_address (user_id) where is_active;
