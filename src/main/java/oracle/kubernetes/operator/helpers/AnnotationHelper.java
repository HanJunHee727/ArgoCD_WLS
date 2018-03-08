// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Objects;

import io.kubernetes.client.models.V1ObjectMeta;
import oracle.kubernetes.weblogic.domain.v1.Domain;

/**
 * Annotates pods, services with details about the Domain instance and checks these annotations.
 * 
 */
public class AnnotationHelper {
  
  private static final String DOMAIN_RESOURCE_VERSION = "weblogic.oracle/domain-resourceVersion";
  
  /**
   * Marks metadata object with an annotation saying that it was created for this domain and resource version
   * @param meta Metadata object that will be included in a newly created resource, e.g. pod or service
   * @param domain The domain
   */
  public static void annotateWithDomain(V1ObjectMeta meta, Domain domain) {
    String domainResourceVersion = domain.getMetadata().getResourceVersion();
    if (domainResourceVersion != null) {
      meta.putAnnotationsItem(DOMAIN_RESOURCE_VERSION, domainResourceVersion);
    }
  }


  /**
   * Marks metadata with annotations that let Prometheus know how to retrieve metrics from
   * the wls-exporter web-app.  The specified httpPort should be the listen port of the WebLogic server
   * running in the pod.
   * @param meta Metadata
   * @param httpPort HTTP listen port
   */
  public static void annotateForPrometheus(V1ObjectMeta meta, int httpPort) {
     meta.putAnnotationsItem("prometheus.io/port", "" + httpPort); // should be the ListenPort of the server in the pod
     meta.putAnnotationsItem("prometheus.io/path", "/wls-exporter/metrics");
     meta.putAnnotationsItem("prometheus.io/scrape", "true");
  }

  /**
   * Check the metadata object for the presence of an annotation matching the domain and resource version.l
   * @param meta The metadata object
   * @param domain The domain
   * @return true, if the metadata includes an annotation matching this domain
   */
  public static boolean checkDomainAnnotation(V1ObjectMeta meta, Domain domain) {
    String domainResourceVersion = domain.getMetadata().getResourceVersion();
    String metaResourceVersion = meta.getAnnotations().get(DOMAIN_RESOURCE_VERSION);
    return Objects.equals(domainResourceVersion, metaResourceVersion);
  }
}
