#!/usr/bin/env bash
# Smoke test for shop_app: checks the definition of done of slice 1 (foundation), 2a (browse) and 2b (login + cart)
# against a running stack.
#   bash scripts/verify-foundation.sh            # checks the running stack
#   bash scripts/verify-foundation.sh --restart  # also does `docker compose down && up` and re-checks
# Needs: docker, curl, python (for JSON). Run from anywhere; works in Git Bash on Windows.
set -euo pipefail

cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1   # stop Git Bash rewriting /container/paths into Windows paths

set -a
# shellcheck disable=SC1091
source .env
set +a

PORT="${NGINX_HTTP_PORT:-80}"
HOSTS=(identity api pgadmin kafka storefront)
BASE_URL_SUFFIX=""
[ "$PORT" != "80" ] && BASE_URL_SUFFIX=":$PORT"
EXPECTED_ISSUER="${KEYCLOAK_URL}/realms/shopapp"
EXPECTED_DATABASES=(keycloak product cart order customer media rating location inventory promotion tax payment webhook recommendation)
SERVICES=(postgres pgadmin identity kafka kafka-ui redis product cart customer storefront-bff storefront nginx)
failures=0

# shellcheck source=lib/storefront-login.sh
source scripts/lib/storefront-login.sh
COOKIE_JAR=$(mktemp)
trap 'rm -f "$COOKIE_JAR"' EXIT

pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; failures=$((failures + 1)); }
url() { echo "http://$1.${DOMAIN}${BASE_URL_SUFFIX}$2"; }

check_line_endings() {
    # count CR bytes per file (a literal $'\r' grep pattern gets stripped by Git Bash, matching everything)
    local crlf=() f
    while IFS= read -r f; do
        [ -f "$f" ] && [ "$(tr -cd '\r' < "$f" | wc -c)" -gt 0 ] && crlf+=("$f")
    done < <(git ls-files -- '*.sh' '*.sql' '*.conf' '*.template' '*.json' '.env')
    if [ ${#crlf[@]} -eq 0 ]; then pass "no CRLF line endings in container files"; else fail "CRLF line endings in: ${crlf[*]}"; fi
}

check_hosts_resolve() {
    local missing=()
    for h in "${HOSTS[@]}"; do
        local ip
        ip=$(python -c "import socket,sys;print(socket.gethostbyname(sys.argv[1]))" "$h.${DOMAIN}" 2>/dev/null || true)
        [ "$ip" = "127.0.0.1" ] || missing+=("$h.${DOMAIN}")
    done
    if [ ${#missing[@]} -eq 0 ]; then
        pass "hostnames resolve to 127.0.0.1"
        return 0
    fi
    fail "hostnames do not resolve to 127.0.0.1: ${missing[*]}"
    echo "      add to C:\\Windows\\System32\\drivers\\etc\\hosts (as Administrator):"
    echo "      127.0.0.1 identity.${DOMAIN} api.${DOMAIN} pgadmin.${DOMAIN} kafka.${DOMAIN} storefront.${DOMAIN}"
    return 1
}

check_services_healthy() {
    local bad=()
    for s in "${SERVICES[@]}"; do
        local status
        status=$(docker compose ps "$s" --format '{{.Status}}' 2>/dev/null || true)
        case "$status" in
            *unhealthy*|*starting*|"") bad+=("$s(${status:-not running})") ;;
            Up*) ;;
            *) bad+=("$s($status)") ;;
        esac
    done
    if [ ${#bad[@]} -eq 0 ]; then pass "all services up and healthy"; else fail "services not healthy: ${bad[*]}"; fi
}

check_token() {
    local response
    response=$(curl -s -d grant_type=password -d client_id=swagger-ui -d username=customer -d password=customer \
        "$(url identity /realms/shopapp/protocol/openid-connect/token)" || true)
    if python - "$response" "$EXPECTED_ISSUER" <<'EOF'
import base64, json, sys
response, expected_issuer = sys.argv[1], sys.argv[2]
try:
    token = json.loads(response)["access_token"]
except Exception:
    sys.exit(f"no access_token in response: {response[:200]}")
payload = token.split(".")[1]
claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
if claims["iss"] != expected_issuer:
    sys.exit(f"iss is {claims['iss']}, expected {expected_issuer}")
if "CUSTOMER" not in claims.get("realm_access", {}).get("roles", []):
    sys.exit(f"CUSTOMER role missing: {claims.get('realm_access')}")
EOF
    then pass "token for customer has issuer $EXPECTED_ISSUER and role CUSTOMER"
    else fail "token check (see message above)"; fi
}

check_in_network_issuer() {
    local config
    config=$(docker compose exec -T kafka-ui wget -qO- "${KEYCLOAK_URL}/realms/shopapp/.well-known/openid-configuration" 2>/dev/null || true)
    if echo "$config" | grep -q "\"issuer\":\"$EXPECTED_ISSUER\""; then
        pass "containers reach the issuer at identity.${DOMAIN}"
    else
        fail "containers cannot reach $EXPECTED_ISSUER through nginx"
    fi
}

check_identity_redirect() {
    # browsers must be sent back to the public URL (domain and port), not to port 80
    local location
    location=$(curl -s -o /dev/null -w '%{redirect_url}' "$(url identity /admin/)" || true)
    case "$location" in
        "${KEYCLOAK_URL}"/*) pass "Keycloak redirects stay on ${KEYCLOAK_URL}" ;;
        *) fail "Keycloak redirects to '${location}', expected ${KEYCLOAK_URL}/... (KEYCLOAK_URL/NGINX_HTTP_PORT mismatch?)" ;;
    esac
}

check_ports_loopback_only() {
    # admin tools with dev credentials must not be reachable from the LAN
    local exposed
    exposed=$(docker compose ps --format '{{.Service}} {{.Ports}}' | grep -E '(0\.0\.0\.0|\[::\]|:::)[0-9]*:' || true)
    if [ -z "$exposed" ]; then pass "published ports listen on 127.0.0.1 only"; else fail "ports exposed on all interfaces: $exposed"; fi
}

check_http() {
    # check_http <name> <url> <expected status> [extra curl args...]
    local name=$1 target=$2 expected=$3 code
    shift 3
    code=$(curl -s -o /dev/null -w '%{http_code}' "$@" "$target" || true)
    if [ "$code" = "$expected" ]; then pass "$name ($target -> $code)"; else fail "$name ($target -> $code, expected $expected)"; fi
}

check_databases() {
    local existing missing=()
    existing=$(docker compose exec -T postgres psql -U "$POSTGRES_USER" -tAc "SELECT datname FROM pg_database" 2>/dev/null || true)
    for db in "${EXPECTED_DATABASES[@]}"; do
        echo "$existing" | tr -d '\r' | grep -qx "$db" || missing+=("$db")
    done
    if [ ${#missing[@]} -eq 0 ]; then pass "all ${#EXPECTED_DATABASES[@]} databases exist"; else fail "missing databases: ${missing[*]}"; fi
}

check_body() {
    # check_body <name> <url> <expected status> <text that must appear in the body>
    local name=$1 target=$2 expected=$3 text=$4 response code body
    response=$(curl -s -w '
%{http_code}' "$target" || true)
    code=${response##*$'
'}
    body=${response%$'
'*}
    if [ "$code" = "$expected" ] && grep -qF -- "$text" <<< "$body"; then
        pass "$name ($target -> $code)"
    else
        fail "$name ($target -> $code, expected $expected containing '$text')"
    fi
}

check_storefront() {
    check_body "storefront home page" "$(url storefront /)" 200 'id="__next"'
    check_body "featured products through the BFF" "$(url storefront /api/product/storefront/products/featured)" 200         '"name":"Galaxy S24"'
    check_body "categories through the BFF" "$(url storefront /api/product/storefront/categories)" 200 '"slug":"phones"'
    check_body "server-rendered product page" "$(url storefront /products/galaxy-s24)" 200 'Galaxy S24'
    check_body "product page shows the price" "$(url storefront /products/galaxy-s24)" 200 '$799.00'
    check_http "unknown product page is a 404" "$(url storefront /products/does-not-exist)" 404
    check_body "unknown product API is a 404 ErrorVm" "$(url storefront /api/product/storefront/product/does-not-exist)" 404         '"statusCode":"404 NOT_FOUND"'
    check_http "unpublished product is never exposed" "$(url storefront /api/product/storefront/product/prototype-phone)" 404
    check_http "variation has no page of its own" "$(url storefront /products/galaxy-s24-black)" 404
    local odd_slug_code
    odd_slug_code=$(curl -s -o /dev/null -w '%{http_code}' "$(url storefront /products/galaxy%2Fs24)" || true)
    case "$odd_slug_code" in
        400|404) pass "odd slug (encoded slash) is rejected with $odd_slug_code, not a 500" ;;
        *) fail "odd slug (encoded slash) -> $odd_slug_code, expected 400 or 404" ;;
    esac
    check_http "huge page number is a 400, not a 500"         "$(url storefront '/api/product/storefront/products?pageNo=2147483647')" 400
}

check_login_and_cart() {
    local base product_id violet_id
    base=$(url storefront "")
    check_body "anonymous session is not logged in" "$(url storefront /authentication)" 200 '"isAuthenticated":false'
    check_http "anonymous cart call is rejected" "$(url storefront /api/cart/storefront/cart/items)" 403
    if ! storefront_login "$COOKIE_JAR" "$base" customer customer; then
        fail "login as customer through storefront-bff + Keycloak"
        return
    fi
    pass "login as customer through storefront-bff + Keycloak"
    if curl -s -b "$COOKIE_JAR" "$(url storefront /api/customer/storefront/customer/profile)" | grep -q '"username":"customer"'; then
        pass "profile through the BFF (token relayed to the customer service)"
    else
        fail "profile through the BFF"
    fi
    product_id=$(curl -s "$(url storefront /api/product/storefront/product/galaxy-s24)"         | python -c "import json,sys;print(json.load(sys.stdin)['id'])")
    violet_id=$(curl -s "$(url storefront "/api/product/storefront/product-variations/$product_id")"         | python -c "import json,sys;print(next(v['id'] for v in json.load(sys.stdin) if v['slug']=='galaxy-s24-violet'))")
    # (|| true: with MSYS_NO_PATHCONV, Windows curl cannot write to /dev/null and exits non-zero)
    curl -s -o /dev/null -b "$COOKIE_JAR" -X DELETE "$(url storefront "/api/cart/storefront/cart/items/$violet_id")" || true
    curl -s -o /dev/null -b "$COOKIE_JAR" -H 'Content-Type: application/json'         -d "{\"productId\":$violet_id,\"quantity\":2}" "$(url storefront /api/cart/storefront/cart/items)" || true
    if curl -s -b "$COOKIE_JAR" "$(url storefront /api/cart/storefront/cart/items)"         | grep -q "\"productId\":$violet_id,\"quantity\":2"; then
        pass "add Galaxy S24 Violet to the cart and read it back"
    else
        fail "add Galaxy S24 Violet to the cart and read it back"
    fi
    curl -s -o /dev/null -b "$COOKIE_JAR" -X DELETE "$(url storefront "/api/cart/storefront/cart/items/$violet_id")" || true
    curl -s -o /dev/null -L -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X POST "$(url storefront /logout)" || true
    if curl -s -b "$COOKIE_JAR" "$(url storefront /authentication)" | grep -q '"isAuthenticated":false'; then
        pass "logout ends the session"
    else
        fail "logout ends the session"
    fi
    local after_logout
    after_logout=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" "$(url storefront /api/cart/storefront/cart/items)" || true)
    case "$after_logout" in
        401|403) pass "cart calls are rejected after logout ($after_logout)" ;;
        *) fail "cart call after logout -> $after_logout, expected 401/403" ;;
    esac
    check_http "cross-site POST to the BFF is refused" "$(url storefront /logout)" 403 -X POST -H 'Origin: http://evil.example'
    check_http "customer service rejects anonymous calls" "$(url api /customer/storefront/customer/profile)" 401
}

run_checks() {
    check_services_healthy
    check_ports_loopback_only
    check_token
    check_identity_redirect
    check_in_network_issuer
    check_http "Kafka UI reachable" "$(url kafka /)" 200
    check_http "pgAdmin reachable" "$(url pgadmin /misc/ping)" 200
    check_http "API placeholder" "$(url api /)" 404
    check_databases
    check_storefront
    check_login_and_cart
}

echo "Using nginx port ${PORT}, domain ${DOMAIN}"
check_line_endings
if check_hosts_resolve; then
    run_checks
    if [ "${1:-}" = "--restart" ]; then
        echo "Restarting the stack (docker compose down && up, volumes kept)..."
        docker compose down >/dev/null 2>&1
        docker compose up -d --wait >/dev/null 2>&1 || true
        check_services_healthy
        check_token
    fi
fi

echo
if [ "$failures" -eq 0 ]; then echo "All checks passed."; else echo "$failures check(s) failed."; fi
exit $((failures > 0))
