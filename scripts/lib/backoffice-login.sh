#!/usr/bin/env bash
# Logs a user into the back office through backoffice-bff + Keycloak like a browser does, keeping cookies in a jar.
# Usage: backoffice_login <cookie-jar> <backoffice-base-url> <username> <password>
# Returns 0 when the BFF session is logged in as an admin afterwards (GET /authentication answers 200).
# A non-admin completes the Keycloak login too, but the BFF then answers 403: use backoffice_auth_status to check.
backoffice_login() {
    local jar=$1 base=$2 username=$3 password=$4 login_page action
    rm -f "$jar"
    # BFF -> Keycloak login page (follows the redirect, keeps the BFF session and Keycloak cookies)
    login_page=$(curl -s -L -c "$jar" -b "$jar" "$base/oauth2/authorization/keycloak")
    action=$(python -c "import html,re,sys;m=re.search(r'id=\"kc-form-login\"[^>]*action=\"([^\"]+)\"',sys.stdin.read());print(html.unescape(m.group(1)) if m else '')" <<< "$login_page")
    [ -n "$action" ] || return 1
    # submit the form; Keycloak redirects back to the BFF callback, which completes the login
    # (the page it then lands on may answer 403 for non-admins, or 502 while the back office app is down)
    curl -s -L -o /dev/null -c "$jar" -b "$jar" --data-urlencode "username=$username" \
        --data-urlencode "password=$password" "$action" || true
    [ "$(backoffice_auth_status "$jar" "$base")" = "200" ]
}

# HTTP status of GET /authentication for the session in the jar: 200 admin, 403 logged-in non-admin,
# 302 not logged in.
backoffice_auth_status() {
    local jar=$1 base=$2
    curl -s -o /dev/null -w '%{http_code}' -b "$jar" "$base/authentication"
}
