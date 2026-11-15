-- Debezium replication user and publication for the product CDC connector (decisions.md 4-3).
-- Idempotent. Runs in the product database as the Postgres superuser, after product's Liquibase created the tables:
--   * debezium-db-init compose service (every `docker compose up`, before the connector is registered)
--   * scripts/apply-debezium-user.sh (by hand, against the running stack)
-- Reads DEBEZIUM_DB_USER / DEBEZIUM_DB_PASSWORD from the environment (psql \getenv), so the secret is not on the
-- command line.
\set ON_ERROR_STOP on
\getenv dbz_user DEBEZIUM_DB_USER
\getenv dbz_password DEBEZIUM_DB_PASSWORD

SELECT format('CREATE ROLE %I', :'dbz_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'dbz_user') \gexec

-- REPLICATION opens the logical replication protocol; nothing else (no superuser, no CREATE, no write rights)
ALTER ROLE :"dbz_user" WITH LOGIN REPLICATION NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'dbz_password';
GRANT CONNECT ON DATABASE product TO :"dbz_user";
-- PUBLIC (so also this login) connects nowhere it was not granted (decisions.md 4-81); the services use the owner /
-- superuser and sampledata has its own CONNECT on product. Every database, also ones created later.
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', datname) FROM pg_database WHERE NOT datistemplate \gexec
GRANT USAGE ON SCHEMA public TO :"dbz_user";
-- the initial snapshot reads the captured table with SELECT
GRANT SELECT ON public.product TO :"dbz_user";

-- CREATE PUBLICATION ... FOR TABLE needs table ownership, which the replication user must not have, so the
-- publication is created here and the connector finds it (publication.name = dbz_product). Keep the table list in
-- sync with table.include.list in kafka/connects/debezium-product.json.
SELECT 'CREATE PUBLICATION dbz_product FOR TABLE public.product'
WHERE NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'dbz_product') \gexec
ALTER PUBLICATION dbz_product SET TABLE public.product;
