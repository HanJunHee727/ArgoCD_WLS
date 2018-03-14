// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import io.kubernetes.client.models.V1PersistentVolume;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import oracle.kubernetes.weblogic.domain.v1.Domain;

/**
 * Parses a generated weblogic-domain-persistent-volume.yaml file into a set of typed k8s java objects
 */
public class ParsedWeblogicDomainPersistentVolumeYaml {

  private CreateDomainInputs inputs;
  private ParsedKubernetesYaml parsedYaml;

  public ParsedWeblogicDomainPersistentVolumeYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    this.inputs = inputs;
    parsedYaml = new ParsedKubernetesYaml(yamlPath);
  }

  public V1PersistentVolume getWeblogicDomainPersistentVolume() {
    return parsedYaml.getPersistentVolumes().find(inputs.getDomainUid() + "-" + inputs.getPersistenceVolumeName());
  }

  public V1PersistentVolume getExpectedBaseCreateWeblogicDomainPersistentVolume() {
    return
        newPersistentVolume()
          .metadata(newObjectMeta()
            .name(inputs.getDomainUid() + "-" + inputs.getPersistenceVolumeName())
            .putLabelsItem("weblogic.domainUID", inputs.getDomainUid()))
          .spec(newPersistentVolumeSpec()
            .storageClassName(inputs.getDomainUid())
            .putCapacityItem("storage", inputs.getPersistenceSize())
            .addAccessModesItem("ReadWriteMany")
            .persistentVolumeReclaimPolicy("Retain"));
  }
}
