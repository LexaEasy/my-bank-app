{{- define "zipkin.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "zipkin.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- include "zipkin.name" . }}
{{- end }}
{{- end }}

{{- define "zipkin.labels" -}}
app.kubernetes.io/name: {{ include "zipkin.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" }}
{{- end }}

{{- define "zipkin.selectorLabels" -}}
app.kubernetes.io/name: {{ include "zipkin.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
