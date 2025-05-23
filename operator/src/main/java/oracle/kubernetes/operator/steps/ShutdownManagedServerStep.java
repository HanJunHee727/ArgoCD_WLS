// Copyright (c) 2018, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.ShutdownType;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.SecretHelper;
import oracle.kubernetes.operator.http.client.HttpRequestStep;
import oracle.kubernetes.operator.http.client.HttpResponseStep;
import oracle.kubernetes.operator.http.rest.Scan;
import oracle.kubernetes.operator.http.rest.ScanCache;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.operator.wlsconfig.PortDetails;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.Shutdown;

import static oracle.kubernetes.operator.KubernetesConstants.HTTP_BAD_REQUEST;
import static oracle.kubernetes.operator.KubernetesConstants.WLS_CONTAINER_NAME;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.WebLogicConstants.ADMIN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;

public class ShutdownManagedServerStep extends Step {
  private static final Long HTTP_SHUTDOWN_SECONDS = 5L;

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private final String serverName;
  private final V1Pod pod;

  private ShutdownManagedServerStep(Step next, String serverName, V1Pod pod) {
    super(next);
    this.serverName = serverName;
    this.pod = pod;
  }

  /**
   * Creates asynchronous {@link Step}.
   *
   * @param next Next processing step
   * @param serverName name of server
   * @param pod server pod
   * @return asynchronous step
   */
  public static Step createShutdownManagedServerStep(Step next, String serverName, V1Pod pod) {
    return new ShutdownManagedServerStep(next, serverName, pod);
  }

  @Override
  public @Nonnull Result apply(Packet packet) {
    LOGGER.fine(MessageKeys.BEGIN_SERVER_SHUTDOWN_REST, serverName);
    V1Service service = getDomainPresenceInfo(packet).getServerService(serverName);

    String now = OffsetDateTime.now().toString();
    if (service == null || !PodHelper.isReady(pod) || PodHelper.isFailed(pod) || PodHelper.isWaitingToRoll(pod)) {
      return doNext(PodHelper.annotatePodAsNeedingToShutdown(pod, now, getNext()), packet);
    }
    return doNext(
          Step.chain(
              SecretHelper.createAuthorizationSourceStep(),
              PodHelper.annotatePodAsNeedingToShutdown(pod, now,
                  new ShutdownManagedServerWithHttpStep(service, pod, getNext()))),
          packet);
  }

  static final class ShutdownManagedServerProcessing extends HttpRequestProcessing {
    private Boolean isGracefulShutdown;
    private Long timeout;
    private Boolean ignoreSessions;
    private Boolean waitForAllSessions;

    ShutdownManagedServerProcessing(Packet packet, @Nonnull V1Service service, V1Pod pod) {
      super(packet, service, pod);
      initializeRequestPayloadParameters();
    }

    private static String getManagedServerShutdownPath(Boolean isGracefulShutdown) {
      String shutdownString = Boolean.TRUE.equals(isGracefulShutdown) ? "shutdown" : "forceShutdown";
      return "/management/weblogic/latest/serverRuntime/" + shutdownString;
    }

    private static String getManagedServerShutdownPayload(Boolean isGracefulShutdown,
                                                          Boolean ignoreSessions, Long timeout,
                                                          Boolean waitForAllSessions) {
      if (Boolean.FALSE.equals(isGracefulShutdown)) {
        return "{}";
      }

      return "{  \"ignoreSessions\": "
          + ignoreSessions
          + ", \"timeout\": "
          + timeout
          + ", \"waitForAllSessions\": "
          + waitForAllSessions
          + "}";
    }

    private Long getTimeout() {
      return timeout;
    }

    private HttpRequest createRequest() {
      String body = getManagedServerShutdownPayload(isGracefulShutdown, ignoreSessions, timeout, waitForAllSessions);
      String url = getRequestUrl(isGracefulShutdown);

      return createRequestBuilder(url, HTTP_SHUTDOWN_SECONDS).POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }

    private void initializeRequestPayloadParameters() {
      String serverName = getServerName();
      String clusterName = getClusterNameFromServiceLabel();
      List<V1EnvVar> envVarList = getV1EnvVars();

      Shutdown shutdown = Optional.ofNullable(getDomainPresenceInfo(getPacket()).getServer(serverName, clusterName))
          .map(EffectiveServerSpec::getShutdown).orElse(null);

      isGracefulShutdown = isGracefulShutdown(envVarList, shutdown);
      timeout = calculateTimeout(envVarList, shutdown);
      ignoreSessions = getIgnoreSessions(envVarList, shutdown);
      waitForAllSessions = getWaitForAllSessions(envVarList, shutdown);
    }

    private List<V1EnvVar> getV1EnvVars() {
      return Optional.ofNullable(getPod().getSpec())
              .map(this::getEnvVars).orElse(Collections.emptyList());
    }

    private List<V1EnvVar> getEnvVars(V1PodSpec v1PodSpec) {
      return getContainer(v1PodSpec).map(V1Container::getEnv).orElse(Collections.emptyList());
    }

    Optional<V1Container> getContainer(V1PodSpec v1PodSpec) {
      return v1PodSpec.getContainers().stream().filter(this::isK8sContainer).findFirst();
    }

    boolean isK8sContainer(V1Container c) {
      return WLS_CONTAINER_NAME.equals(c.getName());
    }

    private String getEnvValue(List<V1EnvVar> vars, String name) {
      for (V1EnvVar envVar : vars) {
        if (envVar.getName().equals(name)) {
          return envVar.getValue();
        }
      }
      return null;
    }

    private Boolean isGracefulShutdown(List<V1EnvVar> envVarList, Shutdown shutdown) {
      String shutdownType = getEnvValue(envVarList, "SHUTDOWN_TYPE");

      shutdownType = shutdownType == null ? Optional.ofNullable(shutdown).map(Shutdown::getShutdownType)
          .orElse(ShutdownType.GRACEFUL).toString() : shutdownType;


      return shutdownType.equalsIgnoreCase(ShutdownType.GRACEFUL.toString());
    }

    private Boolean getWaitForAllSessions(List<V1EnvVar> envVarList, Shutdown shutdown) {
      String shutdownWaitForAllSessions = getEnvValue(envVarList, "SHUTDOWN_WAIT_FOR_ALL_SESSIONS");

      return shutdownWaitForAllSessions == null ? Optional.ofNullable(shutdown).map(Shutdown::getWaitForAllSessions)
              .orElse(Shutdown.DEFAULT_WAIT_FOR_ALL_SESSIONS) : Boolean.valueOf(shutdownWaitForAllSessions);
    }

    private Boolean getIgnoreSessions(List<V1EnvVar> envVarList, Shutdown shutdown) {
      String shutdownIgnoreSessions = getEnvValue(envVarList, "SHUTDOWN_IGNORE_SESSIONS");

      return shutdownIgnoreSessions == null ? Optional.ofNullable(shutdown).map(Shutdown::getIgnoreSessions)
              .orElse(Shutdown.DEFAULT_IGNORESESSIONS) : Boolean.valueOf(shutdownIgnoreSessions);
    }

    private Long calculateTimeout(List<V1EnvVar> envVarList, Shutdown shutdown) {
      String shutdownTimeout = getEnvValue(envVarList, "SHUTDOWN_TIMEOUT");

      return shutdownTimeout == null ? Optional.ofNullable(shutdown).map(Shutdown::getTimeoutSeconds)
              .orElse(Shutdown.DEFAULT_TIMEOUT) : Long.valueOf(shutdownTimeout);
    }

    Long getRequestTimeoutSeconds() {
      // Add a 10 second fudge factor here to account for any delay in
      // connecting and issuing the shutdown request.
      return timeout + PodHelper.DEFAULT_ADDITIONAL_DELETE_TIME;
    }

    private String getRequestUrl(Boolean isGracefulShutdown) {
      return getServiceUrl() + getManagedServerShutdownPath(isGracefulShutdown);
    }

    protected PortDetails getPortDetails() {
      Integer port = getWlsServerPort();
      WlsServerConfig serverConfig = getWlsServerConfig();
      boolean isSecure = port != null && serverConfig != null
          && !port.equals(serverConfig.getListenPort());
      return new PortDetails(port, isSecure);
    }

    private Integer getWlsServerPort() {
      Integer listenPort = Optional.ofNullable(getWlsServerConfig()).map(WlsServerConfig::getListenPort)
          .orElse(null);
      Integer adminPort = Optional.ofNullable(getWlsServerConfig()).map(WlsServerConfig::getAdminPort)
              .orElse(null);
      Integer sslListenPort = Optional.ofNullable(getWlsServerConfig()).map(WlsServerConfig::getSslListenPort)
              .orElse(null);
      if (adminPort != null) {
        return adminPort;
      }
      if (sslListenPort != null) {
        return sslListenPort;
      }
      if (listenPort == null) {
        // This can only happen if the running server pod does not exist in the WLS Domain.
        // This is a rare case where the server was deleted from the WLS Domain config.
        listenPort = getListenPortFromPod(this.getPod());
      }

      return listenPort;
    }

    private Integer getListenPortFromPod(V1Pod pod) {
      return getContainer(pod.getSpec()).map(V1Container::getPorts).orElse(Collections.emptyList()).stream()
          .filter(this::isTCPProtocol).findFirst().map(V1ContainerPort::getContainerPort).orElse(0);
    }

    boolean isTCPProtocol(V1ContainerPort port) {
      return "TCP".equals(port.getProtocol());
    }

    private WlsServerConfig getWlsServerConfig() {
      // standalone server that does not belong to any cluster
      WlsServerConfig serverConfig = getWlsDomainConfig().getServerConfig(getServerName());

      if (serverConfig == null) {
        // dynamic or configured server in a cluster
        String clusterName = getClusterNameFromServiceLabel();
        WlsClusterConfig cluster = getWlsDomainConfig().getClusterConfig(clusterName);
        serverConfig = findServerConfig(cluster);
      }
      return serverConfig;
    }

    private String getClusterNameFromServiceLabel() {
      return Optional.of(getService())
          .map(V1Service::getMetadata)
          .map(V1ObjectMeta::getLabels)
          .map(m -> m.get(CLUSTERNAME_LABEL))
          .orElse(null);
    }

    private WlsServerConfig findServerConfig(WlsClusterConfig wlsClusterConfig) {
      for (WlsServerConfig serverConfig : wlsClusterConfig.getServerConfigs()) {
        if (Objects.equals(getServerName(), serverConfig.getName())) {
          return serverConfig;
        }
      }
      return null;
    }

    private String getServerName() {
      return this.getPod().getMetadata().getLabels().get(LabelConstants.SERVERNAME_LABEL);
    }

    private WlsDomainConfig getWlsDomainConfig() {
      DomainPresenceInfo info = getDomainPresenceInfo(getPacket());
      WlsDomainConfig domainConfig =
          (WlsDomainConfig) getPacket().get(ProcessingConstants.DOMAIN_TOPOLOGY);
      if (domainConfig == null) {
        Scan scan = ScanCache.INSTANCE.lookupScan(info.getNamespace(), info.getDomainUid());
        domainConfig = scan.getWlsDomainConfig();
      }
      return domainConfig;
    }

    HttpRequestStep createRequestStep(
        ShutdownManagedServerResponseStep shutdownManagedServerResponseStep) {
      HttpRequestStep requestStep = HttpRequestStep.create(createRequest(),
          shutdownManagedServerResponseStep).withTimeoutSeconds(getRequestTimeoutSeconds());
      shutdownManagedServerResponseStep.requestStep = requestStep;
      return requestStep;
    }
  }

  static final class ShutdownManagedServerWithHttpStep extends Step {
    @Nonnull
    private final V1Service service;
    private final V1Pod pod;

    ShutdownManagedServerWithHttpStep(@Nonnull V1Service service, V1Pod pod, Step next) {
      super(next);
      this.service = service;
      this.pod = pod;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      ShutdownManagedServerProcessing processing = new ShutdownManagedServerProcessing(packet, service, pod);
      ShutdownManagedServerResponseStep shutdownManagedServerResponseStep =
          new ShutdownManagedServerResponseStep(pod, processing.getTimeout(),
                  PodHelper.getPodServerName(pod), getNext());
      HttpRequestStep requestStep = processing.createRequestStep(shutdownManagedServerResponseStep);
      return doNext(requestStep, packet);
    }

  }

  private static DomainPresenceInfo getDomainPresenceInfo(Packet packet) {
    return (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
  }

  static final class ShutdownManagedServerResponseStep extends HttpResponseStep {
    private static final String SHUTDOWN_REQUEST_RETRY_COUNT = "shutdownRequestRetryCount";
    private final V1Pod pod;
    private final Long timeout;
    private final String serverName;
    private HttpRequestStep requestStep;

    ShutdownManagedServerResponseStep(V1Pod pod, Long timeout, String serverName, Step next) {
      super(next);
      this.pod = pod;
      this.timeout = timeout;
      this.serverName = serverName;
    }

    @Override
    public Result onSuccess(Packet packet, HttpResponse<String> response) {
      LOGGER.fine(MessageKeys.SERVER_SHUTDOWN_REST_SUCCESS, serverName);
      removeShutdownRequestRetryCount(packet);
      return doNext(packet);
    }

    @Override
    public Result onFailure(Packet packet, HttpResponse<String> response) {

      // Check shutdown annotation and proceed if timeout already satisfied
      String shutdownTime = PodHelper.getPodShutdownAnnotation(pod);
      if (shutdownTime != null) {
        OffsetDateTime shutdownStarted = OffsetDateTime.parse(shutdownTime);
        if (OffsetDateTime.now().isAfter(shutdownStarted.plus(timeout, ChronoUnit.SECONDS))) {
          removeShutdownRequestRetryCount(packet);
          return doNext(packet);
        }
      }

      Throwable throwable = getThrowableResponse(packet);
      if (throwable != null) {
        if (throwable instanceof HttpTimeoutException) {
          return doRequeue();
        }

        if (shouldRetry(packet)) {
          addShutdownRequestRetryCountToPacket(packet, 1);
          // Retry request
          LOGGER.info(MessageKeys.SERVER_SHUTDOWN_REST_RETRY, serverName);
          return doNext(requestStep, packet);
        }
        if (!isServerStateShutdown(packet)) {
          LOGGER.info(MessageKeys.SERVER_SHUTDOWN_REST_THROWABLE, serverName, throwable.getMessage());
        }
      } else if (response != null && response.statusCode() == HTTP_BAD_REQUEST) {
        // This occurs when the server start is already SUSPENDING
        return doRequeue();
      } else {
        LOGGER.info(MessageKeys.SERVER_SHUTDOWN_REST_FAILURE, serverName, response);
      }
      removeShutdownRequestRetryCount(packet);
      return doNext(Step.chain(createDomainRefreshStep(getDomainPresenceInfo(packet).getDomainName(),
          getDomainPresenceInfo(packet).getNamespace()), getNext()), packet);
    }

    private Step createDomainRefreshStep(String domainName, String namespace) {
      return RequestBuilder.DOMAIN.get(namespace, domainName, new DomainUpdateStep());
    }

    private boolean shouldRetry(Packet packet) {
      return isServerStateRunningOrAdmin(packet) && getShutdownRequestRetryCount(packet) == null && requestStep != null;
    }

    private boolean isServerStateRunningOrAdmin(Packet packet) {
      String state = PodHelper.getServerState(getDomainPresenceInfo(packet).getDomain(), serverName);
      return RUNNING_STATE.equals(state) || ADMIN_STATE.equals(state);
    }

    private boolean isServerStateShutdown(Packet packet) {
      String state = PodHelper.getServerState(getDomainPresenceInfo(packet).getDomain(), serverName);
      return SHUTDOWN_STATE.equals(state);
    }

    private static Integer getShutdownRequestRetryCount(Packet packet) {
      return (Integer) packet.get(SHUTDOWN_REQUEST_RETRY_COUNT);
    }

    private static void addShutdownRequestRetryCountToPacket(Packet packet, Integer count) {
      packet.put(SHUTDOWN_REQUEST_RETRY_COUNT, count);
    }

    private static void removeShutdownRequestRetryCount(Packet packet) {
      packet.remove(SHUTDOWN_REQUEST_RETRY_COUNT);
    }

    void setHttpAsyncRequestStep(HttpRequestStep requestStep) {
      this.requestStep = requestStep;
    }
  }

  static class DomainUpdateStep extends DefaultResponseStep<DomainResource> {
    @Override
    public Result onSuccess(Packet packet, KubernetesApiResponse<DomainResource> callResponse) {
      if (callResponse.getObject() != null) {
        DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
        info.setDomain(callResponse.getObject());
      }
      return doNext(packet);
    }
  }
}
