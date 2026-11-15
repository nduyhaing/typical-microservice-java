# shop_app — Slice 6: Observability, Kubernetes, CI — Design

Date: 2026-11-15 · Status: written for automatic implementation (P9) · YAS @ `179e8135`.

## Intent

Make shop_app operable like YAS: see what every service is doing (metrics, logs, traces in Grafana), run it on
Kubernetes with Helm charts, and build/test every module automatically on each push.

End state: `docker compose --profile observability up` adds the OTel collector, Prometheus, Loki, Tempo and Grafana;
Grafana opens with provisioned dashboards (JVM/HTTP per service, Kafka/CDC lag, Postgres) and a request from the
storefront can be followed as one trace through BFF → order → product/tax/promotion with matching log lines; a
`kind`/k3d cluster runs the whole shop from the Helm charts; GitHub Actions builds and tests each module on push and
PR, runs the apps' checks, scans for secrets and runs CodeQL.

## Design

### Observability (compose profile `observability`, off by default — memory)
- **Traces + logs + metrics via OpenTelemetry**: prefer Spring Boot 4's built-in OTel support (Micrometer tracing
  + OTLP export, `spring-boot-starter-opentelemetry` or the Boot 4 equivalent — builder checks) over the YAS
  `opentelemetry-javaagent.jar` (no binary jar committed; record). Configure once in `common-library` (auto-config /
  shared properties) so every service exports OTLP to `otel-collector:4318` when `MANAGEMENT_OTLP_*`/
  `OTEL_EXPORTER_OTLP_ENDPOINT` is set and does nothing when it isn't (default dev stack unaffected). Trace context
  propagates through RestClient, Spring Cloud Gateway (BFFs) and Kafka (producer/consumer observation) — Debezium
  messages won't carry it; record. Log lines carry traceId/spanId (logback pattern in common config) and are shipped
  to Loki through the collector (OTLP logs) — not by scraping Docker.
- **Collector** (`otel/opentelemetry-collector-contrib`, pinned): OTLP in; traces → Tempo, logs → Loki (OTLP),
  metrics → Prometheus (remote-write or the collector's Prometheus exporter scraped by Prometheus; services also
  keep `/actuator/prometheus` — choose one path, record). Prometheus also scrapes Kafka Connect/Debezium JMX if
  cheap (otherwise consumer-lag from kafbat/`kafka-exporter`, record).
- **Grafana** provisioned datasources (Prometheus, Loki, Tempo with trace↔log links via traceId) and dashboards
  (JVM/HTTP per service, Spring Kafka consumer lag for search/recommendation/webhook, Postgres basics if an exporter
  is added). Anonymous viewer access on loopback only, admin password from `.env`; nginx host
  `grafana.${DOMAIN}` (needs a hosts entry — the user must add it; list it in README; the coordinator asks the user).
- Actuator: `/actuator/prometheus` and health stay reachable only inside the network — block `/*/actuator/**` at
  nginx except health if anything external needs it (currently exposed — fix; record).
- Memory: measure with the profile on; record in README "Resources"; keep each component's heap bounded.

### Kubernetes (Helm)
- `k8s/charts/`: one **library/base chart** (`shopapp-service`: Deployment, Service, ConfigMap/Secret refs, probes
  on actuator health liveness/readiness, resources requests/limits, JVM caps via env, optional HPA, ServiceMonitor
  optional) and a thin chart (values) per service + the two Next.js apps + the BFFs; an umbrella chart
  `shopapp` with dependencies. Infrastructure for a local cluster in `k8s/deploy/` (YAS uses operators: Strimzi,
  Zalando/CloudNativePG, ECK, Keycloak operator) — for shop_app use lightweight Helm charts (Bitnami-free options
  given licensing changes: official/community charts for Postgres (CloudNativePG operator), Kafka (Strimzi), Keycloak
  (official operator or codecentric keycloakx), Redis, Elasticsearch (ECK) — builder picks and records; or plain
  manifests for dev). Ingress (ingress-nginx) with the same hostnames. Secrets: values reference Kubernetes Secrets;
  no secrets committed beyond the dev `.env` defaults (a `make-secrets.sh` creates them from `.env`).
- `scripts/k8s-up.sh`: creates a `kind` (or k3d) cluster, installs infra + umbrella chart, waits for readiness.
  Smoke: the same `verify-foundation.sh` against the cluster's ingress (parameterised base URL) for at least the
  slice 1–2 checks. If the local machine can't run a cluster alongside compose (memory), `helm lint`/`helm template`
  + `kubeconform` validation in CI is the gate and a live cluster run is documented as optional — record.

### CI (GitHub Actions)
- Per-module workflow generated from one reusable workflow (`.github/workflows/maven-module.yml` with
  `workflow_call`) + path filters per module (YAS has one file per module); JDK 27 (Temurin EA or GA — whichever
  setup-java provides; record), Maven cache, `mvn -pl <module> -am verify` (Testcontainers works on GitHub-hosted
  ubuntu runners), JaCoCo report artifact, Checkstyle.
- Apps: storefront/backoffice `npm ci`, lint, typecheck, test, build.
- Charts: `helm lint` + `helm template | kubeconform`.
- Security: gitleaks (with an allowlist for the documented dev credentials in `.env`/realm export), CodeQL (java +
  javascript), Dependabot config (maven, npm, github-actions, docker).
- Images: build per service on main and push to GHCR (`ghcr.io/nduyhaing/shopapp-<svc>`) — only on push to master,
  using `GITHUB_TOKEN`; tag with the commit SHA.
- Note: the repo is pushed to GitHub (public); the workflows must pass on GitHub — the coordinator checks the runs
  with `gh run list`/`gh run view` using the personal account.

### Definition of done
1. Observability: profile up healthy; a storefront checkout produces one trace spanning BFF → order → product/tax;
   logs for that trace found in Loki by traceId; dashboards show metrics for every service; default stack (profile
   off) unchanged and smoke green.
2. Kubernetes: charts lint/template/kubeconform clean; either a live kind run passing the slice 1–2 smoke checks or a
   recorded reason why not + validated manifests.
3. CI: all workflows green on GitHub for the pushed master commit.
4. README (Observability, Kubernetes, CI sections) + decisions 6-*.
