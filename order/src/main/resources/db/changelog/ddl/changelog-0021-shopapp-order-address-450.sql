--liquibase formatted sql

--changeset shopapp:order-address-450
-- Location keeps address text up to 450 characters (YAS varchar(450)); the order's copy was varchar(255), so a long
-- saved address could not be ordered (3c review I2). Phone and zip code stay: location allows 25 characters.
alter table order_address
    alter column contact_name type varchar(450),
    alter column address_line1 type varchar(450),
    alter column address_line2 type varchar(450),
    alter column city type varchar(450),
    alter column district_name type varchar(450),
    alter column state_or_province_name type varchar(450),
    alter column country_name type varchar(450);
