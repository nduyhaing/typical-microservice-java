#!/usr/bin/env bash
# Tests for changed-modules.sh (decisions.md 6-69): builds throw-away git repositories with a small root pom and
# checks which modules a change selects. Run by Backend CI's changes job and by hand:
#   bash .github/scripts/changed-modules.test.sh
set -euo pipefail

SCRIPT="$(cd "$(dirname "$0")" && pwd)/changed-modules.sh"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
failures=0

# new_repo: a repository with modules common-library, product, cart, sampledata and one commit; sets $base.
# Runs in this shell (never in $(...)): every later step must stay inside $WORK/repo.
new_repo() {
    rm -rf "$WORK/repo"
    mkdir -p "$WORK/repo"
    cd "$WORK/repo"
    git init -q
    git config user.email test@example.com
    git config user.name test
    printf '<modules>\n<module>common-library</module>\n<module>product</module>\n<module>cart</module>\n<module>sampledata</module>\n</modules>\n' > pom.xml
    for m in common-library product cart sampledata; do mkdir -p "$m/src/main/java"; echo "$m" > "$m/src/main/java/A.java"; done
    mkdir -p product/src/main/resources/db/changelog/data docker/postgres
    echo "-- seed" > product/src/main/resources/db/changelog/data/sample-data.sql
    echo "-- grants" > docker/postgres/sampledata-grants.sql
    echo "-- debezium" > docker/postgres/debezium-order.sql
    git add -A
    git commit -qm base
    base=$(git rev-parse HEAD)
}

# expect <description> <expected json> <file to change>...
expect() {
    local description=$1 want=$2 got
    shift 2
    new_repo
    [ "$(pwd)" = "$WORK/repo" ] || { echo "not in the test repo: $(pwd)" >&2; exit 2; }
    for file in "$@"; do mkdir -p "$(dirname "$file")"; echo change >> "$file"; done
    git add -A
    git commit -qm change
    got=$(bash "$SCRIPT" "$base" HEAD)
    if [ "$got" = "$want" ]; then
        echo "ok   - $description"
    else
        echo "FAIL - $description: want $want, got $got"
        failures=$((failures + 1))
    fi
}

expect "a module's own source selects only it" '["cart"]' cart/src/main/java/A.java
expect "common-library selects every module" '["common-library","product","cart","sampledata"]' \
    common-library/src/main/java/A.java
expect "product's seed changeset also selects sampledata (bundled by sampledata's pom)" '["product","sampledata"]' \
    product/src/main/resources/db/changelog/data/sample-data.sql
expect "product's changelog also selects sampledata (its ITs build the product schema)" '["product","sampledata"]' \
    product/src/main/resources/db/changelog/ddl/changelog-0099.sql
expect "product code does not select sampledata" '["product"]' product/src/main/java/A.java
expect "docker/postgres/sampledata-* selects sampledata" '["sampledata"]' docker/postgres/sampledata-grants.sql
expect "other docker/postgres files select nothing" '[]' docker/postgres/debezium-order.sql
expect "a doc change selects nothing" '[]' README.md

if [ "$failures" -gt 0 ]; then
    echo "$failures failure(s)"
    exit 1
fi
echo "All changed-modules tests passed."
