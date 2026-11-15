#!/usr/bin/env bash
# Creates or updates the Debezium replication user, its grants and the dbz_product publication in the running
# Postgres (decisions.md 4-3), then reloads the Postgres config (max_slot_wal_keep_size). Idempotent.
# The debezium-db-init compose service runs the same SQL on every `docker compose up`; this script is for applying
# it by hand, e.g. after changing DEBEZIUM_DB_PASSWORD in .env (then also: docker compose restart kafka-connect).
# Usage: bash scripts/apply-debezium-user.sh   (from anywhere, stack running)
set -euo pipefail
cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1
set -a; source .env; set +a
docker compose exec -T -e DEBEZIUM_DB_USER -e DEBEZIUM_DB_PASSWORD postgres \
    psql -q -U "$POSTGRES_USER" -d product -f - < docker/postgres/debezium-user.sql
docker compose exec -T postgres psql -q -U "$POSTGRES_USER" -tAc "SELECT pg_reload_conf()" >/dev/null
echo "Debezium user $DEBEZIUM_DB_USER and publication dbz_product are in place"
