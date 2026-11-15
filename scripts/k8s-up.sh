#!/usr/bin/env bash
# Runs shop_app on a local kind cluster: cluster, ingress-nginx, CloudNativePG, infrastructure (k8s/deploy), Secrets
# from .env, then the shopapp umbrella chart; waits until everything is ready (decisions.md 6-20..6-39, k8s/README.md).
#   bash scripts/k8s-up.sh
# Settings (environment):
#   K8S_PROFILE=full|core   core = Postgres, Keycloak, Redis + the slice 1-2 shopping path (values-core.yaml);
#                           full (default) = everything, also Kafka, Kafka Connect, Elasticsearch
#   K8S_HTTP_PORT=8000      host port of the ingress (127.0.0.1); compose's nginx already uses 80
#   K8S_IMAGES=ghcr|local   ghcr (default): ghcr.io/nduyhaing/shopapp-<svc>:${K8S_IMAGE_TAG:-latest};
#                           local: the compose images shopapp/<svc>:dev (build them first), loaded into kind
#   GHCR_USER, GHCR_TOKEN   pull credentials for private GHCR packages (token with read:packages); optional
#   K8S_WITH_KAFKA_UI=1     also Kafka UI behind kafka.<domain> (full profile)
#   K8S_CLUSTER=shopapp     kind cluster name
# Needs: docker, kind, kubectl, helm on PATH. Hosts file: the compose hostnames, pointing at 127.0.0.1.
set -euo pipefail

cd "$(dirname "$0")/.."
export MSYS_NO_PATHCONV=1

PROFILE="${K8S_PROFILE:-full}"
HTTP_PORT="${K8S_HTTP_PORT:-8000}"
IMAGES="${K8S_IMAGES:-ghcr}"
IMAGE_TAG="${K8S_IMAGE_TAG:-latest}"
CLUSTER="${K8S_CLUSTER:-shopapp}"
NS=shopapp
INGRESS_NGINX_VERSION=controller-v1.15.1
CNPG_VERSION=1.30.1
case "$PROFILE" in full|core) ;; *) echo "K8S_PROFILE must be full or core" >&2; exit 1 ;; esac

set -a
# shellcheck disable=SC1091
source .env
set +a
DOMAIN="${DOMAIN:-shopapp.local}"
SUFFIX=""
[ "$HTTP_PORT" != "80" ] && SUFFIX=":$HTTP_PORT"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

step() { echo; echo "== $*"; }
for tool in docker kind kubectl helm; do
    command -v "$tool" > /dev/null || { echo "k8s-up: $tool is not on PATH" >&2; exit 1; }
done

step "kind cluster $CLUSTER (ingress on 127.0.0.1:$HTTP_PORT)"
if kind get clusters 2> /dev/null | grep -qx "$CLUSTER"; then
    echo "exists"
else
    sed -e "s/hostPort: 8000/hostPort: $HTTP_PORT/" -e "s/containerPort: 8000/containerPort: $HTTP_PORT/" \
        k8s/deploy/kind/cluster.yaml > "$TMP/cluster.yaml"
    kind create cluster --name "$CLUSTER" --config "$TMP/cluster.yaml" --wait 120s
fi
kubectl config use-context "kind-$CLUSTER" > /dev/null
KC="kubectl --context kind-$CLUSTER"

step "ingress-nginx $INGRESS_NGINX_VERSION"
$KC apply -f "https://raw.githubusercontent.com/kubernetes/ingress-nginx/$INGRESS_NGINX_VERSION/deploy/static/provider/kind/deploy.yaml" > /dev/null
sed "s/port: 8000/port: $HTTP_PORT/" k8s/deploy/ingress-nginx.yaml | $KC apply -f - > /dev/null
$KC -n ingress-nginx patch configmap ingress-nginx-controller --type merge \
    -p '{"data":{"proxy-set-headers":"ingress-nginx/shopapp-proxy-headers"}}' > /dev/null
# The controller listens on the public port itself (kind maps host:PORT -> node:PORT -> hostPort PORT), so
# X-Forwarded-Port is the browser's port: the BFFs (forward-headers-strategy framework) build their login redirect
# URIs from it, and with the kind manifest's port 80 they pointed at :80 (decisions.md 6-61)
if ! $KC -n ingress-nginx get deployment ingress-nginx-controller \
    -o jsonpath='{.spec.template.spec.containers[0].args}' | grep -q -- '--http-port='; then
    c=/spec/template/spec/containers/0
    $KC -n ingress-nginx patch deployment ingress-nginx-controller --type json -p "[
        {\"op\": \"test\", \"path\": \"$c/ports/0/name\", \"value\": \"http\"},
        {\"op\": \"add\", \"path\": \"$c/args/-\", \"value\": \"--http-port=$HTTP_PORT\"},
        {\"op\": \"replace\", \"path\": \"$c/ports/0/containerPort\", \"value\": $HTTP_PORT},
        {\"op\": \"replace\", \"path\": \"$c/ports/0/hostPort\", \"value\": $HTTP_PORT}]" > /dev/null
fi
$KC -n ingress-nginx rollout status deployment/ingress-nginx-controller --timeout=300s

step "CoreDNS: *.$DOMAIN -> ingress (pods use the public URLs, like compose's nginx aliases)"
corefile=$($KC -n kube-system get configmap coredns -o jsonpath='{.data.Corefile}')
if ! grep -q shopapp-edge <<< "$corefile"; then
    domain_re=${DOMAIN//./\\.}
    rule="    rewrite name regex (.+\\.)?${domain_re}\\.?\$ shopapp-edge.ingress-nginx.svc.cluster.local answer auto"
    awk -v rule="$rule" '{ print } /^[[:space:]]*ready/ && !done { print rule; done = 1 }' <<< "$corefile" > "$TMP/Corefile"
    $KC -n kube-system create configmap coredns --from-file=Corefile="$TMP/Corefile" --dry-run=client -o yaml \
        | $KC apply -f - > /dev/null
    $KC -n kube-system rollout restart deployment/coredns > /dev/null
fi
$KC -n kube-system rollout status deployment/coredns --timeout=120s

step "CloudNativePG $CNPG_VERSION"
$KC apply --server-side -f \
    "https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/release-${CNPG_VERSION%.*}/releases/cnpg-$CNPG_VERSION.yaml" > /dev/null
$KC -n cnpg-system rollout status deployment/cnpg-controller-manager --timeout=300s

step "namespace $NS, Secrets from .env, ConfigMaps from the repository files"
$KC apply -f k8s/deploy/namespace.yaml > /dev/null
bash k8s/scripts/make-secrets.sh .env "$NS"
configmap() {
    local name=$1
    shift
    $KC -n "$NS" create configmap "$name" "$@" --dry-run=client -o yaml | $KC apply -f - > /dev/null
    echo "configmap $NS/$name"
}
# postgres_init.sql + every file of docker/postgres (debezium-*.sql, sampledata-*.sql, ...)
configmap shopapp-postgres-sql --from-file=postgres_init.sql --from-file=docker/postgres/
configmap keycloak-realm --from-file=identity/realm-export.json
# DOMAIN feeds the realm's redirect URIs (http://storefront.${DOMAIN}/*): with a port they must carry it too
configmap keycloak-env --from-literal=KC_HOSTNAME="http://identity.$DOMAIN$SUFFIX" --from-literal=DOMAIN="$DOMAIN$SUFFIX"
if [ "$PROFILE" = full ]; then
    configmap elasticsearch-shopapp --from-file=docker/elasticsearch/entrypoint.sh --from-file=docker/elasticsearch/roles.yml
    configmap kafka-connect-init --from-file=kafka/register-connectors.sh
    configmap kafka-connectors --from-file=kafka/connects/
fi

step "infrastructure ($PROFILE)"
$KC apply -f k8s/deploy/postgres.yaml -f k8s/deploy/redis.yaml -f k8s/deploy/keycloak.yaml > /dev/null
if [ "$PROFILE" = full ]; then
    $KC apply -f k8s/deploy/kafka.yaml -f k8s/deploy/elasticsearch.yaml -f k8s/deploy/db-init-jobs.yaml > /dev/null
    [ "${K8S_WITH_KAFKA_UI:-0}" = 1 ] && $KC apply -f k8s/deploy/kafka-ui.yaml > /dev/null
fi
$KC -n "$NS" wait cluster.postgresql.cnpg.io/shopapp-postgres --for=condition=Ready --timeout=600s
$KC -n "$NS" wait job/postgres-init --for=condition=Complete --timeout=600s
$KC -n "$NS" rollout status statefulset/redis --timeout=300s
$KC -n "$NS" rollout status deployment/identity --timeout=600s
if [ "$PROFILE" = full ]; then
    $KC -n "$NS" rollout status statefulset/kafka --timeout=600s
    $KC -n "$NS" rollout status statefulset/elasticsearch --timeout=600s
    $KC -n "$NS" rollout status deployment/kafka-connect --timeout=600s
fi

step "chart dependencies"
for chart in k8s/charts/*/; do
    case "$(basename "$chart")" in shopapp-service|shopapp) continue ;; esac
    helm dependency update --skip-refresh "$chart" > /dev/null
done
helm dependency update --skip-refresh k8s/charts/shopapp > /dev/null

step "images ($IMAGES)"
chart_args=(--namespace "$NS" --set "global.domain=$DOMAIN" --set "global.httpPort=$HTTP_PORT")
[ "$PROFILE" = core ] && chart_args+=(-f k8s/charts/shopapp/values-core.yaml)
[ "${K8S_WITH_KAFKA_UI:-0}" = 1 ] && chart_args+=(--set ingress.kafkaUi.enabled=true)
if [ "$IMAGES" = local ]; then
    chart_args+=(--set global.imageRegistry=shopapp --set global.imageNamePrefix= --set global.imageTag=dev)
    # every image the release uses: the rendered Deployments, minus edge-deny (pulled from Docker Hub)
    mapfile -t local_images < <(helm template shopapp k8s/charts/shopapp "${chart_args[@]}" 2> /dev/null \
        | sed -nE 's/^ *image: "?(shopapp\/[^"]+)"?$/\1/p' | sort -u)
    for image in "${local_images[@]}"; do
        docker image inspect "$image" > /dev/null 2>&1 \
            || { echo "k8s-up: $image is missing; build it with docker compose build first" >&2; exit 1; }
        kind load docker-image --name "$CLUSTER" "$image"
    done
else
    chart_args+=(--set "global.imageTag=$IMAGE_TAG")
    # GHCR packages are private by default: with GHCR_USER + GHCR_TOKEN (a token with read:packages) the pods pull
    # with the Secret ghcr-pull; without them the packages must be public. The token goes to kubectl in a file.
    if [ -n "${GHCR_TOKEN:-}" ]; then
        auth=$(printf '%s:%s' "${GHCR_USER:?GHCR_USER is needed with GHCR_TOKEN}" "$GHCR_TOKEN" | base64 | tr -d '\n')
        printf '{"auths":{"ghcr.io":{"auth":"%s"}}}' "$auth" > "$TMP/dockerconfig.json"
        $KC -n "$NS" create secret generic ghcr-pull --type kubernetes.io/dockerconfigjson \
            --from-file=.dockerconfigjson="$TMP/dockerconfig.json" --dry-run=client -o yaml | $KC apply -f - > /dev/null
        chart_args+=(--set "global.imagePullSecrets={ghcr-pull}")
        echo "secret $NS/ghcr-pull"
    fi
fi

step "shopapp chart"
helm upgrade --install shopapp k8s/charts/shopapp --kube-context "kind-$CLUSTER" "${chart_args[@]}" \
    --wait --timeout 20m

if [ "$PROFILE" = full ]; then
    step "CDC: database logins and connector"
    $KC -n "$NS" wait job/debezium-db-init job/sampledata-db-init --for=condition=Complete --timeout=600s
    $KC -n "$NS" rollout status deployment/kafka-connect-init --timeout=600s
fi

step "ready"
echo "storefront  http://storefront.$DOMAIN$SUFFIX"
echo "back office http://backoffice.$DOMAIN$SUFFIX"
echo "identity    http://identity.$DOMAIN$SUFFIX"
echo "smoke and troubleshooting: k8s/README.md"
