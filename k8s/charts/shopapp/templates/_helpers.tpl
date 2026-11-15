{{- define "shopapp.labels" -}}
app.kubernetes.io/part-of: shopapp
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}

{{/* identity -> identity.shopapp.local */}}
{{- define "shopapp.host" -}}
{{- printf "%s.%s" (index . 1) ((index . 0).Values.global.domain | default "shopapp.local") -}}
{{- end -}}

{{- define "shopapp.enabled" -}}
{{- $root := index . 0 -}}
{{- $svc := index $root.Values (index . 1) | default dict -}}
{{- if $svc.enabled }}true{{ end -}}
{{- end -}}

{{/* common annotations of every shop_app Ingress */}}
{{- define "shopapp.ingressAnnotations" -}}
nginx.ingress.kubernetes.io/proxy-buffer-size: {{ .Values.ingress.proxyBufferSize | quote }}
{{- end -}}

{{/* backend: (list "service" port) */}}
{{- define "shopapp.backend" -}}
service:
  name: {{ index . 0 }}
  port:
    number: {{ index . 1 }}
{{- end -}}
