# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.domainNamespaces" }}
{{- $args := include "utils.cloneDictionary" . | fromYaml -}}
{{- range $key := $args.domainNamespaces -}}
{{-   $ignore := set $args "domainNamespace" $key -}}
{{/*- include "operator.domainConfigMap" $args currently the GA operator runtime does this -*/}}
{{-   include "operator.operatorRoleBinding" $args -}}
{{- end }}
{{- end }}
