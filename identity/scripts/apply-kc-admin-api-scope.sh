#!/usr/bin/env bash
# Adds the kc-admin-api client scope (admin REST API audience, required since Keycloak 26.x) to the shopapp realm
# of an already-running Keycloak and gives it to the customer-management client.
# Fresh installs get the same from identity/realm-export.json; this is for volumes created before that change.
# Usage: bash identity/scripts/apply-kc-admin-api-scope.sh   (from the repo root, stack running)
set -euo pipefail
cd "$(dirname "$0")/../.."
export MSYS_NO_PATHCONV=1
set -a; source .env; set +a
kc() { docker compose exec -T identity /opt/keycloak/bin/kcadm.sh "$@"; }
kc config credentials --server http://localhost:8080 --realm master --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null
scope_id=$(kc get client-scopes -r shopapp --fields id,name | python -c "import json,sys;print(next((s['id'] for s in json.load(sys.stdin) if s['name']=='kc-admin-api'),''))")
if [ -z "$scope_id" ]; then
    kc create client-scopes -r shopapp -f - <<'JSON' >/dev/null
{"name":"kc-admin-api","description":"Grants access to the Keycloak Admin REST API by adding its required audience to the token","protocol":"openid-connect","attributes":{"include.in.token.scope":"true","display.on.consent.screen":"false"},"protocolMappers":[{"name":"admin-api-audience","protocol":"openid-connect","protocolMapper":"oidc-admin-api-audience-mapper","consentRequired":false,"config":{"lightweight.claim":"true","introspection.token.claim":"true","access.token.claim":"true"}}]}
JSON
    scope_id=$(kc get client-scopes -r shopapp --fields id,name | python -c "import json,sys;print(next(s['id'] for s in json.load(sys.stdin) if s['name']=='kc-admin-api'))")
    echo "created client scope kc-admin-api"
fi
client_id=$(kc get clients -r shopapp -q clientId=customer-management --fields id | python -c "import json,sys;print(json.load(sys.stdin)[0]['id'])")
kc update "clients/$client_id/default-client-scopes/$scope_id" -r shopapp >/dev/null
echo "customer-management has kc-admin-api"
