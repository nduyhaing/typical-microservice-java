-- Rights of the "sampledata" login in the product database (decisions.md 5-53). Plain SQL, idempotent; run as the
-- Postgres superuser after product's Liquibase created the tables, by docker/postgres/sampledata-user.sql (compose
-- service sampledata-db-init) and by the sampledata ITs.
-- Only what loading the sample catalog needs: read and insert the catalog tables (ids from their sequences) and
-- republish a soft-deleted product (is_published, and last_modified_on so the republish is a newer version, 5-70).
-- No UPDATE of anything else, no DELETE, nothing on stock_reservation /
-- stock_adjustment, and no other database's tables (orders, customers, carts live in their own databases).
GRANT USAGE ON SCHEMA public TO sampledata;
GRANT SELECT, INSERT ON public.brand, public.category, public.product, public.product_category,
    public.product_option, public.product_option_combination, public.product_option_value,
    public.product_attribute_group, public.product_attribute, public.product_attribute_value,
    public.product_related TO sampledata;
GRANT UPDATE (is_published, last_modified_on) ON public.product TO sampledata;
GRANT USAGE ON SEQUENCE public.brand_id_seq, public.category_id_seq, public.product_id_seq,
    public.product_category_id_seq, public.product_option_id_seq, public.product_option_combination_id_seq,
    public.product_option_value_id_seq, public.product_attribute_group_id_seq, public.product_attribute_id_seq,
    public.product_attribute_value_id_seq, public.product_related_id_seq TO sampledata;
