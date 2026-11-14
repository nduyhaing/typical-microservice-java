#!/usr/bin/env bash
# Smoke test for the shop_app foundation (slice 1): checks the definition of done against a running stack.
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
HOSTS=(identity api pgadmin kafka)
BASE_URL_SUFFIX=""
[ "$PORT" != "80" ] && BASE_URL_SUFFIX=":$PORT"
EXPECTED_ISSUER="http://identity.${DOMAIN}/realms/shopapp"
EXPECTED_DATABASES=(keycloak product cart order customer media rating location inventory promotion tax payment webhook recommendation)
SERVICES=(postgres pgadmin identity kafka kafka-ui nginx)
failures=0

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
    echo "      127.0.0.1 identity.${DOMAIN} api.${DOMAIN} pgadmin.${DOMAIN} kafka.${DOMAIN}"
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
    config=$(docker compose exec -T kafka-ui wget -qO- "http://identity.${DOMAIN}/realms/shopapp/.well-known/openid-configuration" 2>/dev/null || true)
    if echo "$config" | grep -q "\"issuer\":\"$EXPECTED_ISSUER\""; then
        pass "containers reach the issuer at identity.${DOMAIN}"
    else
        fail "containers cannot reach $EXPECTED_ISSUER through nginx"
    fi
}

check_http() {
    local name=$1 target=$2 expected=$3 code
    code=$(curl -s -o /dev/null -w '%{http_code}' "$target" || true)
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

run_checks() {
    check_services_healthy
    check_token
    check_in_network_issuer
    check_http "Kafka UI reachable" "$(url kafka /)" 200
    check_http "pgAdmin reachable" "$(url pgadmin /misc/ping)" 200
    check_http "API placeholder" "$(url api /)" 404
    check_databases
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
