--liquibase formatted sql

--changeset shopapp:payment-providers
-- YAS data changesets 0001..0004 (final ids COD and PAYPAL). The PayPal row holds no credentials (YAS stored a
-- sandbox client id and secret in additional_settings): they come from .env and live in payment-paypal only. PAYPAL
-- is enabled here but only offered while payment-paypal reports its credentials configured (decisions.md 5-16).
-- No media ids: shop_app has no provider icons (YAS media 81/82).
insert into payment_provider (id, enabled, name, configure_url, landing_view_component_name, additional_settings)
values ('COD', true, 'Cash on Delivery', 'cod-config', 'cod landing view', null),
       ('PAYPAL', true, 'PayPal', 'paypal-config', 'paypal landing view', null);
