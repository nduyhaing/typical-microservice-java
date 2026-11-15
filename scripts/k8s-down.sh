#!/usr/bin/env bash
# Deletes the local kind cluster created by scripts/k8s-up.sh, with all its data (Postgres, Kafka, Elasticsearch
# volumes live inside the cluster's node container). The compose stack is not touched.
#   bash scripts/k8s-down.sh            # K8S_CLUSTER=shopapp by default
set -euo pipefail
CLUSTER="${K8S_CLUSTER:-shopapp}"
if kind get clusters 2> /dev/null | grep -qx "$CLUSTER"; then
    kind delete cluster --name "$CLUSTER"
else
    echo "no kind cluster named $CLUSTER"
fi
