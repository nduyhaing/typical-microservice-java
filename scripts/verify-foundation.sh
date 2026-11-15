#!/usr/bin/env bash
# Smoke test for shop_app: checks the definition of done of slice 1 (foundation) and slice 2 (2a browse,
# 2b login + cart, 2c checkout + orders) and later slices against a running stack.
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
HOSTS=(identity api pgadmin kafka storefront backoffice)
BASE_URL_SUFFIX=""
[ "$PORT" != "80" ] && BASE_URL_SUFFIX=":$PORT"
EXPECTED_ISSUER="${KEYCLOAK_URL}/realms/shopapp"
EXPECTED_DATABASES=(keycloak product cart order customer media rating location inventory promotion tax payment webhook recommendation)
SERVICES=(postgres pgadmin identity kafka kafka-ui kafka-connect elasticsearch search redis product cart customer order media location tax inventory rating promotion storefront-bff storefront backoffice-bff backoffice nginx)
failures=0

# shellcheck source=lib/storefront-login.sh
source scripts/lib/storefront-login.sh
# shellcheck source=lib/backoffice-login.sh
source scripts/lib/backoffice-login.sh
COOKIE_JAR=$(mktemp)
ADMIN_JAR=$(mktemp)
trap 'rm -f "$COOKIE_JAR" "$ADMIN_JAR"' EXIT

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
    # check_body <name> <url> <expected status> <text that must appear in the body> [extra curl args...]
    local name=$1 target=$2 expected=$3 text=$4 response code body
    shift 4
    response=$(curl -s -w '
%{http_code}' "$@" "$target" || true)
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

# Slice 2c, with the logged-in cookie jar and Galaxy S24 Violet (quantity 2) in the cart.
check_checkout_and_order() {
    local violet_id=$1 checkout order_id
    checkout=$(curl -s -b "$COOKIE_JAR" -H 'Content-Type: application/json'         -d "{\"checkoutItemPostVms\":[{\"productId\":$violet_id,\"quantity\":2}]}"         "$(url storefront /api/order/storefront/checkouts)" || true)
    if ! grep -q '"totalAmount":1638.0' <<< "$checkout"; then
        fail "checkout priced by the server (2 x 819.00)"
        return
    fi
    pass "checkout priced by the server (2 x 819.00)"
    # typed addresses take a country id from location (3c-42); Galaxy S24 has no tax class, so the total is untaxed
    vn_id=$(curl -s "$(url api /location/storefront/countries)"         | python -c "import json,sys;print(next(c['id'] for c in json.load(sys.stdin) if c['code2']=='VN'))" || true)
    order_id=$(curl -s -b "$COOKIE_JAR" -H 'Content-Type: application/json'         -d "{\"checkoutId\":\"$(python -c "import json,sys;print(json.load(sys.stdin)['id'])" <<< "$checkout")\",\"paymentMethod\":\"COD\",\"totalPrice\":1,\"shippingAddressPostVm\":{\"contactName\":\"Customer Dev\",\"phone\":\"0123456789\",\"addressLine1\":\"1 Main St\",\"city\":\"Ho Chi Minh City\",\"countryId\":$vn_id}}"         "$(url storefront /api/order/storefront/orders)"         | python -c "import json,sys;o=json.load(sys.stdin);print(o['id'] if o.get('orderStatus')=='PENDING_PAYMENT' and float(o.get('totalPrice') or 0)==1638.0 else '')" || true)
    if [ -n "$order_id" ]; then
        pass "order placed as PENDING_PAYMENT with the server total (client total ignored)"
    else
        fail "order placed as PENDING_PAYMENT with the server total"
        return
    fi
    if curl -s -b "$COOKIE_JAR" "$(url storefront /api/order/storefront/orders/my-orders)" | grep -q "\"id\":$order_id,"; then
        pass "my orders lists order #$order_id"
    else
        fail "my orders lists order #$order_id"
    fi
    if curl -s -b "$COOKIE_JAR" "$(url storefront /api/cart/storefront/cart/items)" | grep -q "\"productId\":$violet_id,"; then
        fail "ordered item removed from the cart"
    else
        pass "ordered item removed from the cart"
    fi
}

# Slice 3c: address book through the BFF (customer saves the link, location the address, caller's token relayed).
check_address_book() {
    local country_id address_id status
    country_id=$(curl -s "$(url storefront /api/location/storefront/countries)"         | python -c "import json,sys;print(next(c['id'] for c in json.load(sys.stdin) if c['code2']=='VN'))" || true)
    address_id=$(curl -s -b "$COOKIE_JAR" -H 'Content-Type: application/json'         -d "{\"contactName\":\"Smoke Address\",\"phone\":\"0123456789\",\"addressLine1\":\"1 Smoke St\",\"countryId\":${country_id:-0}}"         "$(url storefront /api/customer/storefront/user-address)"         | python -c "import json,sys;print(json.load(sys.stdin)['addressGetVm']['id'])" 2>/dev/null || true)
    if [ -z "$address_id" ]; then
        fail "save an address in the address book"
        return
    fi
    pass "save an address in the address book"
    if curl -s -b "$COOKIE_JAR" "$(url storefront /api/customer/storefront/user-address)" | grep -q "\"id\":$address_id,"; then
        pass "address book lists address #$address_id"
    else
        fail "address book lists address #$address_id"
    fi
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$COOKIE_JAR" -X DELETE         "$(url storefront "/api/customer/storefront/user-address/$address_id")" || true)
    if [ "$status" = "200" ]; then pass "delete the saved address"; else fail "delete the saved address -> $status"; fi
}

# 3c review I3: product's order-only stock endpoints are reachable inside the compose network only.
check_stock_endpoints_blocked() {
    local origin op
    origin="http://storefront.${DOMAIN}${BASE_URL_SUFFIX}"
    for op in subtract restore; do
        check_http "a signed-in customer cannot call $op-quantity through the BFF"             "$(url storefront "/api/product/backoffice/products/$op-quantity?reference=smoke-1")" 403             -b "$COOKIE_JAR" -H "Origin: $origin" -X PUT -H 'Content-Type: application/json'             -d '[{"productId":1,"quantity":1}]'
        check_http "the API gateway refuses $op-quantity"             "$(url api "/product/backoffice/products/$op-quantity?reference=smoke-1")" 403             -X PUT -H 'Content-Type: application/json' -d '[{"productId":1,"quantity":1}]'
    done
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
    check_checkout_and_order "$violet_id"
    check_address_book
    check_stock_endpoints_blocked
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
    check_http "anonymous checkout is rejected" "$(url storefront /api/order/storefront/checkouts)" 403         -X POST -H 'Content-Type: application/json' -d '{"checkoutItemPostVms":[{"productId":25,"quantity":1}]}' 
}

# Slice 3a: back office (ADMIN only) and catalog admin through backoffice-bff.
check_backoffice() {
    local base origin brand_id status
    base=$(url backoffice "")
    origin="http://backoffice.${DOMAIN}${BASE_URL_SUFFIX}"
    check_http "back office needs a login" "$(url backoffice /)" 302
    if ! backoffice_login "$ADMIN_JAR" "$base" admin admin; then
        fail "admin login to the back office"
        return
    fi
    pass "admin login to the back office"
    check_body "back office brands page" "$(url backoffice /catalog/brands)" 200 'Brands' -b "$ADMIN_JAR"
    curl -s -o /dev/null -b "$ADMIN_JAR" -H "Origin: $origin" -H 'Content-Type: application/json'         -d '{"name":"Smoke Brand","slug":"smoke-brand","isPublish":true}'         "$(url backoffice /api/product/backoffice/brands)" || true
    brand_id=$(curl -s -b "$ADMIN_JAR" "$(url backoffice '/api/product/backoffice/brands?brandName=smoke')"         | python -c "import json,sys;b=[x for x in json.load(sys.stdin) if x['slug']=='smoke-brand'];print(b[0]['id'] if b else '')" || true)
    if [ -n "$brand_id" ]; then
        pass "admin creates a brand and sees it in the list"
    else
        fail "admin creates a brand and sees it in the list"
        return
    fi
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$ADMIN_JAR" -H "Origin: $origin" -X DELETE         "$(url backoffice "/api/product/backoffice/brands/$brand_id")" || true)
    if [ "$status" = "204" ]; then pass "admin deletes the brand"; else fail "admin deletes the brand -> $status"; fi
    backoffice_login "$COOKIE_JAR" "$base" customer customer || true
    status=$(backoffice_auth_status "$COOKIE_JAR" "$base" || true)
    if [ "$status" = "403" ]; then pass "a customer is refused by the back office (403)"; else fail "customer in back office -> $status"; fi
    check_http "customer token cannot use admin endpoints" "$(url api /product/backoffice/brands)" 401
}

# Writes a 4x4 red PNG to stdout (generated, so the smoke test needs no binary fixture).
smoke_png() {
    python -c "import struct,sys,zlib
chunk=lambda t,d:struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d))
rows=(b'\x00'+b'\xff\x00\x00'*4)*4
sys.stdout.buffer.write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',4,4,8,2,0,0,0))
    +chunk(b'IDAT',zlib.compress(rows))+chunk(b'IEND',b''))"
}

# Back office product update body from the admin detail (+ related products) with another thumbnail. The update
# replaces categories, images and related products (3a-19), so the current ones are sent back; variations: null
# leaves variations untouched. Usage: product_put_body <detail-json> <related-json> <thumbnail id or "">
product_put_body() {
    python - "$1" "$2" "$3" <<'EOF'
import json, sys
d, related, thumb = json.loads(sys.argv[1]), json.loads(sys.argv[2]), sys.argv[3]
keep = ["name", "slug", "price", "isAllowedToOrder", "isPublished", "isFeatured", "stockTrackingEnabled", "brandId",
        "shortDescription", "description", "specification", "sku", "gtin", "weight", "dimensionUnit", "length",
        "width", "height", "metaTitle", "metaKeyword", "metaDescription", "taxClassId"]
body = {k: d.get(k) for k in keep}
body["isVisibleIndividually"] = d.get("isVisible")
body["categoryIds"] = [c["id"] for c in d.get("categories") or []]
body["productImageIds"] = [m["id"] for m in d.get("productImageMedias") or []]
body["relatedProductIds"] = [p["id"] for p in related]
body["thumbnailMediaId"] = int(thumb) if thumb else None
print(json.dumps(body))
EOF
}

# Slice 3b: media upload through backoffice-bff, product thumbnail from the media service on the storefront page.
check_media() {
    local base origin media_id status customer_token product_id detail related original_thumb media_url headers big_id
    local media_in_use
    base=$(url backoffice "")
    origin="http://backoffice.${DOMAIN}${BASE_URL_SUFFIX}"
    if [ "$(backoffice_auth_status "$ADMIN_JAR" "$base" || true)" != "200" ] \
        && ! backoffice_login "$ADMIN_JAR" "$base" admin admin; then
        fail "admin login to the back office (media)"
        return
    fi
    media_id=$(smoke_png | curl -s -b "$ADMIN_JAR" -H "Origin: $origin" -F caption=smoke \
        -F 'multipartFile=@-;filename=smoke.png;type=image/png' "$(url backoffice /api/media/medias)" \
        | python -c "import json,sys;m=json.load(sys.stdin);print(m['id'] if m.get('mediaType')=='image/png' else '')" \
        2>/dev/null || true)
    if [ -z "$media_id" ]; then
        fail "admin uploads a PNG through the back office"
        return
    fi
    pass "admin uploads a PNG through the back office (media #$media_id)"
    status=$(echo 'not an image' | curl -s -o /dev/null -w '%{http_code}' -b "$ADMIN_JAR" -H "Origin: $origin" \
        -F 'multipartFile=@-;filename=fake.png;type=image/png' "$(url backoffice /api/media/medias)" || true)
    if [ "$status" = "400" ]; then pass "a text file named .png is refused (400)"; else fail "text file named .png -> $status"; fi
    # a file of exactly 10 MB passes nginx (client_max_body_size 11m) and the media service (3b-23)
    big_id=$(smoke_png | python -c "import sys;d=sys.stdin.buffer.read();sys.stdout.buffer.write(d.ljust(10*1024*1024,b'\0'))" \
        | curl -s -b "$ADMIN_JAR" -H "Origin: $origin" -F 'multipartFile=@-;filename=ten-mb.png;type=image/png' \
            "$(url backoffice /api/media/medias)" \
        | python -c "import json,sys;print(json.load(sys.stdin).get('id',''))" 2>/dev/null || true)
    if [ -n "$big_id" ]; then
        pass "a 10 MB PNG uploads through the back office"
        curl -s -o /dev/null -b "$ADMIN_JAR" -H "Origin: $origin" -X DELETE \
            "$(url backoffice "/api/media/medias/$big_id")" || true
    else
        fail "a 10 MB PNG upload through the back office was refused"
    fi
    customer_token=$(curl -s -d grant_type=password -d client_id=swagger-ui -d username=customer -d password=customer \
        "$(url identity /realms/shopapp/protocol/openid-connect/token)" \
        | python -c "import json,sys;print(json.load(sys.stdin)['access_token'])" || true)
    status=$(smoke_png | curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $customer_token" \
        -F 'multipartFile=@-;filename=smoke.png' "$(url api /media/medias)" || true)
    if [ "$status" = "403" ]; then pass "a customer token cannot upload media (403)"; else fail "customer upload -> $status"; fi
    # without a bearer header Spring's CSRF check refuses first (403, 3b-2); a bad token is a 401
    status=$(smoke_png | curl -s -o /dev/null -w '%{http_code}' -F 'multipartFile=@-;filename=smoke.png' \
        "$(url api /media/medias)" || true)
    if [ "$status" = "403" ]; then pass "an anonymous upload is refused (403)"; else fail "anonymous upload -> $status"; fi
    status=$(smoke_png | curl -s -o /dev/null -w '%{http_code}' -H 'Authorization: Bearer not-a-token' \
        -F 'multipartFile=@-;filename=smoke.png' "$(url api /media/medias)" || true)
    if [ "$status" = "401" ]; then pass "an upload with an invalid token is a 401"; else fail "invalid token upload -> $status"; fi

    product_id=$(curl -s "$(url storefront /api/product/storefront/product/galaxy-a55)" \
        | python -c "import json,sys;print(json.load(sys.stdin)['id'])" || true)
    detail=$(curl -s -b "$ADMIN_JAR" "$(url backoffice "/api/product/backoffice/products/$product_id")" || true)
    related=$(curl -s -b "$ADMIN_JAR" "$(url backoffice "/api/product/backoffice/products/related-products/$product_id")" || true)
    original_thumb=$(python -c "import json,sys;t=json.load(sys.stdin).get('thumbnailMedia');print(t['id'] if t else '')" \
        <<< "$detail" 2>/dev/null || echo invalid)
    # the media may only be deleted once no product points at it any more (3b review M-10)
    media_in_use=no
    if [ "$original_thumb" = "invalid" ] || ! python -c "import json,sys;json.load(sys.stdin)" <<< "$related" 2>/dev/null; then
        fail "read the Galaxy A55 admin detail"
    else
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$ADMIN_JAR" -H "Origin: $origin" -X PUT \
            -H 'Content-Type: application/json' -d "$(product_put_body "$detail" "$related" "$media_id")" \
            "$(url backoffice "/api/product/backoffice/products/$product_id")" || true)
        if [ "$status" = "204" ]; then pass "admin sets the image as the Galaxy A55 thumbnail"; else fail "set thumbnail -> $status"; fi
        media_url=$(curl -s "$(url api "/media/medias/$media_id")" \
            | python -c "import json,sys;print(json.load(sys.stdin)['url'])" || true)
        check_body "storefront product page shows the media URL" "$(url storefront /products/galaxy-a55)" 200 \
            "${media_url:-no media url}"
        headers=$(curl -s -o /dev/null -D - "${media_url:-$(url api /media/none)}" | tr -d '\r' || true)
        if grep -q '^HTTP/[0-9.]* 200' <<< "$headers" && grep -qi '^content-type: image/png' <<< "$headers"; then
            pass "media file URL answers 200 image/png"
        else
            fail "media file URL $media_url does not answer 200 image/png"
        fi
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$ADMIN_JAR" -H "Origin: $origin" -X PUT \
            -H 'Content-Type: application/json' -d "$(product_put_body "$detail" "$related" "$original_thumb")" \
            "$(url backoffice "/api/product/backoffice/products/$product_id")" || true)
        if [ "$status" = "204" ]; then
            pass "restore the Galaxy A55 thumbnail"
        else
            fail "restore thumbnail -> $status"
            media_in_use=yes
        fi
    fi
    if [ "$media_in_use" = "yes" ]; then
        fail "uploaded media $media_id kept: Galaxy A55 may still use it as its thumbnail (restore it by hand)"
        return
    fi
    status=$(curl -s -o /dev/null -w '%{http_code}' -b "$ADMIN_JAR" -H "Origin: $origin" -X DELETE \
        "$(url backoffice "/api/media/medias/$media_id")" || true)
    case "$status" in
        200|204) pass "admin deletes the uploaded media ($status)" ;;
        *) fail "delete uploaded media -> $status" ;;
    esac
}

# Slice 4a CDC: the Debezium connector runs and a product change reaches dbproduct.public.product within 30 s.
# The change is a rename through the product API (admin token), restored afterwards.
check_cdc() {
    local status token product_id detail related original marker found
    status=$(docker compose exec -T kafka-connect curl -s localhost:8083/connectors/product-connector/status 2>/dev/null \
        | python -c "import json,sys;s=json.load(sys.stdin);print(s['connector']['state']+'/'+','.join(t['state'] for t in s['tasks']))" \
        2>/dev/null || true)
    if [ "$status" = "RUNNING/RUNNING" ]; then
        pass "Debezium product-connector and its task are RUNNING"
    else
        fail "Debezium product-connector is '${status:-unreachable}' (docker compose logs kafka-connect kafka-connect-init)"
        return
    fi
    token=$(curl -s -d grant_type=password -d client_id=swagger-ui -d username=admin -d password=admin \
        "$(url identity /realms/shopapp/protocol/openid-connect/token)" \
        | python -c "import json,sys;print(json.load(sys.stdin)['access_token'])" 2>/dev/null || true)
    product_id=$(curl -s "$(url api /product/storefront/product/galaxy-a55)" \
        | python -c "import json,sys;print(json.load(sys.stdin)['id'])" 2>/dev/null || true)
    detail=$(curl -s -H "Authorization: Bearer $token" "$(url api "/product/backoffice/products/$product_id")" || true)
    related=$(curl -s -H "Authorization: Bearer $token" \
        "$(url api "/product/backoffice/products/related-products/$product_id")" || true)
    original=$(python -c "import json,sys;d=json.load(sys.stdin);print(d['name'])" <<< "$detail" 2>/dev/null || true)
    if [ -z "$product_id" ] || [ -z "$original" ]; then
        fail "read the Galaxy A55 admin detail (CDC)"
        return
    fi
    marker="$original CDC smoke $$"
    put_name() {
        local thumb body
        thumb=$(python -c "import json,sys;t=json.load(sys.stdin).get('thumbnailMedia');print(t['id'] if t else '')" <<< "$detail")
        body=$(product_put_body "$detail" "$related" "$thumb" | python -c "import json,sys;b=json.load(sys.stdin);b['name']=sys.argv[1];print(json.dumps(b))" "$1")
        curl -s -o /dev/null -w '%{http_code}' -X PUT -H "Authorization: Bearer $token" \
            -H 'Content-Type: application/json' -d "$body" "$(url api "/product/backoffice/products/$product_id")" || true
    }
    status=$(put_name "$marker")
    if [ "$status" != "204" ]; then
        fail "admin renames Galaxy A55 for the CDC check -> $status"
        return
    fi
    # re-read the topic (small) until the marker, unique to this run, shows up; give up after 30 s
    local deadline=$((SECONDS + 30))
    found=""
    while [ -z "$found" ] && [ "$SECONDS" -lt "$deadline" ]; do
        found=$(docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
            --bootstrap-server localhost:9092 --topic dbproduct.public.product --from-beginning --timeout-ms 5000 \
            2>/dev/null | grep -m 1 -F "\"name\":\"$marker\"" || true)
    done
    if python -c "import json,sys;v=json.loads(sys.argv[1]);assert v['after']['id']==int(sys.argv[2]) and v['op']=='u'" \
        "$found" "$product_id" 2>/dev/null; then
        pass "a product rename lands on topic dbproduct.public.product (CDC)"
    else
        fail "no CDC message for product $product_id on dbproduct.public.product within 30 s"
    fi
    status=$(put_name "$original")
    if [ "$status" = "204" ]; then pass "restore the Galaxy A55 name"; else fail "restore the Galaxy A55 name '$original' -> $status (fix by hand)"; fi
}

# Slice 4 search (decisions.md 4-19): the index is filled from the CDC topic, hidden products are never found, and an
# admin rename reaches the search results within 30 s. The name is put back afterwards.
check_search() {
    local body total token product_id detail related original marker thumb status deadline found
    body=$(curl -s "$(url api '/search/storefront/catalog-search?keyword=galaxy')" || true)
    total=$(python -c "import json,sys;d=json.load(sys.stdin);print(d['totalElements'])" <<< "$body" 2>/dev/null || true)
    if [ -n "$total" ] && [ "$total" -gt 0 ] && grep -q '"name":"Galaxy' <<< "$body"; then
        pass "search 'galaxy' finds $total Samsung phones"
    else
        fail "search 'galaxy' returned no products (docker compose logs search elasticsearch)"
        return
    fi
    check_body "search suggestions" "$(url api '/search/storefront/search_suggest?keyword=gala')" 200 '"name":"Galaxy'
    check_body "unpublished product is never found" "$(url api '/search/storefront/catalog-search?keyword=prototype')" \
        200 '"totalElements":0'
    check_http "search page size is bounded" "$(url api '/search/storefront/catalog-search?size=1000')" 400
    token=$(curl -s -d grant_type=password -d client_id=swagger-ui -d username=admin -d password=admin \
        "$(url identity /realms/shopapp/protocol/openid-connect/token)" \
        | python -c "import json,sys;print(json.load(sys.stdin)['access_token'])" 2>/dev/null || true)
    product_id=$(curl -s "$(url api /product/storefront/product/galaxy-a55)" \
        | python -c "import json,sys;print(json.load(sys.stdin)['id'])" 2>/dev/null || true)
    detail=$(curl -s -H "Authorization: Bearer $token" "$(url api "/product/backoffice/products/$product_id")" || true)
    related=$(curl -s -H "Authorization: Bearer $token" \
        "$(url api "/product/backoffice/products/related-products/$product_id")" || true)
    original=$(python -c "import json,sys;print(json.load(sys.stdin)['name'])" <<< "$detail" 2>/dev/null || true)
    if [ -z "$product_id" ] || [ -z "$original" ]; then
        fail "read the Galaxy A55 admin detail (search)"
        return
    fi
    thumb=$(python -c "import json,sys;t=json.load(sys.stdin).get('thumbnailMedia');print(t['id'] if t else '')" <<< "$detail")
    marker="Searchsmoke$$"
    search_put_name() {
        local put
        put=$(product_put_body "$detail" "$related" "$thumb" \
            | python -c "import json,sys;b=json.load(sys.stdin);b['name']=sys.argv[1];print(json.dumps(b))" "$1")
        curl -s -o /dev/null -w '%{http_code}' -X PUT -H "Authorization: Bearer $token" \
            -H 'Content-Type: application/json' -d "$put" "$(url api "/product/backoffice/products/$product_id")" || true
    }
    status=$(search_put_name "$original $marker")
    if [ "$status" != "204" ]; then
        fail "admin renames Galaxy A55 for the search check -> $status"
        return
    fi
    deadline=$((SECONDS + 30))
    found=""
    while [ -z "$found" ] && [ "$SECONDS" -lt "$deadline" ]; do
        found=$(curl -s "$(url api "/search/storefront/catalog-search?keyword=$marker")" \
            | python -c "import json,sys;print(','.join(str(p['id']) for p in json.load(sys.stdin)['products']))" \
            2>/dev/null || true)
        [ "$found" = "$product_id" ] || { found=""; sleep 2; }
    done
    if [ -n "$found" ]; then
        pass "an admin rename is found by search within 30 s (CDC -> search)"
    else
        fail "search did not find the renamed product $product_id within 30 s"
    fi
    status=$(search_put_name "$original")
    if [ "$status" = "204" ]; then pass "restore the Galaxy A55 name"; else fail "restore the Galaxy A55 name '$original' -> $status (fix by hand)"; fi
}

# Slice 3c: tax, stock and a saved address at checkout, end to end. Admin calls go through backoffice-bff, the
# customer's through storefront-bff. Everything is put back afterwards (s3c_cleanup, also from the EXIT trap); only
# the placed order stays (orders cannot be deleted).
S3C_TAX_CLASS="Smoke VAT"
S3C_WAREHOUSE="Smoke Warehouse"
S3C_ADDRESS_NAME="Smoke Tax Address"
S3C_ACTIVE=""
S3C_PRODUCT_ID=""
S3C_ORIG_DETAIL=""
S3C_RELATED=""
S3C_CLASS_ID=""
S3C_RATE_ID=""
S3C_WH_ID=""
S3C_STOCK_ID=""
S3C_WH_UNITS=0
S3C_ADDRESS_ID=""
S3C_LEFTOVER_CLASSES=""
S3C_LOCK="${TMPDIR:-/tmp}/shopapp-smoke-3c.lock"
S3C_LOCKED=""
S3C_JAR=$(mktemp)
trap 's4_cleanup >/dev/null 2>&1 || true; s3c_cleanup >/dev/null 2>&1 || true; s3c_unlock; rm -f "$COOKIE_JAR" "$ADMIN_JAR" "$S3C_JAR"' EXIT

# Two smoke runs at once would read each other's changes as the product's original values: one slice 3c run at a
# time (a lock directory holding the owner's pid; a dead owner's lock is taken over), waiting up to 3 minutes.
s3c_lock() {
    local waited=0 owner
    while ! mkdir "$S3C_LOCK" 2>/dev/null; do
        owner=$(cat "$S3C_LOCK/pid" 2>/dev/null || true)
        if [ -n "$owner" ] && ! kill -0 "$owner" 2>/dev/null; then rm -rf "$S3C_LOCK"; continue; fi
        [ "$waited" -ge 180 ] && return 1
        sleep 3
        waited=$((waited + 3))
    done
    echo $$ > "$S3C_LOCK/pid"
    S3C_LOCKED=yes
}
s3c_unlock() { if [ -n "$S3C_LOCKED" ]; then rm -rf "$S3C_LOCK"; S3C_LOCKED=""; fi; }

s3c_origin() { echo "http://backoffice.${DOMAIN}${BASE_URL_SUFFIX}"; }
# bo <method> <path> [json body]: admin call through backoffice-bff; prints the body, then the status on its own line
bo() {
    local method=$1 path=$2 body=${3:-}
    if [ -n "$body" ]; then
        curl -s -w '\n%{http_code}' -b "$ADMIN_JAR" -H "Origin: $(s3c_origin)" -X "$method" \
            -H 'Content-Type: application/json' -d "$body" "$(url backoffice "$path")" || true
    else
        curl -s -w '\n%{http_code}' -b "$ADMIN_JAR" -H "Origin: $(s3c_origin)" -X "$method" \
            "$(url backoffice "$path")" || true
    fi
}
# sf <method> <path> [json body]: customer call through storefront-bff, same output as bo
sf() {
    local method=$1 path=$2 body=${3:-}
    if [ -n "$body" ]; then
        curl -s -w '\n%{http_code}' -b "$S3C_JAR" -X "$method" -H 'Content-Type: application/json' -d "$body" \
            "$(url storefront "$path")" || true
    else
        curl -s -w '\n%{http_code}' -b "$S3C_JAR" -X "$method" "$(url storefront "$path")" || true
    fi
}
status_of() { tail -n 1 <<< "$1"; }
body_of() { sed '$d' <<< "$1"; }
# json <python expression over d> <<< json: prints the value, empty on any error
json() { python -c "import json,sys
try:
    d=json.load(sys.stdin); v=$1
    print('' if v is None else v)
except Exception:
    print('')" 2>/dev/null || true; }

# Product update body from the original admin detail with another tax class and stock tracking flag.
s3c_product_body() {
    local thumb tax_class=$1 tracking=$2
    thumb=$(json "(d.get('thumbnailMedia') or {}).get('id')" <<< "$S3C_ORIG_DETAIL")
    product_put_body "$S3C_ORIG_DETAIL" "$S3C_RELATED" "$thumb" | python -c "import json,sys
b=json.load(sys.stdin); t=sys.argv[1]; k=sys.argv[2]
b['taxClassId']=int(t) if t not in ('', 'None') else None
b['stockTrackingEnabled']=(k=='True')
print(json.dumps(b))" "$tax_class" "$tracking"
}

# Removes what an interrupted earlier run left behind: smoke warehouses (booked out first) and smoke tax classes.
s3c_preclean() {
    local ids id stock
    ids=$(body_of "$(bo GET /api/inventory/backoffice/warehouses)" \
        | json "' '.join(str(w['id']) for w in d if w['name']=='$S3C_WAREHOUSE')")
    for id in $ids; do
        body_of "$(bo GET "/api/inventory/backoffice/stocks?warehouseId=$id")" \
            | json "'\n'.join(f\"{s['id']} {s['quantity']}\" for s in d if s['quantity'])" \
            | while read -r stock qty; do
                [ -n "$stock" ] && bo PUT /api/inventory/backoffice/stocks \
                    "{\"stockQuantityList\":[{\"stockId\":$stock,\"quantity\":-$qty,\"note\":\"smoke: leftover\"}]}" >/dev/null
            done
        bo DELETE "/api/inventory/backoffice/warehouses/$id" >/dev/null
    done
    ids=$(body_of "$(bo GET /api/tax/backoffice/tax-classes)" \
        | json "' '.join(str(c['id']) for c in d if c['name']=='$S3C_TAX_CLASS')")
    S3C_LEFTOVER_CLASSES=$ids
    for id in $ids; do
        for stock in $(body_of "$(bo GET '/api/tax/backoffice/tax-rates/paging?pageSize=100')" \
            | json "' '.join(str(r['id']) for r in d['taxRateGetDetailContent'] if r['taxClassId']==$id)"); do
            bo DELETE "/api/tax/backoffice/tax-rates/$stock" >/dev/null
        done
        bo DELETE "/api/tax/backoffice/tax-classes/$id" >/dev/null
    done
}

s3c_flow() {
    local base vn_id r detail sellable checkout checkout_id preview order body
    if [ "$(backoffice_auth_status "$ADMIN_JAR" "$(url backoffice "")" || true)" != "200" ] \
        && ! backoffice_login "$ADMIN_JAR" "$(url backoffice "")" admin admin; then
        fail "admin login to the back office (tax and stock)"
        return 1
    fi
    vn_id=$(curl -s "$(url storefront /api/location/storefront/countries)" | json "next(c['id'] for c in d if c['code2']=='VN')")
    S3C_PRODUCT_ID=$(curl -s "$(url storefront /api/product/storefront/product/usb-c-cable)" | json "d['id']")
    if [ -z "$vn_id" ] || [ -z "$S3C_PRODUCT_ID" ]; then
        fail "look up Viet Nam and the USB-C Cable"
        return 1
    fi
    if ! s3c_lock; then
        fail "another smoke run has held the slice 3c lock ($S3C_LOCK) for 3 minutes"
        return 1
    fi
    s3c_preclean
    S3C_ACTIVE=yes

    # admin: tax class + 10 % rate for Viet Nam
    S3C_CLASS_ID=$(body_of "$(bo POST /api/tax/backoffice/tax-classes "{\"name\":\"$S3C_TAX_CLASS\"}")" | json "d['id']")
    [ -n "$S3C_CLASS_ID" ] && S3C_RATE_ID=$(body_of "$(bo POST /api/tax/backoffice/tax-rates \
        "{\"rate\":10,\"taxClassId\":$S3C_CLASS_ID,\"countryId\":$vn_id}")" | json "d['id']")
    if [ -z "$S3C_RATE_ID" ]; then
        fail "admin creates a tax class and a 10 % Viet Nam rate"
        return 1
    fi
    pass "admin creates a tax class and a 10 % Viet Nam rate"

    # admin: product taxed and stock-tracked (all other fields sent back unchanged, 3a-19)
    S3C_ORIG_DETAIL=$(body_of "$(bo GET "/api/product/backoffice/products/$S3C_PRODUCT_ID")")
    S3C_RELATED=$(body_of "$(bo GET "/api/product/backoffice/products/related-products/$S3C_PRODUCT_ID")")
    if [ "$(json "d['id']" <<< "$S3C_ORIG_DETAIL")" != "$S3C_PRODUCT_ID" ] || [ -z "$(json "len(d)+1" <<< "$S3C_RELATED")" ]; then
        S3C_ORIG_DETAIL=""
        fail "read the USB-C Cable admin detail"
        return 1
    fi
    # an interrupted run left the product taxed with a smoke class: its original is the seed (no class, untracked,
    # 100 units in sample-data.sql)
    r=$(json "d['taxClassId']" <<< "$S3C_ORIG_DETAIL")
    if [ -n "$r" ] && [[ " $S3C_LEFTOVER_CLASSES " == *" $r "* ]]; then
        echo "      note: the USB-C Cable still had a leftover smoke tax class; restoring the seed values (no class, untracked, 100)"
        S3C_ORIG_DETAIL=$(json "json.dumps({**d, 'taxClassId': None, 'stockTrackingEnabled': False, 'stockQuantity': 100})" <<< "$S3C_ORIG_DETAIL")
    fi
    r=$(bo PUT "/api/product/backoffice/products/$S3C_PRODUCT_ID" "$(s3c_product_body "$S3C_CLASS_ID" True)")
    if [ "$(status_of "$r")" != "204" ]; then
        fail "admin assigns the tax class and enables stock tracking -> $(status_of "$r")"
        return 1
    fi
    # start from 0 sellable units so the numbers below are exact
    r=$(bo PUT /api/product/backoffice/products/update-quantity "[{\"productId\":$S3C_PRODUCT_ID,\"stockQuantity\":0}]")
    if [ "$(status_of "$r")" = "204" ]; then
        pass "admin assigns the tax class, enables stock tracking and sets the stock to 0"
    else
        fail "admin sets the stock to 0 -> $(status_of "$r")"
        return 1
    fi

    # admin: warehouse in Viet Nam, add the product, receive 3 units
    S3C_WH_ID=$(body_of "$(bo POST /api/inventory/backoffice/warehouses \
        "{\"name\":\"$S3C_WAREHOUSE\",\"contactName\":\"Smoke Keeper\",\"phone\":\"0123456789\",\"addressLine1\":\"1 Depot St\",\"city\":\"Ho Chi Minh City\",\"countryId\":$vn_id}")" \
        | json "d['id']")
    if [ -z "$S3C_WH_ID" ]; then
        fail "admin creates a warehouse in Viet Nam"
        return 1
    fi
    r=$(bo POST /api/inventory/backoffice/stocks "[{\"productId\":$S3C_PRODUCT_ID,\"warehouseId\":$S3C_WH_ID}]")
    S3C_STOCK_ID=$(body_of "$(bo GET "/api/inventory/backoffice/stocks?warehouseId=$S3C_WH_ID")" \
        | json "next(s['id'] for s in d if s['productId']==$S3C_PRODUCT_ID)")
    if [ "$(status_of "$r")" != "204" ] || [ -z "$S3C_STOCK_ID" ]; then
        fail "admin creates a warehouse and adds the USB-C Cable"
        return 1
    fi
    pass "admin creates a warehouse in Viet Nam and adds the USB-C Cable"
    r=$(bo PUT /api/inventory/backoffice/stocks \
        "{\"stockQuantityList\":[{\"stockId\":$S3C_STOCK_ID,\"quantity\":3,\"note\":\"smoke: received\"}]}")
    [ "$(status_of "$r")" = "200" ] && S3C_WH_UNITS=3
    sellable=$(body_of "$(bo GET "/api/product/backoffice/products/$S3C_PRODUCT_ID")" | json "d['stockQuantity']")
    if [ "$S3C_WH_UNITS" = "3" ] && [ "$sellable" = "3" ]; then
        pass "a +3 adjustment adds 3 sellable units (0 -> 3)"
    else
        fail "a +3 adjustment -> status $(status_of "$r"), sellable stock '$sellable', expected 3"
        return 1
    fi

    # customer: saved address in Viet Nam, cart, checkout, preview, order
    base=$(url storefront "")
    if ! storefront_login "$S3C_JAR" "$base" customer customer; then
        fail "customer login (tax and stock)"
        return 1
    fi
    for r in $(body_of "$(sf GET /api/customer/storefront/user-address)" \
        | json "' '.join(str(a['id']) for a in d if a.get('contactName')=='$S3C_ADDRESS_NAME')"); do
        sf DELETE "/api/customer/storefront/user-address/$r" >/dev/null
    done
    S3C_ADDRESS_ID=$(body_of "$(sf POST /api/customer/storefront/user-address \
        "{\"contactName\":\"$S3C_ADDRESS_NAME\",\"phone\":\"0123456789\",\"addressLine1\":\"2 Smoke St\",\"city\":\"Ho Chi Minh City\",\"countryId\":$vn_id}")" \
        | json "d['addressGetVm']['id']")
    if [ -z "$S3C_ADDRESS_ID" ]; then
        fail "customer saves an address in Viet Nam"
        return 1
    fi
    pass "customer saves an address in Viet Nam"
    sf DELETE "/api/cart/storefront/cart/items/$S3C_PRODUCT_ID" >/dev/null
    r=$(sf POST /api/cart/storefront/cart/items "{\"productId\":$S3C_PRODUCT_ID,\"quantity\":5}")
    if [ "$(status_of "$r")" = "400" ]; then pass "adding 5 to the cart with 3 in stock is a 400"; else fail "cart add above stock -> $(status_of "$r")"; fi
    r=$(sf POST /api/cart/storefront/cart/items "{\"productId\":$S3C_PRODUCT_ID,\"quantity\":2}")
    case "$(status_of "$r")" in
        200|201|204) pass "customer adds 2 USB-C Cables to the cart" ;;
        *) fail "cart add 2 -> $(status_of "$r")" ;;
    esac
    checkout_id=$(body_of "$(sf POST /api/order/storefront/checkouts \
        "{\"checkoutItemPostVms\":[{\"productId\":$S3C_PRODUCT_ID,\"quantity\":2}]}")" | json "d['id']")
    preview=$(body_of "$(sf POST /api/order/storefront/orders/preview \
        "{\"checkoutId\":\"$checkout_id\",\"shippingAddressId\":$S3C_ADDRESS_ID}")")
    # money is compared as decimals: total = subtotal x 1.10 (rounded to cents)
    if python -c "import json,sys
from decimal import Decimal as D, ROUND_HALF_UP
p=json.loads(sys.argv[1]); s=D(str(p['subtotal'])); t=D(str(p['total']))
want=(s*D('1.10')).quantize(D('0.01'), ROUND_HALF_UP)
sys.exit(0 if s==D('38') and t.quantize(D('0.01'))==want else 1)" "$preview" 2>/dev/null; then
        pass "preview with the saved address adds 10 % tax (38.00 -> $(json "d['total']" <<< "$preview"))"
    else
        fail "preview with the saved address: ${preview:0:200}"
        return 1
    fi
    order=$(body_of "$(sf POST /api/order/storefront/orders \
        "{\"checkoutId\":\"$checkout_id\",\"shippingAddressId\":$S3C_ADDRESS_ID,\"paymentMethod\":\"COD\"}")")
    if python -c "import json,sys
from decimal import Decimal as D
o=json.loads(sys.argv[1]); p=json.loads(sys.argv[2])
sys.exit(0 if o.get('orderStatus')=='PENDING_PAYMENT' and D(str(o['tax']))>0
         and D(str(o['totalPrice']))==D(str(p['total'])) else 1)" "$order" "$preview" 2>/dev/null; then
        pass "order #$(json "d['id']" <<< "$order") total includes the tax ($(json "d['totalPrice']" <<< "$order"))"
    else
        fail "order with tax: ${order:0:200}"
    fi
    sellable=$(body_of "$(bo GET "/api/product/backoffice/products/$S3C_PRODUCT_ID")" | json "d['stockQuantity']")
    if [ "$sellable" = "1" ]; then pass "the order took 2 units (sellable stock 3 -> 1)"; else fail "sellable stock after the order is '$sellable', expected 1"; fi
    checkout_id=$(body_of "$(sf POST /api/order/storefront/checkouts \
        "{\"checkoutItemPostVms\":[{\"productId\":$S3C_PRODUCT_ID,\"quantity\":2}]}")" | json "d['id']")
    r=$(sf POST /api/order/storefront/orders \
        "{\"checkoutId\":\"$checkout_id\",\"shippingAddressId\":$S3C_ADDRESS_ID,\"paymentMethod\":\"COD\"}")
    if [ "$(status_of "$r")" = "400" ] && grep -q "Not enough stock" <<< "$r"; then
        pass "ordering 2 with 1 left is a 400 not enough stock"
    else
        fail "ordering more than in stock -> $(status_of "$r") $(body_of "$r" | head -c 200)"
    fi
    sellable=$(body_of "$(bo GET "/api/product/backoffice/products/$S3C_PRODUCT_ID")" | json "d['stockQuantity']")
    [ "$sellable" = "1" ] || fail "the refused order changed the stock (sellable '$sellable', expected 1)"
}

# Puts everything back; safe to run twice. Fails loudly when the product could not be restored.
s3c_cleanup() {
    local r
    [ -n "$S3C_ACTIVE" ] || return 0
    S3C_ACTIVE=""
    if [ -n "$S3C_ADDRESS_ID" ]; then
        sf DELETE "/api/cart/storefront/cart/items/$S3C_PRODUCT_ID" >/dev/null
        r=$(sf DELETE "/api/customer/storefront/user-address/$S3C_ADDRESS_ID")
        if [ "$(status_of "$r")" = "200" ]; then pass "cleanup: delete the saved address"; else fail "cleanup: delete the saved address -> $(status_of "$r")"; fi
        S3C_ADDRESS_ID=""
    fi
    curl -s -o /dev/null -L -b "$S3C_JAR" -c "$S3C_JAR" -X POST "$(url storefront /logout)" || true
    if [ -n "$S3C_WH_ID" ]; then
        # book the received units out again, then the empty warehouse can be deleted
        if [ "$S3C_WH_UNITS" != "0" ]; then
            bo PUT /api/inventory/backoffice/stocks \
                "{\"stockQuantityList\":[{\"stockId\":$S3C_STOCK_ID,\"quantity\":-$S3C_WH_UNITS,\"note\":\"smoke: book out\"}]}" >/dev/null
            S3C_WH_UNITS=0
        fi
        r=$(bo DELETE "/api/inventory/backoffice/warehouses/$S3C_WH_ID")
        if [ "$(status_of "$r")" = "204" ]; then pass "cleanup: delete the warehouse"; else fail "cleanup: delete the warehouse -> $(status_of "$r")"; fi
        S3C_WH_ID=""
    fi
    if [ -n "$S3C_ORIG_DETAIL" ]; then
        local orig_stock orig_tax orig_tracking ok=yes
        orig_stock=$(json "d['stockQuantity']" <<< "$S3C_ORIG_DETAIL")
        orig_tax=$(json "d['taxClassId']" <<< "$S3C_ORIG_DETAIL")
        orig_tracking=$(json "d['stockTrackingEnabled']" <<< "$S3C_ORIG_DETAIL")
        if [ -n "$orig_stock" ]; then
            r=$(bo PUT /api/product/backoffice/products/update-quantity "[{\"productId\":$S3C_PRODUCT_ID,\"stockQuantity\":$orig_stock}]")
            [ "$(status_of "$r")" = "204" ] || ok=no
        fi
        r=$(bo PUT "/api/product/backoffice/products/$S3C_PRODUCT_ID" "$(s3c_product_body "$orig_tax" "$orig_tracking")")
        [ "$(status_of "$r")" = "204" ] || ok=no
        if [ "$ok" = "yes" ]; then
            pass "cleanup: USB-C Cable back to tax class ${orig_tax:-none}, stock tracking $orig_tracking, stock ${orig_stock:-none}"
        else
            fail "cleanup: USB-C Cable NOT restored (taxClassId $orig_tax, stockTrackingEnabled $orig_tracking, stockQuantity $orig_stock): fix it in the back office"
        fi
        S3C_ORIG_DETAIL=""
    fi
    if [ -n "$S3C_RATE_ID" ]; then bo DELETE "/api/tax/backoffice/tax-rates/$S3C_RATE_ID" >/dev/null; S3C_RATE_ID=""; fi
    if [ -n "$S3C_CLASS_ID" ]; then
        r=$(bo DELETE "/api/tax/backoffice/tax-classes/$S3C_CLASS_ID")
        if [ "$(status_of "$r")" = "204" ]; then pass "cleanup: delete the tax rate and class"; else fail "cleanup: delete the tax class -> $(status_of "$r")"; fi
        S3C_CLASS_ID=""
    fi
}

check_slice3c() {
    local customer_token
    check_http "countries are public" "$(url api /location/storefront/countries)" 200
    customer_token=$(curl -s -d grant_type=password -d client_id=swagger-ui -d username=customer -d password=customer \
        "$(url identity /realms/shopapp/protocol/openid-connect/token)" | json "d['access_token']" || true)
    check_http "a customer cannot create a tax class" "$(url api /tax/backoffice/tax-classes)" 403 \
        -H "Authorization: Bearer $customer_token" -H 'Content-Type: application/json' -d '{"name":"Customer VAT"}'
    check_http "a customer cannot create a warehouse" "$(url api /inventory/backoffice/warehouses)" 403 \
        -H "Authorization: Bearer $customer_token" -H 'Content-Type: application/json' -d '{"name":"Customer WH"}'
    check_http "a customer cannot create a country" "$(url api /location/backoffice/countries)" 403 \
        -H "Authorization: Bearer $customer_token" -H 'Content-Type: application/json' -d '{"name":"Customerland"}'
    s3c_flow || true
    s3c_cleanup
    s3c_unlock
}

# Slice 4 reviews and coupons. The customer's calls go through storefront-bff (in $S3C_JAR, logged in again), the
# admin's through backoffice-bff. Cleanup (s4_cleanup, also from the EXIT trap): the review is deleted, the coupon
# deactivated (a used promotion cannot be deleted); the coupon orders stay (orders cannot be deleted).
S4_RATING_ID=""
S4_PROMO_ID=""

# Logs the customer into $S3C_JAR. Windows curl keeps its own copy of the jar (storefront_login's rm does not reach
# it) and Keycloak may still have an SSO session, so "already logged in" also counts.
s4_customer_login() {
    storefront_login "$S3C_JAR" "$(url storefront "")" customer customer         || curl -s -b "$S3C_JAR" "$(url storefront /authentication)" | grep -q '"isAuthenticated":true'
}

s4_admin_login() {
    [ "$(backoffice_auth_status "$ADMIN_JAR" "$(url backoffice "")" || true)" = "200" ] \
        || backoffice_login "$ADMIN_JAR" "$(url backoffice "")" admin admin
}

# s4_retire_promotion <id>: deletes an unused promotion, deactivates a used one; prints what it did
s4_retire_promotion() {
    local id=$1 r detail
    r=$(bo DELETE "/api/promotion/backoffice/promotions/$id")
    if [ "$(status_of "$r")" = "204" ]; then echo "deleted"; return 0; fi
    detail=$(body_of "$(bo GET "/api/promotion/backoffice/promotions/$id")")
    r=$(bo PUT /api/promotion/backoffice/promotions "$(python -c "import json,sys
d=json.loads(sys.argv[1]); d['isActive']=False; d.pop('usageCount', None); print(json.dumps(d))" "$detail" 2>/dev/null)")
    if [ "$(status_of "$r")" = "200" ]; then echo "deactivated"; return 0; fi
    return 1
}

s4_cleanup() {
    local r
    if [ -n "$S4_RATING_ID" ]; then
        r=$(sf DELETE "/api/rating/storefront/ratings/$S4_RATING_ID")
        if [ "$(status_of "$r")" = "204" ]; then pass "cleanup: customer deletes their review"; else fail "cleanup: delete review $S4_RATING_ID -> $(status_of "$r")"; fi
        S4_RATING_ID=""
    fi
    if [ -n "$S4_PROMO_ID" ]; then
        if r=$(s4_retire_promotion "$S4_PROMO_ID"); then pass "cleanup: coupon $r"; else fail "cleanup: coupon $S4_PROMO_ID still active (deactivate it in the back office)"; fi
        S4_PROMO_ID=""
    fi
}

# Slice 4 reviews (decisions.md 4-30..4-39): check_checkout_and_order orders Galaxy S24 Violet on every run, and a
# variation's parent counts as bought (4-30), so the customer may review Galaxy S24.
check_rating() {
    local product_id sub customer_token r other_id html avg
    customer_token=$(curl -s -d grant_type=password -d client_id=swagger-ui -d username=customer -d password=customer \
        "$(url identity /realms/shopapp/protocol/openid-connect/token)" | json "d['access_token']")
    sub=$(python -c "import base64,json,sys
p=sys.argv[1].split('.')[1]; print(json.loads(base64.urlsafe_b64decode(p + '=' * (-len(p) % 4)))['sub'])" \
        "$customer_token" 2>/dev/null || true)
    product_id=$(curl -s "$(url storefront /api/product/storefront/product/galaxy-s24)" | json "d['id']")
    if [ -z "$sub" ] || [ -z "$product_id" ] || ! s4_customer_login || ! s4_admin_login; then
        fail "look up Galaxy S24, log the customer and the admin in (reviews)"
        return
    fi
    # a review left by an interrupted run (one per customer and product, 4-32) is deleted first
    for r in $(curl -s "$(url storefront "/api/rating/storefront/ratings/products/$product_id?pageSize=50")" \
        | json "' '.join(str(x['id']) for x in d['ratingList'] if x['createdBy']=='$sub')"); do
        echo "      note: deleting review #$r left by an earlier run"
        sf DELETE "/api/rating/storefront/ratings/$r" >/dev/null
    done
    r=$(sf POST /api/rating/storefront/ratings "{\"productId\":$product_id,\"star\":4,\"content\":\"Smokereview$$\"}")
    S4_RATING_ID=$(body_of "$r" | json "d['id']")
    if [ "$(status_of "$r")" = "201" ] && [ -n "$S4_RATING_ID" ]; then
        pass "customer reviews Galaxy S24, bought as the Violet variation (201)"
    else
        S4_RATING_ID=""
        fail "customer reviews a bought product -> $(status_of "$r") $(body_of "$r" | head -c 200)"
        return
    fi
    r=$(sf POST /api/rating/storefront/ratings "{\"productId\":$product_id,\"star\":5,\"content\":\"Again\"}")
    if [ "$(status_of "$r")" = "409" ]; then pass "a second review of the same product is a 409"; else fail "second review -> $(status_of "$r")"; fi
    avg=$(curl -s "$(url storefront "/api/rating/storefront/ratings/product/$product_id/average-star")" || true)
    if python -c "import sys;sys.exit(0 if float(sys.argv[1])>0 else 1)" "$avg" 2>/dev/null; then
        pass "Galaxy S24 average is $avg stars"
    else
        fail "Galaxy S24 average '$avg', expected > 0"
    fi
    html=$(curl -s "$(url storefront /products/galaxy-s24)" || true)
    if grep -qF 'Reviews</h2>' <<< "$html" && grep -qF 'data-testid="average-rating"' <<< "$html"; then
        pass "product page shows the Reviews section with the average"
    else
        fail "product page /products/galaxy-s24 has no Reviews section with an average"
    fi
    if body_of "$(bo GET "/api/rating/backoffice/ratings?message=Smokereview$$")" | grep -q "\"id\":$S4_RATING_ID,"; then
        pass "admin finds the review in the back-office list"
    else
        fail "admin back-office review list does not show review #$S4_RATING_ID"
    fi
    # a product the customer never ordered: the first listed one order says is not bought
    other_id=""
    for r in $(curl -s "$(url storefront '/api/product/storefront/products?pageSize=100')" \
        | json "' '.join(str(p['id']) for p in d['productContent'])"); do
        if [ "$(body_of "$(sf GET "/api/order/storefront/orders/completed?productId=$r")" | json "d['isPresent']")" = "False" ]; then
            other_id=$r
            break
        fi
    done
    if [ -z "$other_id" ]; then
        fail "no published product the customer has not ordered (cannot check the not-bought rule)"
    else
        r=$(sf POST /api/rating/storefront/ratings "{\"productId\":$other_id,\"star\":3,\"content\":\"Never bought\"}")
        if [ "$(status_of "$r")" = "400" ] && grep -q "Only customers who bought" <<< "$r"; then
            pass "reviewing a product never bought (#$other_id) is a 400 (only buyers may review)"
        else
            fail "review of a product never bought -> $(status_of "$r") $(body_of "$r" | head -c 200)"
        fi
    fi
    r=$(sf DELETE "/api/rating/storefront/ratings/$S4_RATING_ID")
    if [ "$(status_of "$r")" = "204" ]; then
        S4_RATING_ID=""
        pass "customer deletes their own review (204)"
    else
        fail "customer deletes their own review -> $(status_of "$r")"
    fi
    check_http "a customer cannot list all reviews" "$(url api /rating/backoffice/ratings)" 403 \
        -H "Authorization: Bearer $customer_token"
    check_body "back office reviews page" "$(url backoffice /reviews)" 200 'id="__next"' -b "$ADMIN_JAR"
}

# Slice 4 coupons (decisions.md 4-40..4-59): a 10 % coupon for Galaxy S24 Violet only, usage limit 1. The checkout
# also has a USB-C Cable, which gets no discount. Galaxy S24 (the Violet's parent; a variation has no admin detail)
# and the cable keep their stock and tax class (checked).
check_promotion() {
    local violet_id cable_id s24_id vn_id code dates body r checkout_id preview order address state_before state_after
    s24_id=$(curl -s "$(url storefront /api/product/storefront/product/galaxy-s24)" | json "d['id']")
    violet_id=$(curl -s "$(url storefront "/api/product/storefront/product-variations/$s24_id")" \
        | json "next(v['id'] for v in d if v['slug']=='galaxy-s24-violet')")
    cable_id=$(curl -s "$(url storefront /api/product/storefront/product/usb-c-cable)" | json "d['id']")
    vn_id=$(curl -s "$(url storefront /api/location/storefront/countries)" | json "next(c['id'] for c in d if c['code2']=='VN')")
    if [ -z "$violet_id" ] || [ -z "$cable_id" ] || [ -z "$vn_id" ]; then
        fail "look up Galaxy S24 Violet, the USB-C Cable and Viet Nam (coupons)"
        return
    fi
    if ! s4_admin_login || ! s4_customer_login; then
        fail "admin and customer logins (coupons)"
        return
    fi
    # coupons left active by an interrupted run
    for r in $(body_of "$(bo GET '/api/promotion/backoffice/promotions?couponCode=SMOKE&pageSize=100')" \
        | json "' '.join(str(p['id']) for p in d['promotionDetailVmList'] if p['isActive'] and p['couponCode'].startswith('SMOKE'))"); do
        echo "      note: coupon #$r left by an earlier run $(s4_retire_promotion "$r" || echo 'could not be retired')"
    done
    product_state() {
        local id
        for id in "$s24_id" "$cable_id"; do
            body_of "$(bo GET "/api/product/backoffice/products/$id")" \
                | json "f\"{d['id']}:{d['stockQuantity']}:{d['taxClassId']}:{d['stockTrackingEnabled']}\""
        done | paste -sd ' ' -
    }
    state_before=$(product_state)

    code="SMOKE$(date +%s)"
    dates=$(python -c "from datetime import datetime,timedelta,timezone as t
n=datetime.now(t.utc).replace(microsecond=0); f=lambda x:x.isoformat().replace('+00:00','Z')
print(f(n-timedelta(minutes=5)), f(n+timedelta(days=1)))")
    body="{\"name\":\"Smoke coupon $code\",\"slug\":\"$(tr 'A-Z' 'a-z' <<< "$code")\",\"description\":\"verify-foundation.sh\",\"couponCode\":\"$code\",\"discountType\":\"PERCENTAGE\",\"discountPercentage\":10,\"discountAmount\":null,\"usageType\":\"LIMITED\",\"usageLimit\":1,\"minimumOrderPurchaseAmount\":0,\"isActive\":true,\"startDate\":\"${dates% *}\",\"endDate\":\"${dates#* }\",\"applyTo\":\"PRODUCT\",\"productIds\":[$violet_id],\"brandIds\":[],\"categoryIds\":[]}"
    r=$(bo POST /api/promotion/backoffice/promotions "$body")
    S4_PROMO_ID=$(body_of "$r" | json "d['id']")
    if [ "$(status_of "$r")" = "201" ] && [ -n "$S4_PROMO_ID" ]; then
        pass "admin creates coupon $code (10 % on Galaxy S24 Violet, usage limit 1)"
    else
        S4_PROMO_ID=""
        fail "admin creates a coupon -> $(status_of "$r") $(body_of "$r" | head -c 200)"
        return
    fi

    address="{\"contactName\":\"Smoke Coupon\",\"phone\":\"0123456789\",\"addressLine1\":\"3 Smoke St\",\"city\":\"Ho Chi Minh City\",\"countryId\":$vn_id}"
    checkout_id=$(body_of "$(sf POST /api/order/storefront/checkouts \
        "{\"checkoutItemPostVms\":[{\"productId\":$violet_id,\"quantity\":1},{\"productId\":$cable_id,\"quantity\":1}]}")" | json "d['id']")
    preview=$(body_of "$(sf POST /api/order/storefront/orders/preview \
        "{\"checkoutId\":\"$checkout_id\",\"shippingAddressPostVm\":$address,\"couponCode\":\"$(tr 'A-Z' 'a-z' <<< "$code")\"}")")
    if python -c "import json,sys
from decimal import Decimal as D, ROUND_HALF_UP
p=json.loads(sys.argv[1]); v=int(sys.argv[2]); c=int(sys.argv[3])
line={i['productId']: i for i in p['items']}
want=(D(str(line[v]['subtotal']))*D('0.10')).quantize(D('0.01'), ROUND_HALF_UP)
sys.exit(0 if want>0 and D(str(line[v]['discountAmount']))==want and D(str(line[c]['discountAmount']))==0
         and D(str(p['discount']))==want and p['couponCode']==sys.argv[4]
         and D(str(p['total']))==D(str(p['subtotal']))-want+D(str(p['totalTax'])) else 1)" \
        "$preview" "$violet_id" "$cable_id" "$code" 2>/dev/null; then
        pass "checkout preview with the coupon: discount $(json "d['discount']" <<< "$preview") = 10 % of the Violet line only"
    else
        fail "checkout preview with the coupon: ${preview:0:300}"
    fi
    order=$(body_of "$(sf POST /api/order/storefront/orders \
        "{\"checkoutId\":\"$checkout_id\",\"shippingAddressPostVm\":$address,\"paymentMethod\":\"COD\",\"couponCode\":\"$code\"}")")
    if python -c "import json,sys
from decimal import Decimal as D
o=json.loads(sys.argv[1]); p=json.loads(sys.argv[2])
sys.exit(0 if o.get('couponCode')==p['couponCode'] and D(str(o['discount']))==D(str(p['discount']))
         and D(str(o['totalPrice']))==D(str(p['total'])) else 1)" "$order" "$preview" 2>/dev/null; then
        pass "order #$(json "d['id']" <<< "$order") placed with the coupon at the preview's total ($(json "d['totalPrice']" <<< "$order"))"
    else
        fail "order with the coupon: ${order:0:300}"
    fi
    r=$(body_of "$(bo GET "/api/promotion/backoffice/promotions/$S4_PROMO_ID")" | json "d['usageCount']")
    if [ "$r" = "1" ]; then pass "coupon usage is 1 after the order"; else fail "coupon usage is '$r' after the order, expected 1"; fi
    checkout_id=$(body_of "$(sf POST /api/order/storefront/checkouts \
        "{\"checkoutItemPostVms\":[{\"productId\":$violet_id,\"quantity\":1}]}")" | json "d['id']")
    r=$(sf POST /api/order/storefront/orders \
        "{\"checkoutId\":\"$checkout_id\",\"shippingAddressPostVm\":$address,\"paymentMethod\":\"COD\",\"couponCode\":\"$code\"}")
    if [ "$(status_of "$r")" = "400" ] && [ "$(body_of "$r" | json "d['title']")" = "PROMOTION_USED_UP" ]; then
        pass "a second order with the limit-1 coupon is a 400 PROMOTION_USED_UP"
    else
        fail "second order with the used coupon -> $(status_of "$r") $(body_of "$r" | head -c 200)"
    fi
    state_after=$(product_state)
    if [ "$state_after" = "$state_before" ] && [ "$(wc -w <<< "$state_before")" = "2" ]; then
        pass "Galaxy S24 (Violet's parent) and USB-C Cable keep their stock and tax class (id:stock:taxClass:tracked $state_after)"
    else
        fail "product state changed by the coupon check: before '$state_before', after '$state_after'"
    fi
    r=$(s4_retire_promotion "$S4_PROMO_ID") && S4_PROMO_ID=""
    if [ -z "$S4_PROMO_ID" ] && [ "$r" = "deactivated" ]; then pass "admin deactivates the used coupon"; else fail "deactivate the used coupon -> ${r:-failed}"; fi
    check_body "back office promotions page" "$(url backoffice /promotion/manager-promotion)" 200 'id="__next"' -b "$ADMIN_JAR"
}

check_slice4() {
    check_rating
    check_promotion
    s4_cleanup
    curl -s -o /dev/null -L -b "$S3C_JAR" -c "$S3C_JAR" -X POST "$(url storefront /logout)" || true
    check_body "storefront search page lists Galaxy phones" "$(url storefront '/search?keyword=galaxy')" 200 \
        'href="/products/galaxy-'
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
    check_backoffice
    check_media
    check_slice3c
    check_cdc
    check_search
    check_slice4
}

echo "Using nginx port ${PORT}, domain ${DOMAIN}"
check_line_endings
if check_hosts_resolve; then
    run_checks
    if [ "${1:-}" = "--restart" ]; then
        echo "Restarting the stack (docker compose down && up, volumes kept)..."
        docker compose down >/dev/null 2>&1
        # `up --wait` stops waiting (and starting) as soon as any container fails, also one this script does not
        # check (a service still under development) or after a Docker VM hiccup ("timed out dialing Hyper-V
        # socket"): then start the checked services and give them up to 5 minutes to become healthy
        if ! docker compose up -d --wait >/dev/null 2>&1; then
            docker compose up -d "${SERVICES[@]}" >/dev/null 2>&1 || true
            deadline=$((SECONDS + 300))
            while [ "$SECONDS" -lt "$deadline" ]; do
                statuses=$(docker compose ps "${SERVICES[@]}" --format '{{.Status}}' 2>/dev/null || true)
                [ "$(grep -c '^Up' <<< "$statuses")" -ge "${#SERVICES[@]}" ] \
                    && ! grep -qE 'starting|unhealthy' <<< "$statuses" && break
                sleep 5
            done
        fi
        check_services_healthy
        check_token
    fi
fi

echo
if [ "$failures" -eq 0 ]; then echo "All checks passed."; else echo "$failures check(s) failed."; fi
exit $((failures > 0))
