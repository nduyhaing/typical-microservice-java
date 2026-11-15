-- Grants and publication for the order CDC connector (decisions.md 5-7), same pattern as debezium-user.sql (4-3).
-- Idempotent. Runs in the order database as the Postgres superuser, after order's Liquibase created the tables and
-- after debezium-user.sql created the replication login (debezium-db-init compose service, and
-- scripts/apply-debezium-user.sh by hand). Reads DEBEZIUM_DB_USER from the environment (psql \getenv).
\set ON_ERROR_STOP on
\getenv dbz_user DEBEZIUM_DB_USER

GRANT CONNECT ON DATABASE "order" TO :"dbz_user";
GRANT USAGE ON SCHEMA public TO :"dbz_user";
-- the initial snapshot reads the captured table with SELECT
GRANT SELECT ON public."order" TO :"dbz_user";

-- update events carry the old row too ("before"), so a consumer sees which status an order came from (webhook
-- ON_ORDER_STATUS_UPDATED); the order table is small and written a few times per order
ALTER TABLE public."order" REPLICA IDENTITY FULL;

-- CREATE PUBLICATION ... FOR TABLE needs table ownership, which the replication user must not have, so the
-- publication is created here and the connector finds it (publication.name = dbz_order). Keep the table list in
-- sync with table.include.list in kafka/connects/debezium-order.json.
SELECT 'CREATE PUBLICATION dbz_order FOR TABLE public."order"'
WHERE NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'dbz_order') \gexec
ALTER PUBLICATION dbz_order SET TABLE public."order";
