# shop_app Slice 6 (Observability, Kubernetes, CI) Implementation Plan

> Executed by implementer subagents (P13) with the common agent rules; decisions in a new `### 6` section with ids
> 6-N. Docker is memory-constrained: one agent at a time on Docker (coordinator says "docker go").

**Spec:** `docs/superpowers/specs/2026-11-15-shop-app-slice6-observability-k8s-ci-design.md`

## Review Focus
1. Default dev stack unchanged and within memory when the observability profile is off; telemetry export never
   blocks or fails a request when the collector is down.
2. Trace context really propagates (gateway, RestClient, Kafka) and logs carry trace ids.
3. No new public exposure: actuator endpoints blocked at the edge; Grafana loopback/admin password; no secrets in
   charts or workflows; GHCR push only from master.
4. Charts: probes/resources sane, JVM caps applied, secrets from Secrets, works with the same hostnames.
5. CI: green on GitHub, path filters correct (a change to common-library builds dependents), Testcontainers works
   on runners, gitleaks allowlist narrow.

### Task 1: OpenTelemetry in common-library + all services (traces, logs with trace ids, metrics); compose
`observability` profile (collector, Prometheus, Loki, Tempo, Grafana with provisioned datasources/dashboards); nginx
`grafana.` host + actuator blocked at the edge. (6-1..19)
### Task 2: Helm charts (base + per service + umbrella), `k8s/deploy` infra, `scripts/k8s-up.sh`, validation. (6-20..39)
### Task 3: GitHub Actions (reusable Maven module workflow + per-module callers, apps, charts, gitleaks, CodeQL,
Dependabot, GHCR images). (6-40..59)
### Task 4: README, decisions, definition of done; check CI runs on GitHub. (6-60..)

Tasks 2 and 3 can be written in parallel with 1 (no Docker for most of 3; 2 needs Docker only for a kind run).
