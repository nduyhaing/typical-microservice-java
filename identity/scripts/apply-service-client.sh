#!/usr/bin/env bash
# Adds a service-to-service client to the shopapp realm of an already-running Keycloak (decisions.md 5-5, 5-66): a
# confidential client with only the client-credentials grant (service account, no browser login, no password grant)
# whose secret comes from .env, and exactly the given capability realm roles on its service account (created if
# missing). Everything else is taken off the service account: the old catch-all SERVICE role, other SERVICE_* roles and
# default-roles-shopapp (which carries CUSTOMER), so the token can do only what that service needs.
# Idempotent: an existing client gets its secret, settings and roles re-applied. Fresh installs get both clients from
# identity/realm-export.json; this is for volumes created before that change (and for later service clients).
# Usage (from anywhere, stack running):
#   bash identity/scripts/apply-service-client.sh        # order-service, ORDER_SERVICE_CLIENT_SECRET, SERVICE_GOODS_BACK
#   bash identity/scripts/apply-service-client.sh payment-service PAYMENT_SERVICE_CLIENT_SECRET \
#       SERVICE_ORDER_READ SERVICE_ORDER_PAYMENT SERVICE_PAYPAL
set -euo pipefail
cd "$(dirname "$0")/../.."
export MSYS_NO_PATHCONV=1
set -a; source .env; set +a
client="${1:-order-service}"
secret_var="${2:-ORDER_SERVICE_CLIENT_SECRET}"
shift $(( $# > 2 ? 2 : $# ))
roles=("$@")
if [ "${#roles[@]}" -eq 0 ]; then
    case "$client" in
        order-service) roles=(SERVICE_GOODS_BACK) ;;
        payment-service) roles=(SERVICE_ORDER_READ SERVICE_ORDER_PAYMENT SERVICE_PAYPAL) ;;
        *) echo "name the capability roles of $client (e.g. SERVICE_ORDER_READ)" >&2; exit 1 ;;
    esac
fi
secret="${!secret_var:-}"
if [ -z "$secret" ]; then
    echo "$secret_var is not set in .env" >&2
    exit 1
fi
kc() { docker compose exec -T identity /opt/keycloak/bin/kcadm.sh "$@"; }
kc config credentials --server http://localhost:8080 --realm master --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

for role in "${roles[@]}"; do
    if ! kc get "roles/$role" -r shopapp >/dev/null 2>&1; then
        kc create roles -r shopapp -s "name=$role" \
            -s "description=Service capability $role (client-credentials tokens of shop_app services, decisions.md 5-66)" >/dev/null
        echo "created realm role $role"
    fi
done

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

account="service-account-$client"
user_id=$(kc get users -r shopapp -q "username=$account" --fields id | python -c "import json,sys;print(json.load(sys.stdin)[0]['id'])")
current=$(kc get "users/$user_id/role-mappings/realm" -r shopapp --fields name | python -c "import json,sys;print(' '.join(r['name'] for r in json.load(sys.stdin)))")
for role in $current; do
    keep=false
    for wanted in "${roles[@]}"; do [ "$role" = "$wanted" ] && keep=true; done
    if [ "$keep" = false ] && { [ "$role" = default-roles-shopapp ] || [[ "$role" == SERVICE* ]]; }; then
        kc remove-roles -r shopapp --uusername "$account" --rolename "$role"
        echo "removed $role from $account"
    fi
done
for role in "${roles[@]}"; do
    kc add-roles -r shopapp --uusername "$account" --rolename "$role"
done
echo "$account has realm roles: ${roles[*]}"
