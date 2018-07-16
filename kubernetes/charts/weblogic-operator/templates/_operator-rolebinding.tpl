# Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

{{- define "operator.operatorRoleBinding" }}
---
kind: RoleBinding
apiVersion: rbac.authorization.k8s.io/v1beta1
metadata:
  name: weblogic-operator-rolebinding
  namespace: {{ .domainsNamespace }}
  labels:
    weblogic.resourceVersion: operator-v1
    weblogic.operatorName: {{ .operatorNamespace }}
subjects:
- kind: ServiceAccount
  name: {{ .operatorServiceAccount }}
  namespace: {{ .operatorNamespace }}
  apiGroup: ""
roleRef:
  kind: ClusterRole
  name: weblogic-operator-namespace-role
  apiGroup: ""
{{- end }}
