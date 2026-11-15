#!/usr/bin/env bash
# Creates the thin chart of a new Spring Boot service (k8s/charts/<name>) from the shopapp-service library chart, with
# the conventions of every service: context path /<name>, its own database <name>, the public Keycloak URL.
#   bash k8s/scripts/new-service-chart.sh payment
# Then: add its env (compare docker-compose.yml), add it to k8s/charts/shopapp/Chart.yaml (dependencies) and
# values.yaml (<name>.enabled, ingress.apiServices), and run k8s/scripts/validate-charts.sh.
set -euo pipefail
cd "$(dirname "$0")/../charts"
name=${1:?usage: new-service-chart.sh <service>}
[ -e "$name" ] && { echo "k8s/charts/$name exists" >&2; exit 1; }
mkdir -p "$name/templates"
cat > "$name/Chart.yaml" <<YAML
apiVersion: v2
name: $name
description: shop_app $name service; thin chart over the shopapp-service library chart.
type: application
version: 0.1.0
# image tag when neither the chart values nor global.imageTag set one
appVersion: "latest"
dependencies:
  - name: shopapp-service
    version: 0.1.0
    repository: file://../shopapp-service
YAML
echo '{{ include "shopapp-service.all" . }}' > "$name/templates/all.yaml"
cat > "$name/values.yaml" <<YAML
# $name: values for the shopapp-service library chart (defaults and every key:
# k8s/charts/shopapp-service/templates/_helpers.tpl). Image: <global.imageRegistry>/shopapp-$name:<tag>.
name: $name
kind: spring
contextPath: /$name
database:
  enabled: true
env:
  KEYCLOAK_URL: '{{ include "shopapp-service.publicUrl" (list . "identity") }}'
YAML
echo "created k8s/charts/$name"
