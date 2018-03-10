// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import static java.util.Arrays.asList;

import io.kubernetes.client.models.V1Service;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.create.YamlUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-operator.sh
 * creates are correct when external rest is none, the remote debug port is disabled,
 * elk is disabled and there is no image pull secret.
 */
public class CreateOperatorGeneratedFilesExtRestNoneDebugOffTest {

  private static CreateOperatorInputs inputs;
  private static GeneratedOperatorYamlFiles generatedFiles;

  @BeforeClass
  public static void setup() throws Exception {
    inputs = CreateOperatorInputs.newInputs(); // defaults to external rest none, debug off
    generatedFiles = GeneratedOperatorYamlFiles.generateOperatorYamlFiles(inputs);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (generatedFiles != null) {
      generatedFiles.remove();
    }
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_operatorConfigMap() throws Exception {
    assertThat(
      weblogicOperatorYaml().getOperatorConfigMap(),
      equalTo(weblogicOperatorYaml().getExpectedOperatorConfigMap(""))); // no external operator cert
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_operatorSecrets() throws Exception {
    // Need to compare the yamls since Secret.equal only works for the same instance
    assertThat_yamlIsEqual(
      weblogicOperatorYaml().getOperatorSecrets(),
      weblogicOperatorYaml().getExpectedOperatorSecrets("")); // no external operator key
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_operatorDeployment() throws Exception {
    assertThat(
      weblogicOperatorYaml().getOperatorDeployment(),
      equalTo(weblogicOperatorYaml().getBaseExpectedOperatorDeployment()));
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_externalOperatorService() throws Exception {
    assertThat(weblogicOperatorYaml().getExternalOperatorService(), nullValue());
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_internalOperatorService() throws Exception {
    V1Service want =
      newService("internal-weblogic-operator-service", inputs.getNamespace());
    want.getSpec()
      .type("ClusterIP")
      .putSelectorItem("app", "weblogic-operator")
      .addPortsItem(newServicePort("rest-https").port(8082));
    assertThat(
      weblogicOperatorYaml().getInternalOperatorService(),
      equalTo(want));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorNamespace() throws Exception {
    assertThat(
      weblogicOperatorSecurityYaml().getOperatorNamespace(),
      equalTo(newNamespace(inputs.getNamespace())));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorServiceAccount() throws Exception {
    assertThat(
      weblogicOperatorSecurityYaml().getOperatorServiceAccount(),
      equalTo(newServiceAccount(inputs.getServiceAccount(), inputs.getNamespace())));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_weblogicOperatorClusterRole() throws Exception {
    assertThat(
      weblogicOperatorSecurityYaml().getWeblogicOperatorClusterRole(),
      equalTo(
        newClusterRole("weblogic-operator-cluster-role")
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("")
            .resources(asList("namespaces", "persistentvolumes"))
            .verbs(asList("get", "list", "watch")))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("apiextensions.k8s.io")
            .addResourcesItem("customresourcedefinitions")
            .verbs(asList("get", "list", "watch", "create", "update", "patch", "delete", "deletecollection")))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("weblogic.oracle")
            .addResourcesItem("domains")
            .verbs(asList("get", "list", "watch", "update", "patch")))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("weblogic.oracle")
            .addResourcesItem("domains/status")
            .addVerbsItem("update"))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("extensions")
            .addResourcesItem("ingresses")
            .verbs(asList("get", "list", "watch", "create", "update", "patch", "delete", "deletecollection")))));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_weblogicOperatorClusterRoleNonResource() throws Exception {
    assertThat(
      weblogicOperatorSecurityYaml().getWeblogicOperatorClusterRoleNonResource(),
      equalTo(
        newClusterRole("weblogic-operator-cluster-role-nonresource")
          .addRulesItem(newPolicyRule()
            .addNonResourceURLsItem("/version/*")
            .addVerbsItem("get"))));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorRoleBinding() throws Exception {
    assertThat(
      weblogicOperatorSecurityYaml().getOperatorRoleBinding(),
      equalTo(
        newClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding")
        .addSubjectsItem(newSubject("ServiceAccount", inputs.getServiceAccount(), inputs.getNamespace(), ""))
        .roleRef(newRoleRef("weblogic-operator-cluster-role", "rbac.authorization.k8s.io"))));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorRoleBindingNonResource() throws Exception {
    assertThat(
      weblogicOperatorSecurityYaml().getOperatorRoleBindingNonResource(),
      equalTo(
        newClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding-nonresource")
        .addSubjectsItem(newSubject("ServiceAccount", inputs.getServiceAccount(), inputs.getNamespace(), ""))
        .roleRef(newRoleRef("weblogic-operator-cluster-role-nonresource", "rbac.authorization.k8s.io"))));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorRoleBindingDiscovery() throws Exception {
    assertThat(
      weblogicOperatorSecurityYaml().getOperatorRoleBindingDiscovery(),
      equalTo(
        newClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding-discovery")
        .addSubjectsItem(newSubject("ServiceAccount", inputs.getServiceAccount(), inputs.getNamespace(), ""))
        .roleRef(newRoleRef("system:discovery", "rbac.authorization.k8s.io"))));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_operatorRoleBindingAuthDelegator() throws Exception {
    assertThat(
      weblogicOperatorSecurityYaml().getOperatorRoleBindingAuthDelegator(),
      equalTo(
        newClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding-auth-delegator")
        .addSubjectsItem(newSubject("ServiceAccount", inputs.getServiceAccount(), inputs.getNamespace(), ""))
        .roleRef(newRoleRef("system:auth-delegator", "rbac.authorization.k8s.io"))));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_weblogicOperatorNamespaceRole() throws Exception {
    assertThat(
      weblogicOperatorSecurityYaml().getWeblogicOperatorNamespaceRole(),
      equalTo(
        newClusterRole("weblogic-operator-namespace-role")
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("")
            .resources(asList("secrets", "persistentvolumeclaims"))
            .verbs(asList("get", "list", "watch")))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("storage.k8s.io")
            .addResourcesItem("storageclasses")
            .verbs(asList("get", "list", "watch")))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("")
            .resources(asList("services", "configmaps", "pods", "jobs", "events"))
            .verbs(asList("get", "list", "watch", "create", "update", "patch", "delete", "deletecollection")))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("settings.k8s.io")
            .addResourcesItem("podpresets")
            .verbs(asList("get", "list", "watch", "create", "update", "patch", "delete", "deletecollection")))
          .addRulesItem(newPolicyRule()
            .addApiGroupsItem("extensions")
            .resources(asList("podsecuritypolicies", "networkpolicies"))
            .verbs(asList("get", "list", "watch", "create", "update", "patch", "delete", "deletecollection")))));
  }

  @Test
  public void generatesCorrect_weblogicOperatorSecurityYaml_targetNamespaces_weblogicOperatorRoleBindings() throws Exception {
    for (String targetNamespace : inputs.getTargetNamespaces().split(",")) {
      String ns = targetNamespace.trim();
      assertThat(
        weblogicOperatorSecurityYaml().getWeblogicOperatorRoleBinding(ns),
        equalTo(
          newRoleBinding("weblogic-operator-rolebinding", ns)
            .addSubjectsItem(newSubject("ServiceAccount", inputs.getServiceAccount(), inputs.getNamespace(), ""))
            .roleRef(newRoleRef("weblogic-operator-namespace-role", ""))));
    }
  }

  private ParsedWeblogicOperatorSecurityYaml weblogicOperatorSecurityYaml() {
    return generatedFiles.getWeblogicOperatorSecurityYaml();
  }

  private ParsedWeblogicOperatorYaml weblogicOperatorYaml() {
    return generatedFiles.getWeblogicOperatorYaml();
  }
}
