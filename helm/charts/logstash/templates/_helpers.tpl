{{- define "logstash.fullname" -}}
{{- default .Chart.Name .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- define "logstash.selectorLabels" -}}
app.kubernetes.io/name: logstash
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
{{- define "logstash.labels" -}}
{{ include "logstash.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}
