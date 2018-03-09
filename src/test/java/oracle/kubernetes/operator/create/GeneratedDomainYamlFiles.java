// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.create.ExecCreateDomain.execCreateDomain;
import static oracle.kubernetes.operator.create.ExecResultMatcher.succeedsAndPrints;
import static oracle.kubernetes.operator.create.UserProjects.createUserProjectsDirectory;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Generates the domain yaml files for a set of valid domain input params.
 * Creates and managed the user projects directory that the files are stored in.
 * Parses the generated yaml files into typed java objects.
 */
public class GeneratedDomainYamlFiles {

  private UserProjects userProjects;
  private DomainFiles domainFiles;
  private ParsedDomainCustomResourceYaml domainCustomResourceYaml;
  // TBD - other generated yaml files

  public static GeneratedDomainYamlFiles generateDomainYamlFiles(CreateDomainInputs inputs) throws Exception {
    return new GeneratedDomainYamlFiles(inputs);
  }

  private GeneratedDomainYamlFiles(CreateDomainInputs inputs) throws Exception {
    userProjects = createUserProjectsDirectory();
    boolean ok = false;
    try {
      domainFiles = new DomainFiles(userProjects.getPath(), inputs);
      assertThat(execCreateDomain(userProjects.getPath(), inputs), succeedsAndPrints("Completed"));
      domainCustomResourceYaml =
        new ParsedDomainCustomResourceYaml(domainFiles.getDomainCustomResourceYamlPath(), inputs);
      // TBD - other generated yaml files
      ok = true;
    } finally {
      if (!ok) {
        remove();
      }
    }
  }

  public ParsedDomainCustomResourceYaml getDomainCustomResourceYaml() { return domainCustomResourceYaml; }
  // TBD - other generated yaml files

  public void remove() throws Exception {
    userProjects.remove();
  }
}
