// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1Container;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh
 * creates that are affected by the external rest and/or debug enabled input parameters
 * are correct when external rest is none and the remote debug port is enabled.
 */
public class CreateOperatorGeneratedFilesExtRestNoneDebugOnTest {

  private static CreateOperatorInputs inputs;
  private static GeneratedOperatorYamlFiles generatedFiles;

  @BeforeClass
  public static void setup() throws Exception {
    inputs = CreateOperatorInputs.newInputs().enableDebugging();
    generatedFiles = GeneratedOperatorYamlFiles.generateOperatorYamlFiles(inputs);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (generatedFiles != null) {
      generatedFiles.remove();
    }
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_externalOperatorService() throws Exception {
    assertThat(
      weblogicOperatorYaml().getExternalOperatorService(),
      equalTo(weblogicOperatorYaml().getExpectedExternalOperatorService(true, false)));
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_operatorDeployment() throws Exception {
    ExtensionsV1beta1Deployment want =
      weblogicOperatorYaml().getBaseExpectedOperatorDeployment();
    V1Container operatorContainer = want.getSpec().getTemplate().getSpec().getContainers().get(0);
    operatorContainer.addEnvItem(newEnvVar()
        .name("REMOTE_DEBUG_PORT")
        .value(inputs.getInternalDebugHttpPort()));
    assertThat(weblogicOperatorYaml().getOperatorDeployment(), equalTo(want));
  }

  private ParsedWeblogicOperatorYaml weblogicOperatorYaml() {
    return generatedFiles.getWeblogicOperatorYaml();
  }
}
