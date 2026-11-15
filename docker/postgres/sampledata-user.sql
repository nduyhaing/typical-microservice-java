-- "sampledata" login for the sampledata service (decisions.md 5-53). Idempotent. Runs in the product database as
-- the Postgres superuser after product's Liquibase created the tables (compose service sampledata-db-init, every
-- `docker compose up`). Reads SAMPLEDATA_DB_PASSWORD from the environment (psql \getenv), so the secret is not on
-- the command line.
\set ON_ERROR_STOP on
\getenv sd_password SAMPLEDATA_DB_PASSWORD

SELECT 'CREATE ROLE sampledata'
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sampledata') \gexec

ALTER ROLE sampledata WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'sd_password';
GRANT CONNECT ON DATABASE product TO sampledata;
\ir sampledata-grants.sql
