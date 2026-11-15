CREATE DATABASE keycloak WITH OWNER = admin ENCODING = 'UTF8';
CREATE DATABASE product WITH OWNER = admin ENCODING = 'UTF8';
CREATE DATABASE cart WITH OWNER = admin ENCODING = 'UTF8';
CREATE DATABASE "order" WITH OWNER = admin ENCODING = 'UTF8';
CREATE DATABASE customer WITH OWNER = admin ENCODING = 'UTF8';
CREATE DATABASE media WITH OWNER = admin ENCODING = 'UTF8';
CREATE DATABASE rating WITH OWNER = admin ENCODING = 'UTF8';
CREATE DATABASE location WITH OWNER = admin ENCODING = 'UTF8';
CREATE DATABASE inventory WITH OWNER = admin ENCODING = 'UTF8';
CREATE DATABASE promotion WITH OWNER = admin ENCODING = 'UTF8';
CREATE DATABASE tax WITH OWNER = admin ENCODING = 'UTF8';
CREATE DATABASE payment WITH OWNER = admin ENCODING = 'UTF8';
CREATE DATABASE webhook WITH OWNER = admin ENCODING = 'UTF8';
CREATE DATABASE recommendation WITH OWNER = admin ENCODING = 'UTF8';

-- Debezium CDC (slice 4, decisions.md 4-3): a replication-only login, not the superuser. Created here without a
-- password (NOLOGIN); docker/postgres/debezium-user.sql (run by the debezium-db-init compose service on every
-- `up`, or scripts/apply-debezium-user.sh) sets the password from .env, grants SELECT on public.product once
-- product's Liquibase has created it, and creates the publication.
CREATE ROLE debezium WITH NOLOGIN REPLICATION;
GRANT CONNECT ON DATABASE product TO debezium;

-- No login connects to a database it was not granted (slice 4 review M-5, decisions.md 4-81): PUBLIC loses CONNECT
-- everywhere. The services log in as the owner/superuser (unaffected); debezium and sampledata get CONNECT on
-- product only. docker/postgres/debezium-user.sql repeats this on every `docker compose up` (also for databases
-- added later and for volumes created before this line existed).
REVOKE CONNECT ON DATABASE postgres, keycloak, product, cart, "order", customer, media, rating, location, inventory,
    promotion, tax, payment, webhook, recommendation FROM PUBLIC;
