#!/usr/bin/env bash
# Builds the chart dependencies, lints every chart and validates the rendered manifests with kubeconform
# (decisions.md 6-20..6-39). Used by hand and by CI.
#   bash k8s/scripts/validate-charts.sh            # charts + k8s/deploy manifests
# Needs: helm (3.8+ or 4), kubeconform on PATH (or KUBECONFORM=/path/to/kubeconform).
# CRD schemas (CloudNativePG Cluster) come from the datreeio CRDs-catalog; offline, unknown kinds are skipped.
set -euo pipefail

cd "$(dirname "$0")/../.."
CHARTS=k8s/charts
KUBECONFORM="${KUBECONFORM:-kubeconform}"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

kubeconform_run() {
    "$KUBECONFORM" -strict -summary -ignore-missing-schemas \
        -schema-location default \
        -schema-location 'https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json' \
        "$@"
}

# thin charts first (each vendors the library chart), then the umbrella (vendors the thin charts)
services=()
for chart in "$CHARTS"/*/; do
    name=$(basename "$chart")
    case "$name" in shopapp-service|shopapp) continue ;; esac
    services+=("$name")
done

echo "== helm lint (library)"
helm lint "$CHARTS/shopapp-service"

for name in "${services[@]}"; do
    echo "== $name"
    helm dependency update --skip-refresh "$CHARTS/$name" > /dev/null
    helm lint --quiet "$CHARTS/$name"
    helm template "$name" "$CHARTS/$name" > "$OUT/$name.yaml"
done

echo "== shopapp (umbrella)"
helm dependency update --skip-refresh "$CHARTS/shopapp" > /dev/null
helm lint "$CHARTS/shopapp"
helm lint "$CHARTS/shopapp" -f "$CHARTS/shopapp/values-core.yaml"
helm template shopapp "$CHARTS/shopapp" --namespace shopapp > "$OUT/shopapp.yaml"
helm template shopapp "$CHARTS/shopapp" --namespace shopapp -f "$CHARTS/shopapp/values-core.yaml" \
    --set global.httpPort=8000 > "$OUT/shopapp-core.yaml"

echo "== kubeconform (charts)"
kubeconform_run "$OUT"/*.yaml

echo "== kubeconform (k8s/deploy)"
kubeconform_run k8s/deploy/*.yaml

echo "All charts valid."
