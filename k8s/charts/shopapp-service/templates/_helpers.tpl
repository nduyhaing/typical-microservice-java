{{/*
shopapp-service library chart. A thin chart (one per workload) depends on this chart and renders everything with

    {{ include "shopapp-service.all" . }}

Its values.yaml sets at least `name` and `kind`; everything else has a default below. The defaults are merged under
the chart's values (mergeOverwrite), so a value can replace a default but a false / 0 / "" cannot switch a non-empty
default off: the only defaults that are "on" are the restricted securityContext flags. `kind` picks the rest:

  spring  Spring Boot service or BFF: JAVA_TOOL_OPTIONS (same caps as the Dockerfiles), probes on
          <contextPath>/actuator/health/{liveness,readiness}, writable /tmp (emptyDir), 512Mi/768Mi memory.
  nextjs  Next.js standalone server: HTTP probes on `probes.path`, HOSTNAME=0.0.0.0 (Kubernetes sets HOSTNAME to the
          pod name, which would make the server listen on that name only), writable .next/cache, 128Mi/384Mi.
*/}}

{{- define "shopapp-service.defaults" -}}
name: ""
kind: spring
replicaCount: 1
image:
  # full repository; default <global.imageRegistry>/<global.imageNamePrefix><name>
  repository: ""
  # default global.imageTag, then the chart appVersion
  tag: ""
  pullPolicy: IfNotPresent
# names of docker-registry Secrets; default global.imagePullSecrets
imagePullSecrets: []
port: 8080
# servlet context path of a Spring service ("/product"); "" for the BFFs
contextPath: ""
probes:
  # nextjs only: path of the HTTP probes
  path: /
  periodSeconds: 10
  timeoutSeconds: 3
  failureThreshold: 3
  # startup budget = startupPeriodSeconds * startupFailureThreshold (JVM start + Liquibase on a busy node)
  startupPeriodSeconds: 5
  startupFailureThreshold: 36
# plain env: NAME: value; values are rendered with `tpl`, so they can use
# {{ include "shopapp-service.publicUrl" (list . "api") }}
env: {}
# env from Secrets: NAME: {secret: <name>, key: <key>, optional: false}
secretEnv: {}
# shared, non-secret settings (the umbrella chart's ConfigMap); optional, so a thin chart installs alone
sharedConfigMap: shopapp-config
database:
  enabled: false
  host: postgres
  port: 5432
  name: ""
  # Secret with the login (kubernetes.io/basic-auth keys), created by k8s/scripts/make-secrets.sh
  secret: shopapp-postgres
  usernameKey: username
  passwordKey: password
  # one Postgres for every service: same pool as the Dockerfiles
  maxPoolSize: 5
jvm:
  options: "-Xmx320m -Xss512k -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC"
resources: {}
podSecurityContext:
  runAsNonRoot: true
  runAsUser: 1000
  runAsGroup: 1000
  fsGroup: 1000
  seccompProfile:
    type: RuntimeDefault
securityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop: ["ALL"]
# extra writable emptyDirs: [{name, mountPath, sizeLimit}]
writableDirs: []
# one PersistentVolumeClaim mounted at mountPath (media images, recommendation model cache); forces Recreate
persistence:
  enabled: false
  mountPath: ""
  size: 1Gi
  storageClass: ""
  accessMode: ReadWriteOnce
extraVolumes: []
extraVolumeMounts: []
podAnnotations: {}
podLabels: {}
terminationGracePeriodSeconds: 45
# lets the ingress controller drop the endpoint before the JVM stops taking requests
preStopSleepSeconds: 5
service:
  type: ClusterIP
  # default: port
  port: 0
autoscaling:
  enabled: false
  minReplicas: 1
  maxReplicas: 3
  targetCPUUtilizationPercentage: 80
pdb:
  enabled: false
  minAvailable: 1
nodeSelector: {}
tolerations: []
affinity: {}
{{- end -}}

{{- define "shopapp-service.kindDefaults.spring" -}}
resources:
  requests:
    cpu: 100m
    memory: 512Mi
  limits:
    cpu: "1"
    memory: 768Mi
{{- end -}}

{{- define "shopapp-service.kindDefaults.nextjs" -}}
port: 3000
resources:
  requests:
    cpu: 50m
    memory: 128Mi
  limits:
    cpu: 500m
    memory: 384Mi
{{- end -}}

{{/* Effective values (YAML): defaults < kind defaults < chart values. Use: include ... . | fromYaml */}}
{{- define "shopapp-service.values" -}}
{{- $defaults := include "shopapp-service.defaults" . | fromYaml -}}
{{- $kind := .Values.kind | default "spring" -}}
{{- if not (has $kind (list "spring" "nextjs")) -}}
{{- fail (printf "shopapp-service: kind must be spring or nextjs, got %q" $kind) -}}
{{- end -}}
{{- $kindDefaults := include (printf "shopapp-service.kindDefaults.%s" $kind) . | fromYaml -}}
{{- $values := mergeOverwrite $defaults $kindDefaults (deepCopy .Values) -}}
{{- if not $values.name -}}
{{- fail "shopapp-service: values.name is required" -}}
{{- end -}}
{{- toYaml $values -}}
{{- end -}}

{{/* Public URL of an ingress host: (list $root "api") -> http://api.shopapp.local[:port] */}}
{{- define "shopapp-service.publicUrl" -}}
{{- $root := index . 0 -}}
{{- $host := index . 1 -}}
{{- $g := $root.Values.global | default dict -}}
{{- $port := toString ($g.httpPort | default 80) -}}
{{- $suffix := ternary "" (printf ":%s" $port) (eq $port "80") -}}
{{- printf "%s://%s.%s%s" ($g.scheme | default "http") $host ($g.domain | default "shopapp.local") $suffix -}}
{{- end -}}

{{- define "shopapp-service.image" -}}
{{- $v := index . 0 -}}
{{- $root := index . 1 -}}
{{- $g := $root.Values.global | default dict -}}
{{- $prefix := ternary $g.imageNamePrefix "shopapp-" (hasKey $g "imageNamePrefix") -}}
{{- $repository := $v.image.repository | default (printf "%s/%s%s" ($g.imageRegistry | default "ghcr.io/nduyhaing") $prefix $v.name) -}}
{{- $tag := $v.image.tag | default $g.imageTag | default $root.Chart.AppVersion | default "latest" -}}
{{- printf "%s:%s" $repository (toString $tag) -}}
{{- end -}}

{{- define "shopapp-service.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/part-of: shopapp
{{- end -}}

{{- define "shopapp-service.labels" -}}
{{- $v := index . 0 -}}
{{- $root := index . 1 -}}
{{ include "shopapp-service.selectorLabels" $v }}
app.kubernetes.io/instance: {{ $root.Release.Name }}
app.kubernetes.io/managed-by: {{ $root.Release.Service }}
app.kubernetes.io/version: {{ $root.Chart.AppVersion | default $root.Chart.Version | quote }}
helm.sh/chart: {{ printf "%s-%s" $root.Chart.Name $root.Chart.Version | trunc 63 | trimSuffix "-" }}
{{- end -}}

{{- define "shopapp-service.all" -}}
{{ include "shopapp-service.deployment" . }}
---
{{ include "shopapp-service.service" . }}
{{- $v := include "shopapp-service.values" . | fromYaml }}
{{- if $v.persistence.enabled }}
---
{{ include "shopapp-service.pvc" . }}
{{- end }}
{{- if $v.autoscaling.enabled }}
---
{{ include "shopapp-service.hpa" . }}
{{- end }}
{{- if $v.pdb.enabled }}
---
{{ include "shopapp-service.pdb" . }}
{{- end }}
{{- end -}}
