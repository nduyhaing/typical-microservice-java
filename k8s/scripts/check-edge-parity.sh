#!/usr/bin/env bash
# Checks that the Kubernetes edge (Ingress paths + edge-deny nginx of the shopapp chart) refuses and routes exactly
# what compose nginx does (nginx/templates/default.conf.template), and that compose's browser-only hosts drop
# browser trace headers (decisions.md 6-60, 6-62). Used by hand and by Charts CI.
#   bash k8s/scripts/check-edge-parity.sh
# Needs: helm, python3 (PYTHON=python on Windows).
set -euo pipefail

cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

for chart in k8s/charts/*/; do
    case "$(basename "$chart")" in shopapp-service|shopapp) continue ;; esac
    helm dependency update --skip-refresh "$chart" > /dev/null
done
helm dependency update --skip-refresh k8s/charts/shopapp > /dev/null
helm template shopapp k8s/charts/shopapp --namespace shopapp > "$OUT/shopapp.yaml"

"${PYTHON:-python3}" k8s/scripts/edge_parity.py nginx/templates/default.conf.template "$OUT/shopapp.yaml"
