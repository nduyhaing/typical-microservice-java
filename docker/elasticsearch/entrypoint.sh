#!/bin/bash
# Creates (or re-creates, so a changed password applies) the file-realm user the search service logs in with, then
# starts Elasticsearch as the official image does (decisions.md 4-17). The password comes from the environment.
set -euo pipefail
: "${SEARCH_ES_USERNAME:?}" "${SEARCH_ES_PASSWORD:?}"
cp /shopapp/roles.yml /usr/share/elasticsearch/config/roles.yml
elasticsearch-users userdel "$SEARCH_ES_USERNAME" > /dev/null 2>&1 || true
elasticsearch-users useradd "$SEARCH_ES_USERNAME" -p "$SEARCH_ES_PASSWORD" -r search_writer > /dev/null
exec /usr/local/bin/docker-entrypoint.sh eswrapper
