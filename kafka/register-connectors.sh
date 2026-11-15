#!/bin/sh
# Registers the Debezium connectors with Kafka Connect and keeps them registered (decisions.md 4-4).
# Runs in the kafka-connect-init compose service (curlimages/curl); CONNECT_URL defaults to the in-network worker.
#   * at start: PUT /connectors/{name}/config - creates the connector or replaces its config, so it is idempotent
#     on every `docker compose up` and after restarts; then marks itself healthy (/tmp/registered)
#   * then every CHECK_INTERVAL seconds: re-registers a connector that is gone and restarts a connector or task
#     that is FAILED (e.g. Postgres or Kafka was down for longer than Debezium's retries)
# It stays running (instead of a one-shot) because `docker compose up --wait` fails on a container that exits.
# The JSON files hold ${env:...} placeholders that the worker resolves (EnvVarConfigProvider), not secrets.
set -eu
CONNECT_URL="${CONNECT_URL:-http://kafka-connect:8083}"
CHECK_INTERVAL="${CHECK_INTERVAL:-30}"
DIR="$(dirname "$0")/connects"
CONNECTORS="product-connector:debezium-product.json"

trap 'exit 0' TERM INT

register() {
    name=$1
    file=$2
    attempt=1
    while :; do
        code=$(curl -s -m 30 -o /tmp/connect-response -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
            --data "@$file" "$CONNECT_URL/connectors/$name/config" || true)
        case "$code" in
            200|201) echo "connector $name registered ($code)"; return 0 ;;
        esac
        if [ "$attempt" -ge 20 ]; then
            echo "connector $name: PUT failed with $code: $(cat /tmp/connect-response 2>/dev/null)" >&2
            return 1
        fi
        echo "connector $name: PUT -> $code, retrying ($attempt/20)"
        attempt=$((attempt + 1))
        sleep 3
    done
}

check() {
    name=$1
    file=$2
    code=$(curl -s -m 10 -o /tmp/connect-status -w '%{http_code}' "$CONNECT_URL/connectors/$name/status" || true)
    case "$code" in
        404) echo "connector $name is gone, registering it again"; register "$name" "$file" || true ;;
        200)
            if grep -q '"state":"FAILED"' /tmp/connect-status; then
                echo "connector $name has a FAILED connector or task, restarting it: $(cat /tmp/connect-status)"
                curl -s -m 10 -o /dev/null -X POST \
                    "$CONNECT_URL/connectors/$name/restart?includeTasks=true&onlyFailed=true" || true
            fi ;;
        *) echo "Kafka Connect status of $name -> $code (worker down?)" ;;
    esac
}

for entry in $CONNECTORS; do
    register "${entry%%:*}" "$DIR/${entry#*:}"
done
touch /tmp/registered

while :; do
    sleep "$CHECK_INTERVAL" &
    wait $!
    for entry in $CONNECTORS; do
        check "${entry%%:*}" "$DIR/${entry#*:}"
    done
done
