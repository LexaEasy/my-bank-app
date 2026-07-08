{{- define "kibana.fullname" -}}
{{- default .Chart.Name .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- define "kibana.selectorLabels" -}}
app.kubernetes.io/name: kibana
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
{{- define "kibana.labels" -}}
{{ include "kibana.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}
