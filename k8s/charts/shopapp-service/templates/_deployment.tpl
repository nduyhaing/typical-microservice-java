{{- define "shopapp-service.deployment" -}}
{{- $v := include "shopapp-service.values" . | fromYaml -}}
{{- $root := . -}}
{{- $spring := eq $v.kind "spring" -}}
{{- $base := trimSuffix "/" $v.contextPath -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ $v.name }}
  labels:
    {{- include "shopapp-service.labels" (list $v $root) | nindent 4 }}
spec:
  {{- if not $v.autoscaling.enabled }}
  replicas: {{ $v.replicaCount }}
  {{- end }}
  revisionHistoryLimit: 3
  {{- if $v.persistence.enabled }}
  # one ReadWriteOnce volume: the old pod must let go of it first
  strategy:
    type: Recreate
  {{- end }}
  selector:
    matchLabels:
      {{- include "shopapp-service.selectorLabels" $v | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "shopapp-service.selectorLabels" $v | nindent 8 }}
        {{- with $v.podLabels }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
      {{- with $v.podAnnotations }}
      annotations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
    spec:
      # no Kubernetes API access; no <SERVICE>_PORT=tcp://... variables (the Kafka/Debezium images turn KAFKA_*
      # variables into config, and they are noise everywhere else)
      automountServiceAccountToken: false
      enableServiceLinks: false
      terminationGracePeriodSeconds: {{ $v.terminationGracePeriodSeconds }}
      {{- $pullSecrets := $v.imagePullSecrets | default (($root.Values.global | default dict).imagePullSecrets) }}
      {{- with $pullSecrets }}
      # GHCR packages are private until the owner makes them public (scripts/k8s-up.sh GHCR_TOKEN creates ghcr-pull)
      imagePullSecrets:
        {{- range . }}
        - name: {{ . }}
        {{- end }}
      {{- end }}
      securityContext:
        {{- toYaml $v.podSecurityContext | nindent 8 }}
      containers:
        - name: {{ $v.name }}
          image: {{ include "shopapp-service.image" (list $v $root) | quote }}
          imagePullPolicy: {{ include "shopapp-service.pullPolicy" (list $v $root) }}
          securityContext:
            {{- toYaml $v.securityContext | nindent 12 }}
          ports:
            - name: http
              containerPort: {{ $v.port }}
              protocol: TCP
          {{- if $v.sharedConfigMap }}
          envFrom:
            - configMapRef:
                name: {{ $v.sharedConfigMap }}
                optional: true
          {{- end }}
          env:
            {{- if $spring }}
            - name: JAVA_TOOL_OPTIONS
              value: {{ $v.jvm.options | quote }}
            # Spring Boot enables the probe groups itself on Kubernetes; explicit for the BFFs, which do not set it
            - name: MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED
              value: "true"
            # read-only root filesystem and a uid without a passwd entry: anything that writes under user.home
            # (e.g. DJL's native library cache in recommendation) goes to the writable /tmp
            - name: HOME
              value: /tmp
            {{- else }}
            - name: HOSTNAME
              value: "0.0.0.0"
            - name: PORT
              value: {{ $v.port | quote }}
            {{- end }}
            {{- if $v.database.enabled }}
            - name: SPRING_DATASOURCE_URL
              value: {{ printf "jdbc:postgresql://%s:%v/%s" $v.database.host $v.database.port ($v.database.name | default $v.name) | quote }}
            - name: SPRING_DATASOURCE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: {{ $v.database.secret }}
                  key: {{ $v.database.usernameKey }}
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ $v.database.secret }}
                  key: {{ $v.database.passwordKey }}
            - name: SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE
              value: {{ $v.database.maxPoolSize | quote }}
            {{- end }}
            {{- range $name := keys $v.env | sortAlpha }}
            - name: {{ $name }}
              value: {{ tpl (toString (index $v.env $name)) $root | quote }}
            {{- end }}
            {{- range $name := keys $v.secretEnv | sortAlpha }}
            {{- $ref := index $v.secretEnv $name }}
            - name: {{ $name }}
              valueFrom:
                secretKeyRef:
                  name: {{ $ref.secret }}
                  key: {{ $ref.key }}
                  {{- if $ref.optional }}
                  optional: true
                  {{- end }}
            {{- end }}
          {{- $probePath := ternary (printf "%s/actuator/health/liveness" $base) $v.probes.path $spring }}
          {{- $readyPath := ternary (printf "%s/actuator/health/readiness" $base) $v.probes.path $spring }}
          startupProbe:
            httpGet:
              path: {{ $probePath }}
              port: http
            periodSeconds: {{ $v.probes.startupPeriodSeconds }}
            timeoutSeconds: {{ $v.probes.timeoutSeconds }}
            failureThreshold: {{ $v.probes.startupFailureThreshold }}
          livenessProbe:
            httpGet:
              path: {{ $probePath }}
              port: http
            periodSeconds: {{ $v.probes.periodSeconds }}
            timeoutSeconds: {{ $v.probes.timeoutSeconds }}
            failureThreshold: {{ $v.probes.failureThreshold }}
          readinessProbe:
            httpGet:
              path: {{ $readyPath }}
              port: http
            periodSeconds: {{ $v.probes.periodSeconds }}
            timeoutSeconds: {{ $v.probes.timeoutSeconds }}
            failureThreshold: {{ $v.probes.failureThreshold }}
          {{- if gt (int $v.preStopSleepSeconds) 0 }}
          lifecycle:
            preStop:
              sleep:
                seconds: {{ $v.preStopSleepSeconds }}
          {{- end }}
          resources:
            {{- toYaml $v.resources | nindent 12 }}
          volumeMounts:
            {{- if $spring }}
            - name: tmp
              mountPath: /tmp
            {{- else }}
            - name: next-cache
              mountPath: /app/.next/cache
            {{- end }}
            {{- range $v.writableDirs }}
            - name: {{ .name }}
              mountPath: {{ .mountPath }}
            {{- end }}
            {{- if $v.persistence.enabled }}
            - name: data
              mountPath: {{ required "persistence.mountPath is required" $v.persistence.mountPath }}
            {{- end }}
            {{- with $v.extraVolumeMounts }}
            {{- toYaml . | nindent 12 }}
            {{- end }}
      volumes:
        {{- if $spring }}
        - name: tmp
          emptyDir:
            sizeLimit: 256Mi
        {{- else }}
        - name: next-cache
          emptyDir:
            sizeLimit: 256Mi
        {{- end }}
        {{- range $v.writableDirs }}
        - name: {{ .name }}
          emptyDir:
            sizeLimit: {{ .sizeLimit | default "256Mi" }}
        {{- end }}
        {{- if $v.persistence.enabled }}
        - name: data
          persistentVolumeClaim:
            claimName: {{ $v.name }}-data
        {{- end }}
        {{- with $v.extraVolumes }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
      {{- with $v.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with $v.affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with $v.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
{{- end -}}
