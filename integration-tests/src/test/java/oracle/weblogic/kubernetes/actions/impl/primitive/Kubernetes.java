// Copyright (c) 2020, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AdmissionregistrationV1Api;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.apis.PolicyV1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.CoreV1EventList;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1ClusterRoleBindingList;
import io.kubernetes.client.openapi.models.V1ClusterRoleList;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1IngressList;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceBuilder;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodDisruptionBudgetList;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ReplicaSetList;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1RoleBindingList;
import io.kubernetes.client.openapi.models.V1RoleList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceAccountList;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.openapi.models.V1StorageClass;
import io.kubernetes.client.openapi.models.V1StorageClassList;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfiguration;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfigurationList;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.PatchUtils;
import io.kubernetes.client.util.Streams;
import io.kubernetes.client.util.Yaml;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import oracle.weblogic.domain.ClusterList;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.DomainList;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;

import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodInitialized;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Kubernetes {

  private static final String PRETTY = "true";
  private static final Boolean ALLOW_WATCH_BOOKMARKS = false;
  private static final String RESOURCE_VERSION = "";
  private static final String RESOURCE_VERSION_MATCH_UNSET = null;
  private static final Boolean SEND_INITIAL_EVENTS_UNSET = null;
  private static final Integer TIMEOUT_SECONDS = 5;
  private static final String DOMAIN_GROUP = "weblogic.oracle";
  private static final String DOMAIN_PLURAL = "domains";
  private static final String CLUSTER_PLURAL = "clusters";
  private static final String FOREGROUND = "Foreground";
  private static final String BACKGROUND = "Background";
  private static final int GRACE_PERIOD = 0;

  // Core Kubernetes API clients
  private static ApiClient apiClient = null;
  private static CoreV1Api coreV1Api = null;
  private static PolicyV1Api policyV1Api = null;
  private static CustomObjectsApi customObjectsApi = null;
  private static RbacAuthorizationV1Api rbacAuthApi = null;
  private static AdmissionregistrationV1Api admissionregistrationApi = null;
  private static DeleteOptions deleteOptions = null;

  // Extended GenericKubernetesApi clients
  private static GenericKubernetesApi<V1ConfigMap, V1ConfigMapList> configMapClient = null;
  private static GenericKubernetesApi<V1ClusterRoleBinding, V1ClusterRoleBindingList> roleBindingClient = null;
  private static GenericKubernetesApi<DomainResource, DomainList> crdClient = null;
  private static GenericKubernetesApi<ClusterResource, ClusterList> clusterCrdClient = null;
  private static GenericKubernetesApi<V1Job, V1JobList> jobClient = null;
  private static GenericKubernetesApi<V1Namespace, V1NamespaceList> namespaceClient = null;
  private static GenericKubernetesApi<V1Pod, V1PodList> podClient = null;
  private static GenericKubernetesApi<V1PersistentVolume, V1PersistentVolumeList> pvClient = null;
  private static GenericKubernetesApi<V1PersistentVolumeClaim, V1PersistentVolumeClaimList> pvcClient = null;
  private static GenericKubernetesApi<V1Secret, V1SecretList> secretClient = null;
  private static GenericKubernetesApi<V1Service, V1ServiceList> serviceClient = null;
  private static GenericKubernetesApi<V1ServiceAccount, V1ServiceAccountList> serviceAccountClient = null;
  private static GenericKubernetesApi<V1StorageClass, V1StorageClassList> storageClassClient = null;

  static {
    try {
      Configuration.setDefaultApiClient(ClientBuilder.defaultClient());
      apiClient = Configuration.getDefaultApiClient();
      // disable connection and read write timeout to force the internal HTTP client
      // to keep a long-running connection with the server to fix SSL connection closed issue
      apiClient.setConnectTimeout(0);
      apiClient.setReadTimeout(0);
      coreV1Api = new CoreV1Api();
      policyV1Api = new PolicyV1Api();
      customObjectsApi = new CustomObjectsApi();
      rbacAuthApi = new RbacAuthorizationV1Api();
      admissionregistrationApi = new AdmissionregistrationV1Api();
      initializeGenericKubernetesApiClients();
    } catch (IOException ioex) {
      throw new ExceptionInInitializerError(ioex);
    }
  }

  /**
   * Create static instances of GenericKubernetesApi clients.
   */
  private static void initializeGenericKubernetesApiClients() {
    // Invocation parameters aren't changing so create them as statics
    configMapClient =
        new GenericKubernetesApi<>(
            V1ConfigMap.class,  // the api type class
            V1ConfigMapList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "configmaps", // the resource plural
            apiClient //the api client
        );

    crdClient =
        new GenericKubernetesApi<>(
            DomainResource.class,  // the api type class
            DomainList.class, // the api list type class
            DOMAIN_GROUP, // the api group
            DOMAIN_VERSION, // the api version
            DOMAIN_PLURAL, // the resource plural
            apiClient //the api client
        );

    clusterCrdClient =
        new GenericKubernetesApi<>(
            ClusterResource.class,  // the api type class
            ClusterList.class, // the api list type class
            DOMAIN_GROUP, // the api group
            CLUSTER_VERSION, // the api version
            CLUSTER_PLURAL, // the resource plural
            apiClient //the api client
        );    


    jobClient =
        new GenericKubernetesApi<>(
            V1Job.class,  // the api type class
            V1JobList.class, // the api list type class
            "batch", // the api group
            "v1", // the api version
            "jobs", // the resource plural
            apiClient //the api client
        );

    namespaceClient =
        new GenericKubernetesApi<>(
            V1Namespace.class, // the api type class
            V1NamespaceList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "namespaces", // the resource plural
            apiClient //the api client
        );

    podClient =
        new GenericKubernetesApi<>(
            V1Pod.class,  // the api type class
            V1PodList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "pods", // the resource plural
            apiClient //the api client
        );

    pvClient =
        new GenericKubernetesApi<>(
            V1PersistentVolume.class,  // the api type class
            V1PersistentVolumeList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "persistentvolumes", // the resource plural
            apiClient //the api client
        );

    pvcClient =
        new GenericKubernetesApi<>(
            V1PersistentVolumeClaim.class,  // the api type class
            V1PersistentVolumeClaimList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "persistentvolumeclaims", // the resource plural
            apiClient //the api client
        );

    roleBindingClient =
        new GenericKubernetesApi<>(
            V1ClusterRoleBinding.class, // the api type class
            V1ClusterRoleBindingList.class, // the api list type class
            "rbac.authorization.k8s.io", // the api group
            "v1", // the api version
            "clusterrolebindings", // the resource plural
            apiClient //the api client
        );

    secretClient =
        new GenericKubernetesApi<>(
            V1Secret.class,  // the api type class
            V1SecretList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "secrets", // the resource plural
            apiClient //the api client
        );

    serviceClient =
        new GenericKubernetesApi<>(
            V1Service.class,  // the api type class
            V1ServiceList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "services", // the resource plural
            apiClient //the api client
        );

    serviceAccountClient =
        new GenericKubernetesApi<>(
            V1ServiceAccount.class,  // the api type class
            V1ServiceAccountList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "serviceaccounts", // the resource plural
            apiClient //the api client
        );

    storageClassClient =
        new GenericKubernetesApi<>(
            V1StorageClass.class, // the api type class
            V1StorageClassList.class, // the api list type class
            "storage.k8s.io", // the api group
            "v1", // the api version
            "storageclasses", // the resource plural
            apiClient //the api client
        );
    deleteOptions = new DeleteOptions();
    deleteOptions.setGracePeriodSeconds(0L);
    deleteOptions.setPropagationPolicy(FOREGROUND);
  }

  // ------------------------  deployments -----------------------------------

  /**
   * Create a deployment.
   *
   * @param deployment V1Deployment object containing deployment configuration data
   * @return true if creation was successful
   * @throws ApiException when create fails
   */
  public static boolean createDeployment(V1Deployment deployment) throws ApiException {
    String namespace = deployment.getMetadata().getNamespace();
    boolean status = false;
    try {
      AppsV1Api apiInstance = new AppsV1Api(apiClient);
      V1Deployment createdDeployment = apiInstance.createNamespacedDeployment(
          namespace, // String | namespace in which to create job
          deployment, // V1Deployment | body of the V1Deployment containing deployment data
          PRETTY, // String | pretty print output.
          null, // String | dry run or permanent change
          null, // String | field manager who is making the change
          null // String | field validation
      );
      if (createdDeployment != null) {
        status = true;
      }
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
    return status;
  }

  /**
   * List deployments in the given namespace.
   *
   * @param namespace namespace in which to list the deployments
   * @return list of deployment objects as {@link V1DeploymentList}
   * @throws ApiException when listing fails
   */
  public static V1DeploymentList listDeployments(String namespace) throws ApiException {
    V1DeploymentList deployments;
    try {
      AppsV1Api apiInstance = new AppsV1Api(apiClient);
      deployments = apiInstance.listNamespacedDeployment(
          namespace, // String | namespace.
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );

    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return deployments;
  }

  /**
   * Delete a deployment.
   *
   * @param namespace namespace in which to delete the deployment
   * @param name deployment name
   * @return true if deletion was successful
   * @throws ApiException when delete fails
   */
  public static boolean deleteDeployment(String namespace, String name) throws ApiException {
    try {
      AppsV1Api apiInstance = new AppsV1Api(apiClient);
      apiInstance.deleteNamespacedDeployment(
          name, // String | deployment object name.
          namespace, // String | namespace in which the deployment exists.
          PRETTY, // String | If 'true', then the output is pretty printed.
          null, // String | When present, indicates that modifications should not be persisted.
          GRACE_PERIOD, // Integer | The duration in seconds before the object should be deleted.
          null,
          null, // Boolean | Deprecated: use the PropagationPolicy.
          FOREGROUND, // String | Whether and how garbage collection will be performed.
          null // V1DeleteOptions.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return true;
  }

  // --------------------------- pods -----------------------------------------

  /**
   * Get a pod's log.
   *
   * @param name name of the Pod
   * @param namespace name of the Namespace
   * @return log as a String
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodLog(String name, String namespace) throws ApiException {
    return getPodLog(name, namespace, null);
  }

  /**
   * Get a pod's log.
   *
   * @param name name of the Pod
   * @param namespace name of the Namespace
   * @param container name of container for which to stream logs
   * @return log as a String or NULL when there is an error
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodLog(String name, String namespace, String container) throws ApiException {
    return getPodLog(name, namespace, container, null, null);
  }

  /**
   * Get a pod's log.
   *
   * @param name name of the Pod
   * @param namespace name of the Namespace
   * @param container name of container for which to stream logs
   * @param previous whether return previous terminated container logs
   * @param sinceSeconds relative time in seconds before the current time from which to show logs
   * @return log as a String or NULL when there is an error
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodLog(String name,
                                 String namespace,
                                 String container,
                                 Boolean previous,
                                 Integer sinceSeconds)
      throws ApiException {
    return getPodLog(name, namespace, container, previous, sinceSeconds, null);
  }

  /**
   * Get a pod's log.
   *
   * @param name name of the Pod
   * @param namespace name of the Namespace
   * @param container name of container for which to stream logs
   * @param previous whether return previous terminated container logs
   * @param sinceSeconds relative time in seconds before the current time from which to show logs
   * @param follow whether to follow the log stream of the pod
   * @return log as a String or NULL when there is an error
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodLog(String name,
                                 String namespace,
                                 String container,
                                 Boolean previous,
                                 Integer sinceSeconds,
                                 Boolean follow)
      throws ApiException {
    String log = null;
    checkPodInitialized(name,null,namespace);
    try {
      log = coreV1Api.readNamespacedPodLog(
          name, // name of the Pod
          namespace, // name of the Namespace
          container, // container for which to stream logs
          follow, //  Boolean Follow the log stream of the pod
          null, // skip TLS verification of backend
          null, // number of bytes to read from the server before terminating the log output
          PRETTY, // pretty print output
          previous, // Boolean, Return previous terminated container logs
          sinceSeconds, // relative time (seconds) before the current time from which to show logs
          null,
          null, // number of lines from the end of the logs to show
          null // Boolean, add timestamp at the beginning of every line of log output
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
    return log;
  }

  /**
   * Create a pod.
   *
   * @param namespace name of the namespace
   * @param podBody V1Pod object containing pod configuration data
   * @return V1Pod object
   * @throws ApiException when create pod fails
   */
  public static V1Pod createPod(String namespace, V1Pod podBody) throws ApiException {
    V1Pod pod;
    try {
      pod = coreV1Api.createNamespacedPod(namespace, podBody, null, null, null, null);
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
    return pod;
  }

  /**
   * Delete a Kubernetes Pod.
   *
   * @param name name of the pod
   * @param namespace name of namespace
   * @return true if successful
   */
  public static boolean deletePod(String name, String namespace) {

    KubernetesApiResponse<V1Pod> response = podClient.delete(namespace, name);

    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete pod '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
              + "pod in background!");
    }

    return true;
  }

  /**
   * Returns the V1Pod object given the following parameters.
   *
   * @param namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod to return
   * @return V1Pod object if found otherwise null
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1Pod getPod(String namespace, String labelSelector, String podName) throws ApiException {
    V1PodList pods = listPods(namespace, labelSelector);
    // log pod names for debugging
    for (var pod : pods.getItems()) {
      getLogger().info("DEBUG: Pod Name returned by listPods call {0}", pod.getMetadata().getName());
    }
    for (var pod : pods.getItems()) {
      if (pod.getMetadata().getName().contains(podName)) {
        return pod;
      }
    }
    return null;
  }

  /**
   * Returns the IP address allocated to the pod with following parameters.
   *
   * @param namespace in which to check for the pod existence
   * @param labelSelectors in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod to return
   * @return IP address allocated to the pod
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodIP(String namespace, String labelSelectors, String podName) throws ApiException {
    V1Pod pod = getPod(namespace, labelSelectors, podName);
    return pod.getStatus().getPodIP();
  }

  /**
   * Returns the status phase of the pod.
   *
   * @param namespace in which to check for the pod status
   * @param labelSelectors in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod to check
   * @return the status phase of the pod
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodStatusPhase(String namespace, String labelSelectors, String podName)
      throws ApiException {
    V1Pod pod = getPod(namespace, labelSelectors, podName);
    if (pod != null && pod.getStatus() != null) {
      return pod.getStatus().getPhase();
    } else {
      getLogger().info("Pod does not exist or pod status is null");
      return null;
    }
  }

  /**
   * Get the creationTimestamp for a given pod with following parameters.
   *
   * @param namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName  name of the pod
   * @return creationTimestamp DateTime from metadata of the Pod
   * @throws ApiException if Kubernetes client API call fail
   */
  public static OffsetDateTime getPodCreationTimestamp(String namespace, String labelSelector, String podName)
      throws ApiException {

    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null && pod.getMetadata() != null) {
      return pod.getMetadata().getCreationTimestamp();
    } else if (pod == null) {
      getLogger().info("Pod {0} does not exist in namespace {1}", podName, namespace);
    } else {
      getLogger().info("The metadata of Pod {0} in namespace {1} is null", podName, namespace);
    }
    return null;
  }

  /**
   * Get the container's restart count in the pod.
   * @param namespace name of the pod's namespace
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod
   * @param containerName name of the container, null if there is only one container
   * @return restart count of the container
   * @throws ApiException if Kubernetes client API call fails
   */
  public static int getContainerRestartCount(
      String namespace, String labelSelector, String podName, String containerName)
      throws ApiException {

    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null && pod.getStatus() != null) {
      List<V1ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
      // if containerName is null, get first container restart count
      if (containerName == null && !containerStatuses.isEmpty()) {
        return containerStatuses.getFirst().getRestartCount();
      } else {
        for (V1ContainerStatus containerStatus : containerStatuses) {
          if (containerName.equals(containerStatus.getName())) {
            return containerStatus.getRestartCount();
          }
        }
        getLogger().severe("Container {0} status doesn't exist or pod's container statuses is empty in namespace {1}",
            containerName, namespace);
      }
    } else {
      getLogger().severe("Pod {0} doesn't exist or pod status is null in namespace {1}",
          podName, namespace);
    }
    return 0;
  }

  /**
   * Get the container's image in the pod.
   * @param namespace name of the pod's namespace
   * @param labelSelector in the format "weblogic.operatorName in (%s)"
   * @param podName name of the pod
   * @param containerName name of the specific container if more then one, null if there is only one container
   * @return image used for the container
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getContainerImage(String namespace, String podName,
                                         String labelSelector, String containerName) throws ApiException {
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null) {
      List<V1Container> containerList = pod.getSpec().getContainers();
      if (containerName == null && !containerList.isEmpty()) {
        return containerList.getFirst().getImage();
      } else {
        for (V1Container container : containerList) {
          if (containerName.equals(container.getName())) {
            return container.getImage();
          }
        }
        getLogger().info("Container {0} doesn't exist in pod {1} namespace {2}",
            containerName, podName, namespace);
      }
    } else {
      getLogger().severe("Pod " + podName + " doesn't exist in namespace " + namespace);
    }
    return null;
  }

  /**
   * Get the weblogic.domainRestartVersion label from a given pod.
   *
   * @param namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName  name of the pod
   * @return value of weblogic.domainRestartVersion label, null if unset or the pod is not available
   * @throws ApiException when there is error in querying the cluster
   */
  public static String getPodRestartVersion(String namespace, String labelSelector, String podName)
      throws ApiException {
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null) {
      // return the value of the weblogic.domainRestartVersion label
      return pod.getMetadata().getLabels().get("weblogic.domainRestartVersion");
    } else {
      getLogger().info("getPodRestartVersion(): Pod doesn't exist");
      return null;
    }
  }

  /**
   * Get the introspectVersion label from a given pod.
   *
   * @param namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName  name of the pod
   * @return value of introspectVersion label, null if unset or the pod is not available
   * @throws ApiException when there is error in querying the cluster
   */
  public static String getPodIntrospectVersion(String namespace, String labelSelector, String podName)
      throws ApiException {
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null) {
      // return the value of the introspectVersion label
      return pod.getMetadata().getLabels().get("weblogic.introspectVersion");
    } else {
      getLogger().info("getPodIntrospectVersion(): Pod doesn't exist");
      return null;
    }
  }

  /**
   * List all pods in given namespace.
   *
   * @param namespace Namespace in which to list all pods
   * @param labelSelectors with which the pods are decorated
   * @return V1PodList list of pods or NULL when there is an error
   * @throws ApiException when there is error in querying the cluster
   */
  public static V1PodList listPods(String namespace, String labelSelectors) throws ApiException {
    V1PodList v1PodList = null;
    try {
      v1PodList
          = coreV1Api.listNamespacedPod(
          namespace, // namespace in which to look for the pods.
          Boolean.FALSE.toString(), // pretty print output.
          Boolean.FALSE, // allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // continue to query when there is more results to return.
          null, // selector to restrict the list of returned objects by their fields
          labelSelectors, // selector to restrict the list of returned objects by their labels.
          null, // maximum number of responses to return for a list call.
          null, // shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          null, // Timeout for the list/watch call.
          Boolean.FALSE // Watch for changes to the described resources.
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
    return v1PodList;
  }

  /**
   * List all pod disruption budgets in given namespace.
   *
   * @param namespace Namespace in which to list all pods
   * @param labelSelectors with which the pdbs are decorated
   * @return V1PodDisruptionBudget list of pdbs or NULL when there is an error
   * @throws ApiException when there is error in querying the cluster
   */
  public static V1PodDisruptionBudgetList listPodDisruptionBudgets(String namespace, String labelSelectors)
      throws ApiException {
    try {
      return policyV1Api.listNamespacedPodDisruptionBudget(
          namespace, // namespace in which to look for the pods.
          Boolean.FALSE.toString(), // pretty print output.
          Boolean.FALSE, // allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // continue to query when there is more results to return.
          null, // selector to restrict the list of returned objects by their fields
          labelSelectors, // selector to restrict the list of returned objects by their labels.
          null, // maximum number of responses to return for a list call.
          null, // shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          null, // Timeout for the list/watch call.
          Boolean.FALSE // Watch for changes to the described resources.
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
  }

  private static boolean isNullOrEmpty(String str) {
    return str == null || str.isEmpty();
  }

  /**
   * Copy a directory from Kubernetes pod to local destination path.
   * @param pod V1Pod object
   * @param srcPath source directory location
   * @param destination destination directory path
   */
  public static void copyDirectoryFromPod(V1Pod pod, String srcPath, Path destination) {
    String namespace = pod.getMetadata().getNamespace();
    String podName = pod.getMetadata().getName();

    // kubectl exec -n <some-namespace> <some-pod> -- tar cf - /tmp/foo | tar xf - -C /tmp/bar
    StringBuilder sb = new StringBuilder();
    sb.append(KUBERNETES_CLI + " exec ");
    if (!isNullOrEmpty(namespace)) {
      sb.append("-n ");
      sb.append(namespace);
      sb.append(" ");
    }
    sb.append(podName);
    sb.append(" -- tar cf - ");
    sb.append(srcPath);
    sb.append(" | tar xf - -C ");
    sb.append(destination.toString());
    String cmdToExecute = sb.toString();
    Command
        .withParams(new CommandParams().command(cmdToExecute))
        .execute();
  }

  /**
   * Copy a file from local filesystem to Kubernetes pod.
   * @param namespace namespace of the pod
   * @param pod name of the pod where the file is copied to
   * @param container name of the container
   * @param srcPath source file location
   * @param destPath destination file location on pod
   * @return true if copy succeeds, otherwise false
   */
  public static boolean copyFileToPod(
      String namespace, String pod, String container, Path srcPath, Path destPath) {
    // kubectl cp /tmp/foo <some-namespace>/<some-pod>:/tmp/bar -c <specific-container>
    StringBuilder sb = new StringBuilder();
    sb.append(KUBERNETES_CLI + " cp ");
    sb.append(srcPath.toString());
    sb.append(" ");
    if (!isNullOrEmpty(namespace)) {
      sb.append(namespace);
      sb.append("/");
    }
    sb.append(pod);
    sb.append(":");
    sb.append(destPath.toString());
    if (!isNullOrEmpty(container)) {
      sb.append(" -c ");
      sb.append(container);
    }
    String cmdToExecute = sb.toString();

    return Command.withParams(new CommandParams().command(cmdToExecute)).execute();
  }

  /**
   * Copy a file from Kubernetes pod to local filesystem.
   * @param namespace namespace of the pod
   * @param pod name of the pod where the file is copied from
   * @param container name of the container
   * @param srcPath source file location on the pod
   * @param destPath destination file location on local filesystem
   */
  public static void copyFileFromPod(String namespace, String pod, String container, String srcPath, Path destPath) {
    // kubectl cp <some-namespace>/<some-pod>:/tmp/foo /tmp/bar -c <container>
    StringBuilder sb = new StringBuilder();
    sb.append(KUBERNETES_CLI + " cp ");
    if (!isNullOrEmpty(namespace)) {
      sb.append(namespace);
      sb.append("/");
    }
    sb.append(pod);
    sb.append(":");
    sb.append(srcPath);
    sb.append(" ");
    sb.append(destPath.toString());
    if (!isNullOrEmpty(container)) {
      sb.append(" -c ");
      sb.append(container);
    }
    String cmdToExecute = sb.toString();
    Command
        .withParams(new CommandParams().command(cmdToExecute))
        .execute();
  }

  // --------------------------- namespaces -----------------------------------
  /**
   * Create a Kubernetes namespace.
   *
   * @param name the name of the namespace
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createNamespace(String name) throws ApiException {
    V1ObjectMeta meta = new V1ObjectMetaBuilder().withName(name).build();
    V1Namespace namespace = new V1NamespaceBuilder().withMetadata(meta).build();

    try {
      coreV1Api.createNamespace(
          namespace, // name of the Namespace
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // name associated with the actor or entity that is making these changes
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  // --------------------------- namespaces -----------------------------------
  /**
   * Create a Kubernetes namespace.
   *
   * @param name the name of the namespace
   * @param labels list of labels for the namespace
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createNamespace(String name, Map<String, String> labels) throws ApiException {
    V1ObjectMeta meta = new V1ObjectMetaBuilder().withName(name).withLabels(labels).build();
    V1Namespace namespace = new V1NamespaceBuilder().withMetadata(meta).build();

    try {
      coreV1Api.createNamespace(
          namespace, // name of the Namespace
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // name associated with the actor or entity that is making these changes
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Create a Kubernetes namespace.
   *
   * @param namespace - V1Namespace object containing namespace configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createNamespace(V1Namespace namespace) throws ApiException {
    if (namespace == null) {
      throw new IllegalArgumentException(
          "Parameter 'namespace' cannot be null when calling createNamespace()");
    }

    try {
      coreV1Api.createNamespace(
          namespace, // V1Namespace configuration data object
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // name associated with the actor or entity that is making these changes
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Replace a existing namespace with configuration changes.
   *
   * @param ns V1Namespace object
   * @throws ApiException when replacing namespace fails
   */
  public static void replaceNamespace(V1Namespace ns) throws ApiException {

    try {
      coreV1Api.replaceNamespace(
          ns.getMetadata().getName(), // name of the namespace
          ns, // V1Namespace object body
          PRETTY, // pretty print the output
          null, // dry run or changes need to be permanent
          null, // field manager
          null // field validation
      );
    } catch (ApiException ex) {
      getLogger().severe(ex.getResponseBody());
      throw ex;
    }
  }

  /**
   * List namespaces in the Kubernetes cluster.
   * @return List of all Namespace names in the Kubernetes cluster
   * @throws ApiException if Kubernetes client API call fails
   */
  public static List<String> listNamespaces() throws ApiException {
    return listNamespaces(null);
  }

  /**
   * List namespaces in the Kubernetes cluster matching the label selector.
   * @return List of all Namespace names in the Kubernetes cluster
   * @throws ApiException if Kubernetes client API call fails
   */
  public static List<String> listNamespaces(String labelSelector) throws ApiException {
    ArrayList<String> nameSpaces = new ArrayList<>();
    V1NamespaceList namespaceList;
    try {
      namespaceList = coreV1Api.listNamespace(
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          labelSelector, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    for (V1Namespace namespace : namespaceList.getItems()) {
      nameSpaces.add(namespace.getMetadata().getName());
    }

    return nameSpaces;
  }

  /**
   * List namespaces in the Kubernetes cluster as V1NamespaceList.
   * @return V1NamespaceList of Namespace in the Kubernetes cluster
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1NamespaceList listNamespacesAsObjects() throws ApiException {
    V1NamespaceList namespaceList;
    try {
      namespaceList = coreV1Api.listNamespace(
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          null, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return namespaceList;
  }

  /**
   * Gets namespace.
   * @param name name of namespace.
   * @return V1Namespace  Namespace object from the Kubernetes cluster
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1Namespace getNamespace(String name) throws ApiException {
    try {
      V1NamespaceList namespaceList = coreV1Api.listNamespace(
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          null, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );

      if (!namespaceList.getItems().isEmpty()) {
        for (V1Namespace ns : namespaceList.getItems()) {
          if (ns.getMetadata().getName().equalsIgnoreCase(name)) {
            return ns;
          }
        }
      }
      return null;
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
  }

  /**
   * Delete a namespace for the given name.
   *
   * @param name name of namespace
   * @return true if successful delete request, false otherwise.
   */
  public static boolean deleteNamespace(String name) {

    KubernetesApiResponse<V1Namespace> response = namespaceClient.delete(name);

    if (!response.isSuccess()) {
      // status 409 means contents in the namespace being removed,
      // once done namespace will be purged
      if (response.getHttpStatusCode() == 409) {
        getLogger().warning(response.getStatus().getMessage());
        return false;
      } else {
        getLogger().warning("Failed to delete namespace: "
            + name + " with HTTP status code: " + response.getHttpStatusCode());
        return false;
      }
    }

    testUntil(
        assertDoesNotThrow(() -> namespaceDeleted(name),
          String.format("namespaceExists failed with ApiException for namespace %s", name)),
        getLogger(),
        "namespace {0} to be deleted",
        name);

    return true;
  }

  private static Callable<Boolean> namespaceDeleted(String namespace) {
    return () -> {
      List<String> namespaces = listNamespaces();
      if (!namespaces.contains(namespace)) {
        return true;
      }
      return  false;
    };
  }

  // --------------------------- Events ---------------------------------------------------

  /**
   * List events in a namespace.
   *
   * @param namespace name of the namespace in which to list events
   * @return List of {@link CoreV1Event} objects
   * @throws ApiException when listing events fails
   */
  public static List<CoreV1Event> listNamespacedEvents(String namespace) throws ApiException {
    List<CoreV1Event> events = null;
    try {
      CoreV1EventList list = coreV1Api.listNamespacedEvent(
          namespace, // String | namespace.
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );
      events = Optional.ofNullable(list).map(CoreV1EventList::getItems).orElse(Collections.emptyList());
      events.sort(Comparator.comparing(CoreV1Event::getLastTimestamp,
          Comparator.nullsFirst(Comparator.naturalOrder())));
      Collections.reverse(events);
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return events;
  }

  /**
   * List operator generated events in a namespace.
   *
   * @param namespace name of the namespace in which to list events
   * @return List of {@link CoreV1Event} objects
   * @throws ApiException when listing events fails
   */
  public static List<CoreV1Event> listOpGeneratedNamespacedEvents(String namespace) throws ApiException {
    List<CoreV1Event> events = null;
    try {
      CoreV1EventList list = coreV1Api.listNamespacedEvent(
          namespace, // String | namespace.
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          "weblogic.createdByOperator", // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );
      events = Optional.ofNullable(list).map(CoreV1EventList::getItems).orElse(Collections.emptyList());
      events.sort(Comparator.comparing(CoreV1Event::getLastTimestamp,
          Comparator.nullsFirst(Comparator.naturalOrder())));
      Collections.reverse(events);
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return events;
  }

  // --------------------------- Custom Resource Domain -----------------------------------
  /**
   * Create a Domain Custom Resource.
   *
   * @param domain Domain custom resource model object
   * @param domVersion custom resource's version
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createDomainCustomResource(DomainResource domain, String... domVersion) throws ApiException {
    String domainVersion = (domVersion.length == 0) ? DOMAIN_VERSION : domVersion[0];

    if (domain == null) {
      throw new IllegalArgumentException(
          "Parameter 'domain' cannot be null when calling createDomainCustomResource()");
    }

    if (domain.metadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'domain' cannot be null when calling createDomainCustomResource()");
    }

    if (domain.metadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createDomainCustomResource()");
    }

    String namespace = domain.metadata().getNamespace();

    JsonElement json = convertToJson(domain);

    try {
      customObjectsApi.createNamespacedCustomObject(
          DOMAIN_GROUP, // custom resource's group name
          domainVersion, //custom resource's version
          namespace, // custom resource's namespace
          DOMAIN_PLURAL, // custom resource's plural name
          json, // JSON schema of the Resource to create
          null, // pretty print output
          null, // dry run
          null, // field manager
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }
  
  /**
   * Converts a Java Object to a JSON element.
   *
   * @param obj java object to be converted
   * @return object representing Json element
   */
  private static JsonElement convertToJson(Object obj) {
    Gson gson = apiClient.getJSON().getGson();
    return gson.toJsonTree(obj);
  }

  /**
   * Delete the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteDomainCustomResource(String domainUid, String namespace) {

    KubernetesApiResponse<DomainResource> response = crdClient.delete(namespace, domainUid, deleteOptions);

    if (!response.isSuccess()) {
      getLogger().warning(
          "Failed to delete Domain Custom Resource '" + domainUid + "' from namespace: "
              + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
              + "domain custom resource in background!");
    }

    return true;
  }

  /**
   * Get the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return domain custom resource or null if Domain does not exist
   * @throws ApiException if Kubernetes request fails
   */
  public static DomainResource getDomainCustomResource(String domainUid, String namespace)
      throws ApiException {
    return getDomainCustomResource(domainUid, namespace, DOMAIN_VERSION);
  }

  /**
   * Get the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param domainVersion version of domain
   * @return domain custom resource or null if Domain does not exist
   * @throws ApiException if Kubernetes request fails
   */
  public static DomainResource getDomainCustomResource(String domainUid, String namespace, String domainVersion)
      throws ApiException {
    Object domain;
    try {
      domain = customObjectsApi.getNamespacedCustomObject(
          DOMAIN_GROUP, // custom resource's group name
          domainVersion, // //custom resource's version
          namespace, // custom resource's namespace
          DOMAIN_PLURAL, // custom resource's plural name
          domainUid // custom object's name
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    if (domain != null) {
      return handleResponse(domain, DomainResource.class);
    }

    getLogger().warning("Domain Custom Resource '" + domainUid + "' not found in namespace " + namespace);
    return null;
  }

  /**
   * Patch the Domain Custom Resource using JSON Patch.JSON Patch is a format for describing changes to a JSON document
   * using a series of operations. JSON Patch is specified in RFC 6902 from the IETF. For example, the following
   * operation will replace the "spec.restartVersion" to a value of "2".
   *
   * <p>[
   *      {"op": "replace", "path": "/spec/restartVersion", "value": "2" }
   *    ]
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patchString JSON Patch document as a String
   * @return true if patch is successful otherwise false
   */
  public static boolean patchCustomResourceDomainJsonPatch(String domainUid, String namespace,
                                                           String patchString) {
    return patchDomainCustomResource(
        domainUid, // name of custom resource domain
        namespace, // name of namespace
        new V1Patch(patchString), // patch data
        V1Patch.PATCH_FORMAT_JSON_PATCH // "application/json-patch+json" patch format
    );
  }

  /**
   * Patch the Domain Custom Resource using JSON Merge Patch.JSON Merge Patch is a format for describing a changed
   * version to a JSON document. JSON Merge Patch is specified in RFC 7396 from the IETF. For example, the following
   * JSON object fragment would add/replace the "spec.restartVersion" to a value of "1".
   *
   * <p>{
   *      "spec" : { "restartVersion" : "1" }
   *    }
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patchString JSON Patch document as a String
   * @return true if patch is successful otherwise false
   */
  public static boolean patchCustomResourceDomainJsonMergePatch(String domainUid, String namespace,
                                                                String patchString) {
    return patchDomainCustomResource(
        domainUid, // name of custom resource domain
        namespace, // name of namespace
        new V1Patch(patchString), // patch data
        V1Patch.PATCH_FORMAT_JSON_MERGE_PATCH // "application/merge-patch+json" patch format
    );
  }

  /**
   * Patch the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *     "application/json-patch+json", "application/merge-patch+json",
   * @return true if successful, false otherwise
   */
  public static boolean patchDomainCustomResource(String domainUid, String namespace,
                                                  V1Patch patch, String patchFormat) {

    // GenericKubernetesApi uses CustomObjectsApi calls
    KubernetesApiResponse<DomainResource> response = crdClient.patch(
        namespace, // name of namespace
        domainUid, // name of custom resource domain
        patchFormat, // "application/json-patch+json" or "application/merge-patch+json"
        patch // patch data
    );

    if (!response.isSuccess()) {
      getLogger().warning(
          "Failed with response code " + response.getHttpStatusCode() + " response message "
              + Optional.ofNullable(response.getStatus()).map(V1Status::getMessage).orElse("none")
              + " when patching " + domainUid + " in namespace "
              + namespace + " with " + patch + " using patch format: " + patchFormat);
      return false;
    }

    return true;
  }

  /**
   * Patch the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *     "application/json-patch+json", "application/merge-patch+json",
   * @return response msg of patching domain resouce
   */
  public static String patchDomainCustomResourceReturnResponse(String domainUid, String namespace,
                                                               V1Patch patch, String patchFormat) {
    String responseMsg = "";
    // GenericKubernetesApi uses CustomObjectsApi calls
    KubernetesApiResponse<DomainResource> response = crdClient.patch(
        namespace, // name of namespace
        domainUid, // name of custom resource domain
        patchFormat, // "application/json-patch+json" or "application/merge-patch+json"
        patch // patch data
    );

    String logmsg = "response code: " + response.getHttpStatusCode() + ". response message: "
        + Optional.ofNullable(response.getStatus()).map(V1Status::getMessage).orElse("none")
        + " when patching " + domainUid + " in namespace "
        + namespace + " with " + patch + " using patch format: " + patchFormat;

    if (!response.isSuccess()) {
      responseMsg = "Failed with " + logmsg;
    } else {
      responseMsg = "Succeeded with " + logmsg;
    }
    getLogger().info(responseMsg);

    return responseMsg;
  }

  // --------------------------- Custom Resource Domain -----------------------------------
  /**
   * Create a Cluster Custom Resource.
   *
   * @param cluster Cluster custom resource model object
   * @param clusterVersion Version custom resource's version
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createClusterCustomResource(ClusterResource cluster, String clusterVersion)
      throws ApiException {

    if (cluster == null) {
      throw new IllegalArgumentException(
          "Parameter 'cluster' cannot be null when calling createClusterCustomResource()");
    }

    if (cluster.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'cluster' cannot be null when calling createClusterCustomResource()");
    }

    if (cluster.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createClusterCustomResource()");
    }

    String namespace = cluster.getMetadata().getNamespace();

    JsonElement json = convertToJson(cluster);

    try {
      customObjectsApi.createNamespacedCustomObject(
          DOMAIN_GROUP, // custom resource's group name
          clusterVersion, //custom resource's version
          namespace, // custom resource's namespace
          CLUSTER_PLURAL, // custom resource's plural name
          json, // JSON schema of the Resource to create
          null, // pretty print output
          null, // dry run
          null, // field manager
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Get the Cluster Custom Resource.
   *
   * @param clusterResName name of the cluster custom resource
   * @param namespace name of namespace
   * @param clusterVersion version of cluster
   * @return cluster custom resource or null if ClusterResource does not exist
   * @throws ApiException if Kubernetes request fails
   */
  public static ClusterResource getClusterCustomResource(String clusterResName, String namespace, String clusterVersion)
      throws ApiException {
    Object cluster;
    try {
      cluster = customObjectsApi.getNamespacedCustomObject(
          DOMAIN_GROUP, // custom resource's group name
          clusterVersion, // //custom resource's version
          namespace, // custom resource's namespace
          CLUSTER_PLURAL, // custom resource's plural name
          clusterResName // custom object's name
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    if (cluster != null) {
      return handleResponse(cluster, ClusterResource.class);
    }

    getLogger().warning("Cluster Custom Resource '" + clusterResName + "' not found in namespace " + namespace);
    return null;
  }

  /**
   * Get the Cluster Custom Resource.
   *
   * @param clusterResName name of the cluster custom resource
   * @param namespace name of namespace
   * @return cluster custom resource or null if ClusterResource does not exist
   * @throws ApiException if Kubernetes request fails
   */
  public static ClusterResource getClusterCustomResource(String clusterResName, String namespace) throws ApiException {
    return getClusterCustomResource(clusterResName,namespace, CLUSTER_VERSION);
  }
  
  /**
   * List Cluster Custom Resources in a given namespace.
   *
   * @param namespace name of namespace
   * @return List of Cluster Custom Resources
   */
  public static ClusterList listClusters(String namespace) {
    KubernetesApiResponse<ClusterList> response = null;
    try {
      response = clusterCrdClient.list(namespace);
    } catch (Exception ex) {
      getLogger().warning(ex.getMessage());
      throw ex;
    }
    return response != null ? response.getObject() : new ClusterList();
  }
  
  /**
   * Delete the Cluster Custom Resource.
   *
   * @param clusterName unique cluster identifier
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteClusterCustomResource(String clusterName, String namespace) {

    // GenericKubernetesApi uses CustomObjectsApi calls
    KubernetesApiResponse<ClusterResource> response = clusterCrdClient.delete(namespace, clusterName);

    if (!response.isSuccess()) {
      getLogger().warning(
          "Failed to delete cluster custom resource, response code " + response.getHttpStatusCode()
          + " response message " + Optional.ofNullable(response.getStatus()).map(V1Status::getMessage).orElse("none")
          + " when deleting " + clusterName + " in namespace " + namespace);
      return false;
    }

    return true;
  }
  
  /**
   * Patch the Cluster Custom Resource.
   *
   * @param clusterName name of the cluster to be patched
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *     "application/json-patch+json", "application/merge-patch+json",
   * @return true if successful, false otherwise
   */
  public static boolean patchClusterCustomResource(String clusterName, String namespace,
                                                  V1Patch patch, String patchFormat) {

    // GenericKubernetesApi uses CustomObjectsApi calls
    KubernetesApiResponse<ClusterResource> response = clusterCrdClient.patch(
        namespace, // name of namespace
        clusterName, // name of custom resource domain
        patchFormat, // "application/json-patch+json" or "application/merge-patch+json"
        patch // patch data
    );

    if (!response.isSuccess()) {
      getLogger().warning(
          "Failed with response code " + response.getHttpStatusCode() + " response message "
              + Optional.ofNullable(response.getStatus()).map(V1Status::getMessage).orElse("none")
              + " when patching " + clusterName + " in namespace "
              + namespace + " with " + patch + " using patch format: " + patchFormat);
      return false;
    }

    return true;
  }

  /**
   * Patch the Cluster Custom Resource.
   *
   * @param clusterName name of the cluster to be patched
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *     "application/json-patch+json", "application/merge-patch+json",
   * @return response msg of patching cluster resource
   */
  public static String patchClusterCustomResourceReturnResponse(String clusterName, String namespace,
                                                                V1Patch patch, String patchFormat) {
    String responseMsg;
    // GenericKubernetesApi uses CustomObjectsApi calls
    KubernetesApiResponse<ClusterResource> response = clusterCrdClient.patch(
        namespace, // name of namespace
        clusterName, // name of custom resource domain
        patchFormat, // "application/json-patch+json" or "application/merge-patch+json"
        patch // patch data
    );

    String logmsg = "response code: " + response.getHttpStatusCode()
        + ". response status: " +  response.getStatus() + "."
        + "response message: "
        + Optional.ofNullable(response.getStatus()).map(V1Status::getMessage).orElse("none")
        + " when patching " + clusterName + " in namespace "
        + namespace + " with " + patch + " using patch format: " + patchFormat;
    if (!response.isSuccess()) {
      responseMsg = "Failed with " + logmsg;
    } else {
      responseMsg = "Succeeded with " + logmsg;
    }
    getLogger().info(responseMsg);

    return responseMsg;
  }

  /**
   * Patch the Deployment.
   *
   * @param deploymentName name of the deployment
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *     "application/json-patch+json", "application/merge-patch+json",
   * @return true if successful, false otherwise
   */
  public static boolean patchDeployment(String deploymentName, String namespace,
                                        V1Patch patch, String patchFormat) {

    AppsV1Api apiInstance = new AppsV1Api(apiClient);
    try {
      PatchUtils.patch(
          V1Deployment.class,
          () ->
              apiInstance.patchNamespacedDeploymentCall(
                  deploymentName,
                  namespace,
                  patch,
                  null,
                  null,
                  null, // field-manager is optional
                  null,
                  null,
                  null),
          patchFormat,
          apiClient);
    } catch (ApiException apiex) {
      getLogger().warning("Exception while patching the deployment {0} in namespace {1} : {2} ",
          deploymentName, namespace, apiex);
      return false;
    }
    return true;
  }

  /**
   * Converts the response to appropriate type.
   *
   * @param response response object to convert
   * @param type the type to convert into
   * @return the Java object of the type the response object is converted to
   */
  @SuppressWarnings("unchecked")
  private static <T> T handleResponse(Object response, Class<T> type) {
    JsonElement jsonElement = convertToJson(response);
    return apiClient.getJSON().getGson().fromJson(jsonElement, type);
  }

  /**
   * List Domain Custom Resources for a given namespace.
   *
   * @param namespace name of namespace
   * @return List of Domain Custom Resources
   */
  public static DomainList listDomains(String namespace) {
    KubernetesApiResponse<DomainList> response = null;
    try {
      response = crdClient.list(namespace);
    } catch (Exception ex) {
      getLogger().warning(ex.getMessage());
      throw ex;
    }
    return response != null ? response.getObject() : new DomainList();
  }

  // --------------------------- config map ---------------------------
  /**
   * Create a Kubernetes Config Map.
   *
   * @param configMap V1ConfigMap object containing config map configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createConfigMap(V1ConfigMap configMap) throws ApiException {
    if (configMap == null) {
      throw new IllegalArgumentException(
          "Parameter 'configMap' cannot be null when calling createConfigMap()");
    }

    if (configMap.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'configMap' cannot be null when calling createConfigMap()");
    }

    if (configMap.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createConfigMap()");
    }

    String namespace = configMap.getMetadata().getNamespace();

    try {
      coreV1Api.createNamespacedConfigMap(
          namespace, // config map's namespace
          configMap, // config map configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // name associated with the actor or entity that is making these changes
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Replace a Kubernetes Config Map.
   * The following command updates a complete configMap.
   *
   * @param configMap V1ConfigMap object containing config map configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean replaceConfigMap(V1ConfigMap configMap) throws ApiException {
    LoggingFacade logger = getLogger();
    if (configMap == null) {
      throw new IllegalArgumentException(
          "Parameter 'configMap' cannot be null when calling patchConfigMap()");
    }

    if (configMap.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'configMap' cannot be null when calling patchConfigMap()");
    }

    if (configMap.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling patchConfigMap()");
    }

    String namespace = configMap.getMetadata().getNamespace();

    V1ConfigMap cm;
    try {
      cm = coreV1Api.replaceNamespacedConfigMap(
          configMap.getMetadata().getName(),
          namespace,
          configMap, // config map configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // name associated with the actor or entity that is making these changes
          null // field validation
      );
      assertNotNull(cm, "cm replace failed ");
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * List Config Maps in the Kubernetes cluster.
   *
   * @param namespace Namespace in which to query
   * @return V1ConfigMapList of Config Maps
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1ConfigMapList listConfigMaps(String namespace) throws ApiException {

    V1ConfigMapList configMapList;
    try {
      configMapList = coreV1Api.listNamespacedConfigMap(
          namespace, // config map's namespace
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          null, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return configMapList;
  }

  /**
   * Delete Kubernetes Config Map.
   *
   * @param name name of the Config Map
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteConfigMap(String name, String namespace) {

    KubernetesApiResponse<V1ConfigMap> response = configMapClient.delete(namespace, name, deleteOptions);
    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete config map '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
              + "config map in background!");
    }

    return true;
  }

  // --------------------------- secret ---------------------------
  /**
   * Create a Kubernetes Secret.
   *
   * @param secret V1Secret object containing Kubernetes secret configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createSecret(V1Secret secret) throws ApiException {
    if (secret == null) {
      throw new IllegalArgumentException(
          "Parameter 'secret' cannot be null when calling createSecret()");
    }

    if (secret.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'secret' cannot be null when calling createSecret()");
    }

    if (secret.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createSecret()");
    }

    String namespace = secret.getMetadata().getNamespace();

    try {
      coreV1Api.createNamespacedSecret(
          namespace, // name of the Namespace
          secret, // secret configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // fieldManager is a name associated with the actor
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Gets a Kubernetes Secret.
   *
   * @param secretName Name of secret
   * @param namespace Namespace
   * @return secret, if found
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1Secret getSecret(String secretName, String namespace) throws ApiException {
    return coreV1Api.readNamespacedSecret(secretName, namespace, PRETTY);
  }

  /**
   * Delete a Kubernetes Secret.
   *
   * @param name name of the Secret
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteSecret(String name, String namespace) {

    KubernetesApiResponse<V1Secret> response = secretClient.delete(namespace, name);

    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete secret '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
              + "secret in background!");
    }

    return true;
  }

  /**
   * List secrets in the Kubernetes cluster.
   * @param namespace Namespace in which to query
   * @return V1SecretList of secrets in the Kubernetes cluster
   */
  public static V1SecretList listSecrets(String namespace) {
    KubernetesApiResponse<V1SecretList> list = secretClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      getLogger().warning("Failed to list secrets, status code {0}", list.getHttpStatusCode());
      return null;
    }
  }

  /**
   * Read a secret by its object reference.
   *
   * @param reference V1ObjectReference An object reference to the Secret you want to read.
   * @param namespace The Namespace where Secret is defined.
   * @return V1Secret The requested Secret.
   * @throws ApiException if there is an API error.
   */
  public static V1Secret readSecretByReference(V1ObjectReference reference, String namespace)
      throws ApiException {

    if (reference.getNamespace() != null) {
      namespace = reference.getNamespace();
    }

    return coreV1Api.readNamespacedSecret(
        reference.getName(), namespace, "false");
  }

  // --------------------------- pv/pvc ---------------------------
  /**
   * Create a Kubernetes Persistent Volume.
   *
   * @param persistentVolume V1PersistentVolume object containing persistent volume configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createPv(V1PersistentVolume persistentVolume) throws ApiException {
    if (persistentVolume == null) {
      throw new IllegalArgumentException(
          "Parameter 'persistentVolume' cannot be null when calling createPv()");
    }

    try {
      coreV1Api.createPersistentVolume(
          persistentVolume, // persistent volume configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // fieldManager is a name associated with the actor
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Create a Kubernetes Persistent Volume Claim.
   *
   * @param persistentVolumeClaim V1PersistentVolumeClaim object containing Kubernetes persistent volume claim
  configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createPvc(V1PersistentVolumeClaim persistentVolumeClaim) throws ApiException {
    if (persistentVolumeClaim == null) {
      throw new IllegalArgumentException(
          "Parameter 'persistentVolume' cannot be null when calling createPvc()");
    }

    if (persistentVolumeClaim.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'persistentVolumeClaim' cannot be null when calling createPvc()");
    }

    if (persistentVolumeClaim.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createPvc()");
    }

    String namespace = persistentVolumeClaim.getMetadata().getNamespace();

    try {
      coreV1Api.createNamespacedPersistentVolumeClaim(
          namespace, // name of the Namespace
          persistentVolumeClaim, // persistent volume claim configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // fieldManager is a name associated with the actor
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Delete a Kubernetes Persistent Volume.
   *
   * @param name name of the Persistent Volume
   * @return true if successful
   */
  public static boolean deletePv(String name) {

    KubernetesApiResponse<V1PersistentVolume> response = pvClient.delete(name, deleteOptions);

    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete persistent volume '" + name + "' "
          + "with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
              + "persistent volume in background!");
    }

    return true;
  }

  /**
   * Delete a Kubernetes Persistent Volume Claim.
   *
   * @param name name of the Persistent Volume Claim
   * @param namespace name of the namespace
   * @return true if successful
   */
  public static boolean deletePvc(String name, String namespace) {

    KubernetesApiResponse<V1PersistentVolumeClaim> response = pvcClient.delete(namespace, name, deleteOptions);

    if (!response.isSuccess()) {
      getLogger().warning(
          "Failed to delete persistent volume claim '" + name + "' from namespace: "
              + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
              + "persistent volume claim in background!");
    }

    return true;
  }

  /**
   * List all persistent volumes in the Kubernetes cluster.
   * @return V1PersistentVolumeList of Persistent Volumes in Kubernetes cluster
   */
  public static V1PersistentVolumeList listPersistentVolumes() {
    KubernetesApiResponse<V1PersistentVolumeList> list = pvClient.list();
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      getLogger().warning("Failed to list Persistent Volumes,"
          + " status code {0}", list.getHttpStatusCode());
      return null;
    }
  }

  /**
   * List persistent volumes in the Kubernetes cluster based on the label.
   * @param labels String containing the labels the PV is decorated with
   * @return V1PersistentVolumeList list of Persistent Volumes
   * @throws ApiException when listing fails
   */
  public static V1PersistentVolumeList listPersistentVolumes(String labels) throws ApiException {
    V1PersistentVolumeList listPersistentVolume;
    try {
      listPersistentVolume = coreV1Api.listPersistentVolume(
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          labels, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
    return listPersistentVolume;
  }

  /**
   * Get the V1PersistentVolume object in the Kubernetes cluster with specified Persistent Volume name.
   * @param pvname the name of the Persistent Volume
   * @return V1PersistentVolume the Persistent Volume object with specified name in Kubernetes cluster
   */
  public static V1PersistentVolume getPersistentVolume(String pvname) {
    KubernetesApiResponse<V1PersistentVolume> response = pvClient.get(pvname);
    if (response.isSuccess()) {
      return response.getObject();
    } else {
      getLogger().warning("Failed to get Persistent Volume {0},"
          + " status code {1}", pvname, response.getHttpStatusCode());
      return null;
    }
  }

  /**
   * List persistent volume claims in the namespace.
   * @param namespace name of the namespace in which to list
   * @return V1PersistentVolumeClaimList of Persistent Volume Claims in namespace
   */
  public static V1PersistentVolumeClaimList listPersistentVolumeClaims(String namespace) {
    KubernetesApiResponse<V1PersistentVolumeClaimList> list = pvcClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      getLogger().warning("Failed to list Persistent Volumes claims,"
          + " status code {0}", list.getHttpStatusCode());
      return null;
    }
  }

  /**
   * Get V1PersistentVolumeClaim object in the namespace with the specified Persistent Volume Claim name .
   * @param namespace namespace in which to get the Persistent Volume Claim
   * @param pvcname the name of Persistent Volume Claim
   * @return V1PersistentVolumeClaim the Persistent Volume Claims Object in specified namespace
   */
  public static V1PersistentVolumeClaim getPersistentVolumeClaim(String namespace, String pvcname) {
    KubernetesApiResponse<V1PersistentVolumeClaim> response = pvcClient.get(namespace, pvcname);
    if (response.isSuccess()) {
      return response.getObject();
    } else {
      getLogger().warning("Failed to get Persistent Volumes claim {0},"
          + " status code {1}", pvcname, response.getHttpStatusCode());
      return null;
    }
  }

  // --------------------------- service account ---------------------------
  /**
   * Create a Kubernetes Service Account.
   *
   * @param serviceAccount V1ServiceAccount object containing service account configuration data
   * @return created service account
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1ServiceAccount createServiceAccount(V1ServiceAccount serviceAccount)
      throws ApiException {
    if (serviceAccount == null) {
      throw new IllegalArgumentException(
          "Parameter 'serviceAccount' cannot be null when calling createServiceAccount()");
    }

    if (serviceAccount.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'serviceAccount' cannot be null when calling createServiceAccount()");
    }

    if (serviceAccount.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createServiceAccount()");
    }

    String namespace = serviceAccount.getMetadata().getNamespace();

    try {
      serviceAccount = coreV1Api.createNamespacedServiceAccount(
          namespace, // name of the Namespace
          serviceAccount, // service account configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // fieldManager is a name associated with the actor
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return serviceAccount;
  }

  /**
   * Delete a Kubernetes Service Account.
   *
   * @param name name of the Service Account
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteServiceAccount(String name, String namespace) {

    KubernetesApiResponse<V1ServiceAccount> response = serviceAccountClient.delete(namespace, name, deleteOptions);

    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete Service Account '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
              + "service account in background!");
      V1ServiceAccount serviceAccount = response.getObject();
      getLogger().info(
          "Deleting Service Account " + serviceAccount.getMetadata().getName() + " in background.");
    }

    return true;
  }

  /**
   * List all service accounts in the Kubernetes cluster.
   *
   * @param namespace Namespace in which to list all service accounts
   * @return V1ServiceAccountList of service accounts
   */
  public static V1ServiceAccountList listServiceAccounts(String namespace) {
    KubernetesApiResponse<V1ServiceAccountList> list = serviceAccountClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      getLogger().warning("Failed to list service accounts, status code {0}", list.getHttpStatusCode());
      return null;
    }
  }
  // --------------------------- Services ---------------------------

  /**
   * Create a Kubernetes Service.
   *
   * @param service V1Service object containing service configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createService(V1Service service) throws ApiException {
    if (service == null) {
      throw new IllegalArgumentException(
          "Parameter 'service' cannot be null when calling createService()");
    }

    if (service.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'service' cannot be null when calling createService()");
    }

    if (service.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createService()");
    }

    String namespace = service.getMetadata().getNamespace();

    try {
      coreV1Api.createNamespacedService(
          namespace, // name of the Namespace
          service, // service configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // fieldManager is a name associated with the actor
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Delete a Kubernetes Service.
   *
   * @param name name of the Service
   * @param namespace name of the namespace
   * @return true if successful
   */
  public static boolean deleteService(String name, String namespace) {

    KubernetesApiResponse<V1Service> response = serviceClient.delete(namespace, name, deleteOptions);

    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete Service '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
              + "service in background!");
    }

    return true;
  }

  /**
   * Get namespaced service object.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service object to get
   * @return V1Service object if found, otherwise null
   */
  public static V1Service getNamespacedService(String namespace, String serviceName) {
    V1ServiceList listServices = listServices(namespace);
    if (listServices != null && !listServices.getItems().isEmpty()) {
      for (var service : listServices.getItems()) {
        if (service.getMetadata().getName().equalsIgnoreCase(serviceName)) {
          return service;
        }
      }
    }
    return null;
  }

  /**
   * Get node port of a namespaced service given the channel name.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service
   * @param channelName name of the channel for which to get the nodeport
   * @return node port if service and channel is found, otherwise -1
   */
  public static int getServiceNodePort(String namespace, String serviceName, String channelName) {
    LoggingFacade logger = getLogger();
    logger.info("Retrieving Service NodePort for service [{0}] in namespace [{1}] for channel [{2}]",
        serviceName, namespace, channelName);
    V1Service service = getNamespacedService(namespace, serviceName);
    if (service != null) {
      V1ServicePort port = service.getSpec().getPorts().stream().filter(
          v1ServicePort -> v1ServicePort.getName().equalsIgnoreCase(channelName))
          .findAny().orElse(null);
      if (port != null) {
        return port.getNodePort();
      }
    }
    return -1;
  }

  /**
   * Get port of a namespaced service.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service
   * @return node port if service found otherwise -1
   */
  public static Integer getServiceNodePort(String namespace, String serviceName) {
    List<V1Service> services = listServices(namespace).getItems();
    for (V1Service service : services) {
      if (service.getMetadata().getName().startsWith(serviceName)) {
        return service.getSpec().getPorts().get(0).getNodePort();
      }
    }
    return -1;
  }

  /**
   * Get port of a namespaced service given the channel name.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service
   * @param channelName name of the channel for which to get the port
   * @return node port if service and channel is found, otherwise -1
   */
  public static int getServicePort(String namespace, String serviceName, String channelName) {
    V1Service service = getNamespacedService(namespace, serviceName);
    if (service != null) {
      V1ServicePort port = service.getSpec().getPorts().stream().filter(
          v1ServicePort -> v1ServicePort.getName().equalsIgnoreCase(channelName))
          .findAny().orElse(null);
      if (port != null) {
        return port.getPort();
      }
    }
    return -1;
  }

  /**
   * List services in a given namespace.
   *
   * @param namespace name of the namespace
   * @return V1ServiceList list of {@link V1Service} objects
   */
  public static V1ServiceList listServices(String namespace) {

    KubernetesApiResponse<V1ServiceList> list = serviceClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      getLogger().warning("Failed to list services in namespace {0}, status code {1}",
          namespace, list.getHttpStatusCode());
      return null;
    }
  }

  /**
   * Create a job.
   *
   * @param jobBody V1Job object containing job configuration data
   * @return String job name if job creation is successful
   * @throws ApiException when create job fails
   */
  public static String createNamespacedJob(V1Job jobBody) throws ApiException {
    String name = null;
    String namespace = jobBody.getMetadata().getNamespace();
    try {
      BatchV1Api apiInstance = new BatchV1Api(apiClient);
      V1Job createdJob = apiInstance.createNamespacedJob(
          namespace, // String | namespace in which to create job
          jobBody, // V1Job | body of the V1Job containing job data
          PRETTY, // String | pretty print output.
          null, // String | dry run or permanent change
          null, // String | field manager who is making the change
          null // field validation
      );
      if (createdJob != null) {
        name = createdJob.getMetadata().getName();
      }
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }
    return name;
  }

  /**
   * Delete a job.
   *
   * @param namespace name of the namespace
   * @param name name of the job
   * @return true if delete was successful
   */
  public static boolean deleteJob(String namespace, String name) {

    KubernetesApiResponse<V1Job> response = jobClient.delete(namespace, name);

    if (!response.isSuccess()) {
      getLogger().warning("Failed to delete job '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
              + "job in background!");
    }

    return true;
  }

  /**
   * List jobs in the given namespace.
   *
   * @param namespace in which to list the jobs
   * @return V1JobList list of {@link V1Job} from Kubernetes cluster
   * @throws ApiException when list fails
   */
  public static V1JobList listJobs(String namespace) throws ApiException {
    V1JobList list;
    try {
      BatchV1Api apiInstance = new BatchV1Api(apiClient);
      list = apiInstance.listNamespacedJob(
          namespace, // String | name of the namespace.
          PRETTY, // String | pretty print output.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Integer | Timeout for the list/watch call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return list;
  }

  /**
   * Get V1Job object if any exists in the namespace with given job name.
   *
   * @param jobName name of the job
   * @param namespace name of the namespace in which to get the job object
   * @return V1Job object if any exists otherwise null
   * @throws ApiException when Kubernetes cluster query fails
   */
  public static V1Job getJob(String jobName, String namespace) throws ApiException {
    V1JobList listJobs = listJobs(namespace);
    if (listJobs != null) {
      for (V1Job job : listJobs.getItems()) {
        if (job.getMetadata() != null && job.getMetadata().getName() != null
            && job.getMetadata().getName().equals(jobName)) {
          return job;
        }
      }
    }
    return null;
  }

  // --------------------------- replica sets ---------------------------


  /**
   * Delete a replica set.
   *
   * @param namespace name of the namespace
   * @param name name of the replica set
   * @return true if delete was successful
   * @throws ApiException if delete fails
   */
  public static boolean deleteReplicaSet(String namespace, String name) throws ApiException {
    try {
      AppsV1Api apiInstance = new AppsV1Api(apiClient);
      apiInstance.deleteNamespacedReplicaSet(
          name, // String | name of the replica set.
          namespace, // String | name of the namespace.
          PRETTY, // String | pretty print output.
          null, // String | When present, indicates that modifications should not be persisted.
          GRACE_PERIOD, // Integer | The duration in seconds before the object should be deleted.
          null,
          null, // Boolean | Deprecated: use the PropagationPolicy.
          FOREGROUND, // String | Whether and how garbage collection will be performed.
          null // V1DeleteOptions.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return true;
  }

  /**
   * List replica sets in the given namespace.
   *
   * @param namespace in which to list the replica sets
   * @return V1ReplicaSetList list of {@link V1ReplicaSet} objects
   * @throws ApiException when list fails
   */
  public static V1ReplicaSetList listReplicaSets(String namespace) throws ApiException {
    try {
      AppsV1Api apiInstance = new AppsV1Api(apiClient);
      return apiInstance.listNamespacedReplicaSet(
          namespace, // String | namespace.
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
  }

  // --------------------------- Role-based access control (RBAC)   ---------------------------

  /**
   * Create a cluster role.
   * @param clusterRole V1ClusterRole object containing cluster role configuration data
   * @return true if creation is successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createClusterRole(V1ClusterRole clusterRole) throws ApiException {
    try {
      rbacAuthApi.createClusterRole(
          clusterRole, // cluster role configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // fieldManager is a name associated with the actor
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Create a Cluster Role Binding.
   *
   * @param clusterRoleBinding V1ClusterRoleBinding object containing role binding configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createClusterRoleBinding(V1ClusterRoleBinding clusterRoleBinding)
      throws ApiException {
    try {
      rbacAuthApi.createClusterRoleBinding(
          clusterRoleBinding, // role binding configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // fieldManager is a name associated with the actor
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Create a role in the specified namespace.
   *
   * @param namespace the namespace in which the role binding to be created
   * @param role V1Role object containing role configuration data
   * @return true if the creation succeeds, false otherwise
   * @throws ApiException if Kubernetes client call fails
   */
  public static boolean createNamespacedRole(String namespace, V1Role role) throws ApiException {
    try {
      rbacAuthApi.createNamespacedRole(
          namespace, // namespace where this role is created
          role, // role configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // fieldManager is a name associated with the actor
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Create a role binding in the specified namespace.
   *
   * @param namespace the namespace in which the role binding to be created
   * @param roleBinding V1RoleBinding object containing role binding configuration data
   * @return true if the creation succeeds, false otherwise
   * @throws ApiException if Kubernetes client call fails
   */
  public static boolean createNamespacedRoleBinding(String namespace, V1RoleBinding roleBinding) throws ApiException {
    try {
      rbacAuthApi.createNamespacedRoleBinding(
          namespace, // namespace where this role binding is created
          roleBinding, // role binding configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null, // fieldManager is a name associated with the actor
          null // field validation
      );
    } catch (ApiException apex) {
      getLogger().severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Delete Cluster Role Binding.
   *
   * @param name name of cluster role binding
   * @return true if successful, false otherwise
   */
  public static boolean deleteClusterRoleBinding(String name) {
    KubernetesApiResponse<V1ClusterRoleBinding> response = roleBindingClient.delete(name, deleteOptions);

    if (!response.isSuccess()) {
      getLogger().warning(
          "Failed to delete Cluster Role Binding '" + name + " with HTTP status code: " + response
              .getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      getLogger().info(
          "Received after-deletion status of the requested object, will be deleting "
              + "Cluster Role Binding " + name + " in background!");
    }

    return true;
  }

  /**
   * List role bindings in all namespaces.
   *
   * @param labelSelector labels to narrow the list
   * @return V1RoleBindingList list of {@link V1RoleBinding} objects
   * @throws ApiException when listing fails
   */
  public static V1RoleBindingList listRoleBindingForAllNamespaces(String labelSelector) throws ApiException {
    V1RoleBindingList roleBindings;
    try {
      roleBindings = rbacAuthApi.listRoleBindingForAllNamespaces(
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          labelSelector, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          PRETTY, // String | If true, then the output is pretty printed.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Integer | Timeout for the list/watch call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return roleBindings;
  }

  /**
   * List cluster role bindings.
   *
   * @param labelSelector labels to narrow the list
   * @return V1ClusterRoleBindingList list of {@link V1ClusterRoleBinding} objects
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1ClusterRoleBindingList listClusterRoleBindings(String labelSelector) throws ApiException {
    V1ClusterRoleBindingList clusterRoleBindingList;
    try {
      clusterRoleBindingList = rbacAuthApi.listClusterRoleBinding(
          PRETTY, // String | If true, then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          labelSelector, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Integer | Timeout for the list/watch call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return clusterRoleBindingList;
  }

  /**
   * Delete a rolebinding in the given namespace.
   *
   * @param namespace name of the namespace
   * @param name name of the rolebinding to delete
   * @return return true if deletion was successful
   * @throws ApiException when delete rolebinding fails
   */
  public static boolean deleteNamespacedRoleBinding(String namespace, String name)
      throws ApiException {
    try {
      rbacAuthApi.deleteNamespacedRoleBinding(
          name, // String | name of the job.
          namespace, // String | name of the namespace.
          PRETTY, // String | pretty print output.
          null, // String | When present, indicates that modifications should not be persisted.
          GRACE_PERIOD, // Integer | The duration in seconds before the object should be deleted.
          null,
          null, // Boolean | Deprecated: use the PropagationPolicy.
          FOREGROUND, // String | Whether and how garbage collection will be performed.
          null // V1DeleteOptions.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return true;
  }

  /**
   * List role bindings in a given namespace.
   *
   * @param namespace name of the namespace
   * @return V1RoleBindingList list of {@link V1RoleBinding} objects
   * @throws ApiException when listing fails
   */
  public static V1RoleBindingList listNamespacedRoleBinding(String namespace)
      throws ApiException {
    V1RoleBindingList roleBindings;
    try {
      roleBindings = rbacAuthApi.listNamespacedRoleBinding(
          namespace, // String | namespace.
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return roleBindings;
  }

  /**
   * List validating webhook configurations.
   *
   * @return V1ValidatingWebhookConfigurationList list of {@link V1ValidatingWebhookConfiguration} objects
   * @throws ApiException when listing fails
   */
  public static V1ValidatingWebhookConfigurationList listValidatingWebhookConfiguration()
      throws ApiException {
    V1ValidatingWebhookConfigurationList validatingWebhookConfigurations;
    try {

      validatingWebhookConfigurations = admissionregistrationApi.listValidatingWebhookConfiguration(
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return validatingWebhookConfigurations;
  }


  /**
   * Delete a cluster role.
   *
   * @param name name of the cluster role to delete
   * @return true if deletion was successful
   * @throws ApiException when delete cluster role fails
   */
  public static boolean deleteClusterRole(String name) throws ApiException {
    try {
      rbacAuthApi.deleteClusterRole(
          name, // String | name of the role.
          PRETTY, // String | pretty print output.
          null, // String | When present, indicates that modifications should not be persisted.
          GRACE_PERIOD, // Integer | The duration in seconds before the object should be deleted.
          null,
          null, // Boolean | Deprecated: use the PropagationPolicy.
          FOREGROUND, // String | Whether and how garbage collection will be performed.
          null // V1DeleteOptions.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return true;
  }


  /**
   * List cluster roles in the Kubernetes cluster.
   *
   * @param labelSelector labels to narrow the list
   * @return V1ClusterRoleList list of {@link V1ClusterRole} objects
   * @throws ApiException when listing fails
   */
  public static V1ClusterRoleList listClusterRoles(String labelSelector) throws ApiException {
    V1ClusterRoleList roles;
    try {
      roles = rbacAuthApi.listClusterRole(
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          labelSelector, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return roles;
  }

  /**
   * Delete a role in the Kubernetes cluster in the given namespace.
   *
   * @param namespace name of the namespace
   * @param name name of the role to delete
   * @return true if deletion was successful
   * @throws ApiException when delete fails
   */
  public static boolean deleteNamespacedRole(String namespace, String name) throws ApiException {
    try {
      rbacAuthApi.deleteNamespacedRole(
          name, // String | name of the job.
          namespace, // String | name of the namespace.
          PRETTY, // String | pretty print output.
          null, // String | When present, indicates that modifications should not be persisted.
          GRACE_PERIOD, // Integer | The duration in seconds before the object should be deleted.
          null,
          null, // Boolean | Deprecated: use the PropagationPolicy.
          FOREGROUND, // String | Whether and how garbage collection will be performed.
          null // V1DeleteOptions.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return true;
  }

  /**
   * List roles in a given namespace.
   *
   * @param namespace name of the namespace
   * @return V1RoleList list of {@link V1Role} object
   * @throws ApiException when listing fails
   */
  public static V1RoleList listNamespacedRoles(String namespace) throws ApiException {
    V1RoleList roles;
    try {
      roles = rbacAuthApi.listNamespacedRole(
          namespace, // String | namespace.
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Integer | Timeout for the list call.
          Boolean.FALSE // Boolean | Watch for changes to the described resources.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return roles;
  }

  /**
   * Create a StorageClass object.
   *
   * @param sco V1StorageClass object
   * @return true if the creation succeeds, false otherwise
   */
  public static boolean createStorageClass(V1StorageClass sco) {
    KubernetesApiResponse<V1StorageClass> response = storageClassClient.create(sco);
    if (response.isSuccess()) {
      getLogger().info("Successfully created StorageClass {0}", sco.getMetadata().getName());
      return true;
    } else {
      if (response.getStatus() != null) {
        getLogger().info(Yaml.dump(response.getStatus()));
      }
      getLogger().warning("Failed to create StorageClass {0} with error code {1}",
          sco.getMetadata().getName(), response.getHttpStatusCode());
      return response.getHttpStatusCode() == 409;
    }
  }

  /**
   * Delete a StorageClass object.
   *
   * @param name V1StorageClass object name
   * @return true if the deletion succeeds, false otherwise
   */
  public static boolean deleteStorageClass(String name) {
    KubernetesApiResponse<V1StorageClass> response = storageClassClient.delete(name);
    if (response.isSuccess()) {
      getLogger().info("Successfully deleted StorageClass {0}", name);
      return true;
    } else {
      if (response.getStatus() != null) {
        getLogger().info(Yaml.dump(response.getStatus()));
      }
      getLogger().warning("Failed to delete StorageClass {0} with error code {1}",
          name, response.getHttpStatusCode());
      return false;
    }
  }

  /**
   * List Ingresses in the given namespace.
   *
   * @param namespace name of the namespace
   * @return V1IngressList list of {@link V1Ingress} objects
   * @throws ApiException when listing fails
   */
  public static V1IngressList listNamespacedIngresses(String namespace) throws ApiException {
    V1IngressList ingressList;
    try {
      NetworkingV1Api apiInstance = new NetworkingV1Api(apiClient);
      ingressList = apiInstance.listNamespacedIngress(
          namespace, // namespace
          PRETTY, // String | If 'true', then the output is pretty printed.
          ALLOW_WATCH_BOOKMARKS, // Boolean | allowWatchBookmarks requests watch events with type "BOOKMARK".
          null, // String | The continue option should be set when retrieving more results from the server.
          null, // String | A selector to restrict the list of returned objects by their fields.
          null, // String | A selector to restrict the list of returned objects by their labels.
          null, // Integer | limit is a maximum number of responses to return for a list call.
          RESOURCE_VERSION, // String | Shows changes that occur after that particular version of a resource.
          RESOURCE_VERSION_MATCH_UNSET, // String | how to match resource version, leave unset
          SEND_INITIAL_EVENTS_UNSET, // Boolean | if to send initial events
          TIMEOUT_SECONDS, // Integer | Timeout for the list/watch call.
          ALLOW_WATCH_BOOKMARKS // Boolean | Watch for changes to the described resources.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return ingressList;
  }
  
  /**
   * Update Ingress in the given namespace.
   *
   * @param namespace namespace name
   * @param ingress V1Ingress body
   * @throws ApiException when update fails
   */
  public static void updateNamespacedIngresses(String namespace, V1Ingress ingress) throws ApiException {
    try {
      NetworkingV1Api apiInstance = new NetworkingV1Api(apiClient);
      apiInstance.replaceNamespacedIngress(
          ingress.getMetadata().getName(), // ingress name
          namespace, //namespace
          ingress, //ingress body
          PRETTY, //pretty print output
          null, // dryRun
          null, // field manager
          null // filed validation
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
  }

  /**
   * Delete an ingress in the specified namespace.
   *
   * @param name  ingress name to be deleted
   * @param namespace namespace in which the specified ingress exists
   * @return true if deleting ingress succeed, false otherwise
   * @throws ApiException if Kubernetes API client call fails
   */
  public static boolean deleteIngress(String name, String namespace) throws ApiException {
    try {
      NetworkingV1Api apiInstance = new NetworkingV1Api(apiClient);
      apiInstance.deleteNamespacedIngress(
          name, // ingress name
          namespace, // namespace
          PRETTY, // String | If 'true', then the output is pretty printed.
          null, // String | dry run or permanent change
          GRACE_PERIOD, // Integer | The duration in seconds before the object should be deleted.
          null,
          null, // Boolean | Deprecated: use the PropagationPolicy.
          BACKGROUND, // String | Whether and how garbage collection will be performed.
          null // V1DeleteOptions.
      );
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Get Ingress in the given namespace by name.
   *
   * @param namespace name of the namespace
   * @param name name of the Ingress object
   * @return V1Ingress Ingress object when found, otherwise null
   * @throws ApiException when get fails
   */
  public static V1Ingress getNamespacedIngress(String namespace, String name)
      throws ApiException {
    try {
      for (V1Ingress item
          : listNamespacedIngresses(namespace).getItems()) {
        if (name.equals(item.getMetadata().getName())) {
          return item;
        }
      }
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }
    return null;
  }

  // --------------------------- Exec   ---------------------------

  /**
   * Execute a command in a container.
   *
   * @param pod The pod where the command is to be run
   * @param containerName The container in the Pod where the command is to be run. If no
   *     container name is provided than the first container in the Pod is used.
   * @param redirectToStdout copy process output to stdout
   * @param command The command to run
   * @return result of command execution
   * @throws IOException if an I/O error occurs.
   * @throws ApiException if Kubernetes client API call fails
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  public static ExecResult exec(V1Pod pod, String containerName, boolean redirectToStdout,
                                String... command)
      throws IOException, ApiException, InterruptedException {

    // Execute command using Kubernetes API
    KubernetesExec kubernetesExec = createKubernetesExec(pod, containerName);
    final Process proc = kubernetesExec.exec(command);

    // If redirect enabled, copy stdout and stderr to corresponding Outputstream
    final CopyingOutputStream copyOut =
        redirectToStdout ? new CopyingOutputStream(System.out) : new CopyingOutputStream(null);
    final CopyingOutputStream copyErr =
        redirectToStdout ? new CopyingOutputStream(System.err) : new CopyingOutputStream(null);

    // Start a thread to begin reading the output stream of the command
    try {
      Thread out = createStreamReader(proc.getInputStream(), copyOut,
          "Exception reading from stdout input stream.");
      out.start();

      // Start a thread to begin reading the error stream of the command
      Thread err = createStreamReader(proc.getErrorStream(), copyErr,
          "Exception reading from stderr input stream.");
      err.start();

      // wait for the process, which represents the executing command, to terminate
      proc.waitFor();

      // wait for stdout reading thread to finish any remaining output
      out.join();

      // wait for stderr reading thread to finish any remaining output
      err.join();

      // Read data from process's stdout
      String stdout = readExecCmdData(copyOut.getInputStream());

      // Read from process's stderr, if data available
      String stderr = readExecCmdData(copyErr.getInputStream());

      ExecResult result = new ExecResult(proc.exitValue(), stdout, stderr);
      getLogger().fine("result from exec command: " + result);

      if (result.exitValue() != 0) {
        getLogger().info("result.exitValue={0}", result.exitValue());
        getLogger().info("result.stdout={0}", result.stdout());
        getLogger().info("result.stderr={0}", result.stderr());
      }

      return result;
    } finally {
      if (proc != null) {
        proc.destroy();
      }
    }
  }

  private static Thread createStreamReader(InputStream inputStream, CopyingOutputStream copyOut,
                                           String s) {
    return
        new Thread(
            () -> {
              try {
                Streams.copy(inputStream, copyOut);
              } catch (IOException ex) {
                // "Pipe broken" is expected when process is finished so don't log
                if (ex.getMessage() != null && !ex.getMessage().contains("Pipe broken")) {
                  getLogger().warning(s, ex);
                }
              }
            });
  }

  /**
   * Create an object which can execute commands in a Kubernetes container.
   *
   * @param pod The pod where the command is to be run
   * @param containerName The container in the Pod where the command is to be run. If no
   *     container name is provided than the first container in the Pod is used.
   * @return object for executing a command in a container of the pod
   */
  public static KubernetesExec createKubernetesExec(V1Pod pod, String containerName) {
    return new KubernetesExec()
        .apiClient(apiClient) // the Kubernetes api client to dispatch the "exec" command
        .pod(pod) // The pod where the command is to be run
        .containerName(containerName) // the container in which the command is to be run
        .passStdinAsStream(); // pass a stdin stream into the container
  }

  /**
   * Create an Ingress in the specified namespace.
   *
   * @param namespace the namespace in which the ingress will be created
   * @param ingressBody V1Ingress object, representing the ingress details
   * @return the ingress created
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1Ingress createIngress(String namespace, V1Ingress ingressBody)
      throws ApiException {
    V1Ingress ingress;
    try {
      getLogger().info("Creating ingress: {0}", Yaml.dump(ingressBody));
      NetworkingV1Api apiInstance = new NetworkingV1Api(apiClient);
      ingress = apiInstance.createNamespacedIngress(
          namespace, //namespace
          ingressBody, // V1Ingress object, representing the ingress details
          PRETTY, // pretty print output
          null, // when present, indicates that modifications should not be persisted
          null, // a name associated with the actor or entity that is making these changes
          null // field validation
      );
      getLogger().info("Created ingress: {0}", Yaml.dump(ingress));
    } catch (ApiException apex) {
      getLogger().warning(apex.getResponseBody());
      throw apex;
    }

    return ingress;
  }

  /**
   * Get a list of custom resource definitions deployed in the Kubernetes cluster.
   *
   * @return String list of crds as String of V1CustomResourceDefinitionList
   * @throws ApiException when list fails.
   */
  public static String listCrds() throws ApiException {
    Object crds;
    try {
      crds = customObjectsApi
          .listClusterCustomObject("apiextensions.k8s.io",
              "v1",
              "customresourcedefinitions",
              null,
              false,
              null,
              null,
              null,
              0,
              null,
              null,
              0,
              false);
    } catch (Exception ex) {
      getLogger().severe(ex.getMessage());
      throw ex;
    }
    return crds.toString();
  }
  
  //------------------------

  private static String readExecCmdData(InputStream is) {
    StringBuilder sb = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(is, StandardCharsets.UTF_8))) {
      int c = 0;
      while ((c = reader.read()) != -1) {
        sb.append((char) c);
      }
    } catch (IOException e) {
      getLogger().warning("Exception thrown " + e);
    }
    return sb.toString().trim();
  }

  /**
   * Get the name of the operator pod.
   *
   * @param release release name of the operator
   * @param namespace Kubernetes namespace that the operator is running in
   * @return name of the operator pod
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getOperatorPodName(String release, String namespace) throws ApiException {
    String labelSelector = String.format("app in (%s)", release);
    V1PodList pods = listPods(namespace, labelSelector);
    for (var pod : pods.getItems()) {
      if (pod.getMetadata().getName().contains(release)) {
        return pod.getMetadata().getName();
      }
    }
    return null;
  }

  /**
   * Simple class to redirect/copy data to both the stdout stream and a buffer
   * which can be read from later.
   */
  private static class CopyingOutputStream extends OutputStream {

    final OutputStream out;
    final ByteArrayOutputStream copy = new ByteArrayOutputStream();

    CopyingOutputStream(OutputStream out) {
      this.out = out;
    }

    @Override
    public void write(int b) throws IOException {
      if (out != null) {
        out.write(b);
      }
      copy.write(b);
    }

    public InputStream getInputStream() {
      return new ByteArrayInputStream(copy.toByteArray());
    }
  }
}
