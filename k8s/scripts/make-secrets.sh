#!/usr/bin/env bash
# Creates (or updates) the shop_app Kubernetes Secrets from .env - the charts and k8s/deploy only reference them, so
# no secret is committed beyond the dev defaults already in .env (decisions.md 6-29).
#   bash k8s/scripts/make-secrets.sh [env-file] [namespace]     # defaults: .env, shopapp
# Values go to kubectl as files in a private temp dir, never on a command line.
set -euo pipefail

cd "$(dirname "$0")/../.."
ENV_FILE="${1:-.env}"
NAMESPACE="${2:-shopapp}"

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

TMP=$(mktemp -d)
chmod 700 "$TMP"
trap 'rm -rf "$TMP"' EXIT

# secret <name> <type> KEY=VARIABLE...   (a missing optional variable, marked KEY=?VARIABLE, is skipped)
secret() {
    local name=$1 type=$2 args=() pair key var optional value
    shift 2
    mkdir -p "$TMP/$name"
    for pair in "$@"; do
        key=${pair%%=*}
        var=${pair#*=}
        optional=false
        if [ "${var#\?}" != "$var" ]; then optional=true; var=${var#\?}; fi
        value=${!var:-}
        if [ -z "$value" ]; then
            "$optional" && continue
            echo "make-secrets: $var is not set in $ENV_FILE" >&2
            exit 1
        fi
        printf '%s' "$value" > "$TMP/$name/$key"
        args+=("--from-file=$key=$TMP/$name/$key")
    done
    kubectl create secret generic "$name" --type "$type" -n "$NAMESPACE" "${args[@]}" \
        --dry-run=client -o yaml | kubectl apply -f - > /dev/null
    echo "secret $NAMESPACE/$name"
}

SAMPLEDATA_DB_USER=sampledata

# Postgres owner/superuser "admin": CloudNativePG bootstrap, every service, Keycloak, the init Jobs
secret shopapp-postgres kubernetes.io/basic-auth username=POSTGRES_USER password=POSTGRES_PASSWORD
secret shopapp-keycloak-admin kubernetes.io/basic-auth username=KEYCLOAK_ADMIN password=KEYCLOAK_ADMIN_PASSWORD
secret shopapp-keycloak-clients Opaque \
    CUSTOMER_MANAGEMENT_CLIENT_SECRET=CUSTOMER_MANAGEMENT_CLIENT_SECRET \
    STOREFRONT_BFF_CLIENT_SECRET=STOREFRONT_BFF_CLIENT_SECRET \
    BACKOFFICE_BFF_CLIENT_SECRET=BACKOFFICE_BFF_CLIENT_SECRET \
    'ORDER_SERVICE_CLIENT_SECRET=?ORDER_SERVICE_CLIENT_SECRET'     'PAYMENT_SERVICE_CLIENT_SECRET=?PAYMENT_SERVICE_CLIENT_SECRET'
secret shopapp-paypal Opaque 'PAYPAL_CLIENT_ID=?PAYPAL_CLIENT_ID' 'PAYPAL_CLIENT_SECRET=?PAYPAL_CLIENT_SECRET'
secret shopapp-debezium kubernetes.io/basic-auth username=DEBEZIUM_DB_USER password=DEBEZIUM_DB_PASSWORD
secret shopapp-sampledata-db kubernetes.io/basic-auth username=SAMPLEDATA_DB_USER password=SAMPLEDATA_DB_PASSWORD
secret shopapp-elasticsearch Opaque \
    ELASTIC_PASSWORD=ELASTIC_PASSWORD SEARCH_ES_USERNAME=SEARCH_ES_USERNAME SEARCH_ES_PASSWORD=SEARCH_ES_PASSWORD
