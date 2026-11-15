# shop_app on Kubernetes

Helm charts for every shop_app workload and plain manifests for the infrastructure of a local cluster. The cluster
runs the same images, hostnames, in-cluster service names and settings as `docker-compose.yml`; decisions are in
`decisions.md` 6-20..6-39.

```
k8s/
  charts/
    shopapp-service/   library chart: Deployment, Service, probes, resources, securityContext, PVC, HPA, PDB
    <service>/         one thin chart per workload (values.yaml only): 15 services, 2 BFFs, storefront, backoffice
    shopapp/           umbrella chart: all of the above + shared ConfigMap + Ingresses + edge-deny backend
  deploy/              infrastructure: CloudNativePG Postgres, Keycloak, Redis, Kafka + Kafka Connect, Elasticsearch,
                       Kafka UI (optional), ingress-nginx additions, kind cluster config
  scripts/
    make-secrets.sh    Kubernetes Secrets from .env
    validate-charts.sh helm lint + helm template | kubeconform (also run by CI)
    new-service-chart.sh  thin chart for a new service
scripts/k8s-up.sh, scripts/k8s-down.sh   local kind cluster
```

## Quick start (kind)

Needs `docker`, [`kind`](https://kind.sigs.k8s.io/) (0.33), `kubectl` and `helm` (3.8+ or 4) on `PATH`, and the
compose hostnames in your hosts file (see the main README). Then:

```bash
bash scripts/k8s-up.sh                       # whole shop, images from GHCR (published by CI from master)
K8S_PROFILE=core bash scripts/k8s-up.sh      # slice 1-2 path only: Postgres, Keycloak, Redis + 8 workloads
K8S_IMAGES=local bash scripts/k8s-up.sh      # the compose images shopapp/<svc>:dev (docker compose build first)
bash scripts/k8s-down.sh                     # delete the cluster and all its data
```

The ingress listens on `127.0.0.1:8000` (`K8S_HTTP_PORT`; compose's nginx already has port 80), so the shop is at
`http://storefront.shopapp.local:8000`, the back office at `http://backoffice.shopapp.local:8000` (admin / admin)
and Keycloak at `http://identity.shopapp.local:8000`. With compose stopped, `K8S_HTTP_PORT=80` gives the plain URLs.

`k8s-up.sh` creates the cluster, installs ingress-nginx (`controller-v1.15.1`, kind manifest) and the CloudNativePG
operator (1.30.1), creates the namespace `shopapp`, the Secrets (from `.env`) and ConfigMaps (from the repository's
SQL, realm, Elasticsearch and connector files, so there is one copy of each), applies `k8s/deploy`, waits for
Postgres, the databases, Redis and Keycloak (and Kafka, Kafka Connect, Elasticsearch in the full profile), then
installs the umbrella chart with `--wait` and waits for the CDC init Jobs and the connector registration.

**Memory.** The full profile requests about 12 GiB (17 JVM workloads at 512Mi, Keycloak, Kafka, Kafka Connect,
Elasticsearch, Postgres); the core profile about 6 GiB. Running a cluster next to the compose stack needs that much on
top of compose's ~7.5 GB, so on a 16 GB Docker VM stop compose first or use the core profile.

## Charts

* **Library chart `shopapp-service`** (`templates/_helpers.tpl` lists every value and its default). A thin chart sets
  `name`, `kind` (`spring` or `nextjs`) and its own env; the library renders:
  * Deployment and Service named like the compose service (`product`, `storefront-bff`, ...), port 8080 (BFFs 8087 /
    8088, Next.js 3000), so in-cluster URLs such as `http://product:8080/product` are the same as in compose.
  * Env: the shared ConfigMap `shopapp-config` (envFrom), the chart's `env` (rendered with `tpl`, so public URLs
    follow `global.domain` / `global.httpPort`), `secretEnv` (secretKeyRef), and for `database.enabled`
    `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` + `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` (5, sampledata 2).
  * `JAVA_TOOL_OPTIONS` with the Dockerfiles' caps (`-Xmx320m`, metaspace 192m, code cache 64m, SerialGC; sampledata
    `-Xmx256m`), memory request 512Mi / limit 768Mi (recommendation 1Gi for ONNX Runtime's native memory,
    sampledata 448/640Mi, Next.js 128/384Mi), CPU request 100m / limit 1.
  * Probes on `<contextPath>/actuator/health/liveness` and `/readiness` (BFFs `/actuator/health/...`, Next.js `/`):
    a startup probe gives the JVM 3 minutes (recommendation 6: it downloads its model on the first start).
    Every service sets `management.endpoint.health.probes.enabled=true` except the BFFs, which get it from the env
    the chart sets (Spring Boot also enables the groups by itself on Kubernetes).
  * Restricted securityContext: uid 1000, non-root, no privilege escalation, all capabilities dropped, seccomp
    `RuntimeDefault`, read-only root filesystem with an emptyDir at `/tmp` (`HOME=/tmp`) or `.next/cache`; no
    service-account token, no service-link env vars.
  * Optional PVC (`persistence`: media's `/images`, recommendation's model cache), HPA and PodDisruptionBudget.
* **Umbrella chart `shopapp`**: `values.yaml` holds the non-secret settings (domain, public port, image registry and
  tag, shared ConfigMap, ingress, which workloads are enabled); `values-core.yaml` keeps only the slice 1-2 path.
  Each service's wiring (which services it calls) stays in its thin chart so a thin chart installs alone; override
  anything under the service's key, e.g. `--set product.env.SPRING_LIQUIBASE_CONTEXTS=default`.
* **Images**: `ghcr.io/nduyhaing/shopapp-<service>:<global.imageTag>` (default `latest`); `K8S_IMAGES=local` switches
  to `shopapp/<service>:dev` and loads them into kind. CI publishes `:<sha>` and `:latest` from master; **GHCR
  packages are private by default**, so either the owner makes them public or `k8s-up.sh` gets
  `GHCR_USER` + `GHCR_TOKEN` (a token with `read:packages`) and creates the docker-registry Secret `ghcr-pull`, which
  the charts use through `global.imagePullSecrets` (per workload: `<service>.imagePullSecrets`).

## Ingress

ingress-nginx with the compose hostnames: `identity.<domain>` → Keycloak, `api.<domain>/<service>/` → each service,
`storefront.<domain>` → storefront-bff, `backoffice.<domain>` → backoffice-bff, `kafka.<domain>` → Kafka UI
(optional). Same buffer size (128k) and upload limit (11m on api and backoffice) as compose nginx.

**Edge blocks without snippets.** Compose nginx refuses some paths itself: product's stock `subtract-quantity` /
`restore-quantity` (3c-63), promotion's coupon `apply-usage` / `release-usage` (4-75) and actuator. ingress-nginx
disables `server-snippet` / `configuration-snippet` annotations by default (`allow-snippet-annotations: false`,
and they are rated critical risk), so instead the Ingress routes exactly those paths (plain `Prefix` paths, longest
prefix wins over `/<service>`) and every unknown path to **edge-deny**, a 16 MiB nginx in the chart that answers with
compose's status and JSON body (403 `Not available through the API gateway`, 404 for actuator and unknown paths).
No regex or rewrite annotation is used anywhere, because one on a host would switch all its paths to
case-insensitive regex matching. nginx merges repeated slashes before matching, and Spring's matching is
case-sensitive, so `//product//backoffice/...` is refused and `/Product/...` reaches nothing.

**Forwarded headers.** ingress-nginx overwrites `X-Forwarded-For/-Host/-Proto/-Port` (`use-forwarded-headers` is off);
`Forwarded` and `X-Forwarded-Prefix`, which it would pass through, are cleared with the controller's
`proxy-set-headers` ConfigMap (`k8s/deploy/ingress-nginx.yaml`), like compose's `proxy_set_header ... ""`.

**ingress-nginx is retired** (no releases after March 2026); it is used because the spec asks for it and because
compose's routing maps onto it one to one. The Ingresses contain nothing controller-specific except two annotations,
so moving to another controller (or Gateway API HTTPRoutes) means replacing those annotations and `ingress.className`.

## DNS: pods use the public URLs

As in compose, some calls go through the edge: tokens are issued for `http://identity.<domain>:<port>` and every
service validates that issuer (it fetches the discovery document from it), cart calls `http://api.<domain>/product`,
order calls `.../cart`, the BFFs relay `/api/**` to `http://api.<domain>`. `k8s-up.sh` adds a CoreDNS rule that answers
`*.<domain>` with the Service `ingress-nginx/shopapp-edge`, which listens on the public port and forwards to the
controller. Keycloak gets `KC_HOSTNAME=http://identity.<domain>:<port>` and `DOMAIN=<domain>:<port>` (the realm's
redirect URIs are `http://storefront.${DOMAIN}/*`), so login works on a non-80 port too.

## Infrastructure (`k8s/deploy`)

| Compose service | Kubernetes | Why |
|---|---|---|
| postgres | CloudNativePG `Cluster` (PostgreSQL 16.16 `standard` image with pgvector), Service alias `postgres`, Job `postgres-init` runs `postgres_init.sql` | operator from the spec; `wal_level=logical`, `max_slot_wal_keep_size=1GB`, `max_connections=200`; `admin` is superuser as in compose |
| debezium-db-init, sampledata-db-init | Jobs (wait for product's/order's tables, run the same SQL files) | |
| identity | Deployment `identity` (Keycloak 26.8.2, `start-dev --import-realm`, realm from ConfigMap) | the operator's CRDs and controller are more than one dev instance needs |
| redis | StatefulSet | |
| kafka | StatefulSet (apache/kafka 4.4.0, KRaft, one node) | Strimzi's KafkaConnect needs a Strimzi-built image pushed to a registry for the Debezium plugin; the operator costs memory |
| kafka-connect, kafka-connect-init | Deployment (Debezium connect 3.7.1) + Deployment running `kafka/register-connectors.sh` | same images, env and script as compose |
| elasticsearch | StatefulSet (9.4.8, the same file-realm entrypoint) | ECK would replace the file-realm user and TLS setup the search service uses |
| kafka-ui | Deployment (optional, `K8S_WITH_KAFKA_UI=1`) | |
| pgadmin, nginx | not deployed (use `kubectl port-forward`; ingress-nginx replaces nginx) | |

Pods in `shopapp` run under the **restricted** Pod Security Standard. The infrastructure images keep a writable root
filesystem; the shop's own pods do not.

## Secrets

`k8s/scripts/make-secrets.sh [.env] [namespace]` creates `shopapp-postgres`, `shopapp-keycloak-admin`,
`shopapp-keycloak-clients`, `shopapp-debezium`, `shopapp-sampledata-db` and `shopapp-elasticsearch` from `.env`
(values passed to kubectl as files, not arguments). Charts and manifests only reference them; nothing beyond the
dev defaults in `.env` is committed.

## Validation

```bash
bash k8s/scripts/validate-charts.sh   # KUBECONFORM=/path/to/kubeconform if it is not on PATH
```

Builds the chart dependencies, runs `helm lint` on every chart (umbrella also with `values-core.yaml`), renders them
and validates the output and `k8s/deploy/*.yaml` with `kubeconform -strict` (CloudNativePG's `Cluster` against its
CRD schema from the datreeio CRDs-catalog).

## Adding a service

`bash k8s/scripts/new-service-chart.sh <name>`, add its env (compare `docker-compose.yml`), add it to the umbrella's
`Chart.yaml` dependencies and `values.yaml` (`<name>.enabled`, `ingress.apiServices`; `values-core.yaml` if it is not on
the core path), then run the validation.

## Troubleshooting

* `kubectl -n shopapp get pods`, `kubectl -n shopapp logs deploy/<service>`, `kubectl -n shopapp describe pod ...`
* A service restarts at start: its startup probe ran out (slow node) - raise `<service>.probes.startupFailureThreshold`.
* Login redirects to port 80: `K8S_HTTP_PORT` changed after Keycloak imported the realm; delete the cluster
  (`scripts/k8s-down.sh`) and start again.
* Connector status: `kubectl -n shopapp exec deploy/kafka-connect -- curl -s localhost:8083/connectors?expand=status`.
* Postgres: `kubectl -n shopapp exec -it shopapp-postgres-1 -- psql -U postgres`.
