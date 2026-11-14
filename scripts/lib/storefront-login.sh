#!/usr/bin/env bash
# Logs a user into the storefront through storefront-bff + Keycloak like a browser does, keeping cookies in a jar.
# Usage: storefront_login <cookie-jar> <storefront-base-url> <username> <password>
# Returns 0 when the BFF session is logged in afterwards.
storefront_login() {
    local jar=$1 base=$2 username=$3 password=$4 login_page action
    rm -f "$jar"
    # BFF -> Keycloak login page (follows the redirect, keeps the BFF session and Keycloak cookies)
    login_page=$(curl -s -L -c "$jar" -b "$jar" "$base/oauth2/authorization/keycloak")
    action=$(python -c "import html,re,sys;m=re.search(r'id=\"kc-form-login\"[^>]*action=\"([^\"]+)\"',sys.stdin.read());print(html.unescape(m.group(1)) if m else '')" <<< "$login_page")
    [ -n "$action" ] || return 1
    # submit the form; Keycloak redirects back to the BFF callback, which completes the login
    curl -s -L -o /dev/null -c "$jar" -b "$jar" --data-urlencode "username=$username" \
        --data-urlencode "password=$password" "$action"
    curl -s -b "$jar" "$base/authentication" | grep -q '"isAuthenticated":true'
}
