{{- define "shopapp-service.service" -}}
{{- $v := include "shopapp-service.values" . | fromYaml -}}
apiVersion: v1
kind: Service
metadata:
  # same name as the compose service, so in-cluster URLs (http://product:8080/product) stay the same
  name: {{ $v.name }}
  labels:
    {{- include "shopapp-service.labels" (list $v .) | nindent 4 }}
spec:
  type: {{ $v.service.type }}
  selector:
    {{- include "shopapp-service.selectorLabels" $v | nindent 4 }}
  ports:
    - name: http
      port: {{ $v.service.port | default $v.port }}
      targetPort: http
      protocol: TCP
{{- end -}}

{{- define "shopapp-service.pvc" -}}
{{- $v := include "shopapp-service.values" . | fromYaml -}}
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: {{ $v.name }}-data
  labels:
    {{- include "shopapp-service.labels" (list $v .) | nindent 4 }}
  annotations:
    # keep the data (uploaded images, downloaded model) on helm uninstall
    helm.sh/resource-policy: keep
spec:
  accessModes:
    - {{ $v.persistence.accessMode }}
  {{- with $v.persistence.storageClass }}
  storageClassName: {{ . }}
  {{- end }}
  resources:
    requests:
      storage: {{ $v.persistence.size }}
{{- end -}}

{{- define "shopapp-service.hpa" -}}
{{- $v := include "shopapp-service.values" . | fromYaml -}}
{{- if $v.persistence.enabled -}}
{{- fail (printf "shopapp-service %s: autoscaling needs a workload without a ReadWriteOnce volume" $v.name) -}}
{{- end -}}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ $v.name }}
  labels:
    {{- include "shopapp-service.labels" (list $v .) | nindent 4 }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ $v.name }}
  minReplicas: {{ $v.autoscaling.minReplicas }}
  maxReplicas: {{ $v.autoscaling.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ $v.autoscaling.targetCPUUtilizationPercentage }}
{{- end -}}

{{- define "shopapp-service.pdb" -}}
{{- $v := include "shopapp-service.values" . | fromYaml -}}
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: {{ $v.name }}
  labels:
    {{- include "shopapp-service.labels" (list $v .) | nindent 4 }}
spec:
  minAvailable: {{ $v.pdb.minAvailable }}
  selector:
    matchLabels:
      {{- include "shopapp-service.selectorLabels" $v | nindent 6 }}
{{- end -}}
