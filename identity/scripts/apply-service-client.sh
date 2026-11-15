#!/usr/bin/env bash
# Adds a service-to-service client to the shopapp realm of an already-running Keycloak (decisions.md 5-5): realm
# role SERVICE (created if missing), a confidential client with only the client-credentials grant (service account,
# no browser login, no password grant) whose secret comes from .env, and SERVICE on its service account.
# Idempotent: an existing client gets its secret and settings re-applied. Fresh installs get the order-service client
# from identity/realm-export.json; this is for volumes created before that change (and for later service clients).
# Usage (from anywhere, stack running):
#   bash identity/scripts/apply-service-client.sh                              # order-service, ORDER_SERVICE_CLIENT_SECRET
#   bash identity/scripts/apply-service-client.sh payment-service PAYMENT_SERVICE_CLIENT_SECRET
set -euo pipefail
cd "$(dirname "$0")/../.."
export MSYS_NO_PATHCONV=1
set -a; source .env; set +a
client="${1:-order-service}"
secret_var="${2:-ORDER_SERVICE_CLIENT_SECRET}"
secret="${!secret_var:-}"
if [ -z "$secret" ]; then
    echo "$secret_var is not set in .env" >&2
    exit 1
fi
kc() { docker compose exec -T identity /opt/keycloak/bin/kcadm.sh "$@"; }
kc config credentials --server http://localhost:8080 --realm master --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

if ! kc get roles/SERVICE -r shopapp >/dev/null 2>&1; then
    kc create roles -r shopapp -s name=SERVICE \
        -s 'description=Service-to-service calls (client-credentials tokens of shop_app services, decisions.md 5-5)' >/dev/null
    echo "created realm role SERVICE"
fi

settings=(-s enabled=true -s publicClient=false -s clientAuthenticatorType=client-secret -s "secret=$secret"
    -s standardFlowEnabled=false -s implicitFlowEnabled=false -s directAccessGrantsEnabled=false
    -s serviceAccountsEnabled=true -s 'attributes."access.token.lifespan"=300')
id=$(kc get clients -r shopapp -q "clientId=$client" --fields id | python -c "import json,sys;c=json.load(sys.stdin);print(c[0]['id'] if c else '')")
if [ -z "$id" ]; then
    kc create clients -r shopapp -s "clientId=$client" "${settings[@]}" >/dev/null
    id=$(kc get clients -r shopapp -q "clientId=$client" --fields id | python -c "import json,sys;print(json.load(sys.stdin)[0]['id'])")
    echo "created client $client"
else
    kc update "clients/$id" -r shopapp "${settings[@]}" >/dev/null
    echo "updated client $client"
fi

kc add-roles -r shopapp --uusername "service-account-$client" --rolename SERVICE
echo "service-account-$client has realm role SERVICE"
