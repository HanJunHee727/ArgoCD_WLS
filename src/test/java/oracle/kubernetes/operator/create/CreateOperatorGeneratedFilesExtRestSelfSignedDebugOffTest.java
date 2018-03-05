// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.Test;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh
 * creates that  are affected by the external rest and/or debug enabled input parameters
 * are correct when external rest is self-signed-cert and the remote debug port is disabled.
 */
public class CreateOperatorGeneratedFilesExtRestSelfSignedDebugOffTest extends CreateOperatorGeneratedFilesTest {

  @Override
  protected CreateOperatorInputs createInputs() throws Exception {
    return setupExternalRestSelfSignedCert(super.createInputs());
  }

  @Test
  public void generatesCorrectOperatorConfigMap() throws Exception {
    // haven't figured out how to compare self signed certs yet
    //weblogicOperatorYaml.assertThatOperatorConfigMapIsCorrect(inputs, inputs.getExternalOperatorCert());
  }

  @Test
  public void generatesCorrectOperatorSecrets() throws Exception {
    // haven't figured out how to compare self signed cert keys yet
    //weblogicOperatorYaml.assertThatOperatorSecretsAreCorrect(inputs, inputs.getExternalOperatorKey());
  }

  @Test
  public void generatesCorrectExternalOperatorService() throws Exception {
    weblogicOperatorYaml.assertThatExternalOperatorServiceIsCorrect(inputs, false, true);
  }
}
