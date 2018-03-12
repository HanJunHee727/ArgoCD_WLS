// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

/**
 * Manages the input and generated files for an operator
 */
public class OperatorFiles {

  public static final String CREATE_SCRIPT = "src/test/scripts/unit-test-create-weblogic-operator.sh";
  private static final String CREATE_WEBLOGIC_OPERATOR_INPUTS_YAML = "create-weblogic-operator-inputs.yaml";
  private static final String WEBLOGIC_OPERATOR_YAML = "weblogic-operator.yaml";
  private static final String WEBLOGIC_OPERATOR_SECURITY_YAML = "weblogic-operator-security.yaml";

  private Path userProjectsPath;
  private CreateOperatorInputs inputs;

  public OperatorFiles(Path userProjectsPath, CreateOperatorInputs inputs) {
    this.userProjectsPath = userProjectsPath;
    this.inputs = inputs;
  }

  public Path userProjectsPath() { return userProjectsPath; }

  public Path getCreateWeblogicOperatorInputsYamlPath() {
    return getWeblogicOperatorPath().resolve(CREATE_WEBLOGIC_OPERATOR_INPUTS_YAML);
  }

  public Path getWeblogicOperatorYamlPath() {
    return getWeblogicOperatorPath().resolve(WEBLOGIC_OPERATOR_YAML);
  }

  public Path getWeblogicOperatorSecurityYamlPath() {
    return getWeblogicOperatorPath().resolve(WEBLOGIC_OPERATOR_SECURITY_YAML);
  }

  public Path getWeblogicOperatorPath() {
    return userProjectsPath().resolve("weblogic-operators").resolve(inputs.getNamespace());
  }
}
