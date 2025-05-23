// Copyright (c) 2018, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostAlias;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1Probe;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1TopologySpreadConstraint;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.ServerStartPolicy;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static oracle.kubernetes.operator.helpers.AffinityHelper.getDefaultAntiAffinity;
import static oracle.kubernetes.operator.helpers.PodSecurityHelper.getDefaultContainerSecurityContext;
import static oracle.kubernetes.operator.helpers.PodSecurityHelper.getDefaultPodSecurityContext;

/**
 * Configuration values shared by multiple levels: domain, admin server, managed server, and
 * cluster.
 *
 * @since 2.0
 */
public abstract class BaseConfiguration {

  @Description("Customization affecting the generation of Pods for WebLogic Server instances.")
  private final ServerPod serverPod = new ServerPod();

  @Description(
      "Customization affecting the generation of ClusterIP Services for WebLogic Server instances.")
  @SerializedName("serverService")
  @Expose
  private final ServerService serverService = new ServerService();

  /**
   * Tells the operator whether the customer wants to restart the server pods. The value can be any
   * String, and it can be defined on domain, cluster or server to restart the different pods. After
   * the value is added, the corresponding pods will be terminated and created again. If customer
   * modifies the value again after the pods were recreated, then the pods will again be terminated
   * and recreated.
   *
   * @since 2.0
   */
  @Description(
      "Changes to this field cause the operator to restart WebLogic Server instances. More info: "
      + "https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/"
      + "domain-lifecycle/startup/#restarting-servers.")
  private String restartVersion;

  /**
   * Fills in any undefined settings in this configuration from another configuration.
   *
   * @param other the other configuration which can override this one
   */
  void fillInFrom(BaseConfiguration other) {
    if (other == null) {
      return;
    }

    if (overrideStartPolicyFrom(other)) {
      setServerStartPolicy(other.getServerStartPolicy());
    }

    serverPod.fillInFrom(other.serverPod);
    serverService.fillInFrom(other.serverService);
  }

  private boolean overrideStartPolicyFrom(BaseConfiguration other) {
    if (other.isStartAdminServerOnly()) {
      return false;
    }
    return getServerStartPolicy() == null || other.isStartNever();
  }

  boolean isStartAdminServerOnly() {
    return Objects.equals(getServerStartPolicy(), ServerStartPolicy.ADMIN_ONLY);
  }

  private boolean isStartNever() {
    return Objects.equals(getServerStartPolicy(), ServerStartPolicy.NEVER);
  }

  @Nullable
  public List<V1EnvVar> getEnv() {
    return serverPod.getEnv();
  }

  @Nullable
  public List<V1EnvFromSource> getEnvFrom() {
    return serverPod.getEnvFrom();
  }

  public void setEnv(@Nullable List<V1EnvVar> env) {
    serverPod.setEnv(env);
  }

  void addEnvironmentVariable(String name, String value) {
    serverPod.addEnvVar(new V1EnvVar().name(name).value(value));
  }

  void addEnvironmentVariable(V1EnvVar envVar) {
    serverPod.addEnvVar(envVar);
  }

  public void setEnvFrom(@Nullable List<V1EnvFromSource> envFromSources) {
    serverPod.setEnvFrom(envFromSources);
  }

  public abstract ServerStartPolicy getServerStartPolicy();

  /**
   * Tells the operator whether the customer wants the server to be running. For non-clustered
   * servers - the operator will start it if the policy isn't Never. For clustered servers - the
   * operator will start it if the policy is Always or the policy is IfNeeded and the server needs
   * to be started to get to the cluster's replica count...
   *
   * @since 2.0
   * @param serverStartPolicy start policy
   */
  public abstract void setServerStartPolicy(ServerStartPolicy serverStartPolicy);

  void setLivenessProbe(Integer initialDelay, Integer timeout, Integer period) {
    serverPod.setLivenessProbe(initialDelay, timeout, period);
  }

  public void setLivenessProbeThresholds(Integer successThreshold, Integer failureThreshold) {
    serverPod.setLivenessProbeThresholds(successThreshold, failureThreshold);
  }

  V1Probe getLivenessProbe() {
    return serverPod.getLivenessProbe();
  }

  void setReadinessProbe(Integer initialDelay, Integer timeout, Integer period) {
    serverPod.setReadinessProbeTuning(initialDelay, timeout, period);
  }

  void setReadinessProbeThresholds(Integer successThreshold, Integer failureThreshold) {
    serverPod.setReadinessProbeThresholds(successThreshold, failureThreshold);
  }

  void setReadinessProbeHttpGetActionPath(String httpGetActionPath) {
    serverPod.setReadinessProbeHttpGetActionPath(httpGetActionPath);
  }

  V1Probe getReadinessProbe() {
    return serverPod.getReadinessProbe();
  }

  V1Probe getStartupProbe() {
    return serverPod.getStartupProbe();
  }

  Shutdown getShutdown() {
    return serverPod.getShutdown();
  }

  void addNodeSelector(String labelKey, String labelValue) {
    serverPod.addNodeSelector(labelKey, labelValue);
  }

  Map<String, String> getNodeSelector() {
    return serverPod.getNodeSelector();
  }

  public V1Affinity getAffinity() {
    return Optional.ofNullable(serverPod.getAffinity()).orElse(getDefaultAntiAffinity());
  }

  void setAffinity(V1Affinity affinity) {
    serverPod.setAffinity(affinity);
  }

  public List<V1TopologySpreadConstraint> getTopologySpreadConstraints() {
    return serverPod.getTopologySpreadConstrains();
  }

  void setTopologySpreadConstraints(List<V1TopologySpreadConstraint> topologySpreadConstraints) {
    serverPod.setTopologySpreadConstraints(topologySpreadConstraints);
  }

  public String getPriorityClassName() {
    return serverPod.getPriorityClassName();
  }

  void setPriorityClassName(String priorityClassName) {
    serverPod.setPriorityClassName(priorityClassName);
  }

  public List<V1PodReadinessGate> getReadinessGates() {
    return serverPod.getReadinessGates();
  }

  void addReadinessGate(V1PodReadinessGate readinessGate) {
    serverPod.addReadinessGate(readinessGate);
  }

  public String getRestartPolicy() {
    return serverPod.getRestartPolicy();
  }

  void setRestartPolicy(String restartPolicy) {
    serverPod.setRestartPolicy(restartPolicy);
  }

  public String getRuntimeClassName() {
    return serverPod.getRuntimeClassName();
  }

  void setRuntimeClassName(String runtimeClassName) {
    serverPod.setRuntimeClassName(runtimeClassName);
  }

  public String getNodeName() {
    return serverPod.getNodeName();
  }

  public String getServiceAccountName() {
    return serverPod.getServiceAccountName();
  }

  public Boolean getAutomountServiceAccountToken() {
    return serverPod.getAutomountServiceAccountToken();
  }

  public void setNodeName(String nodeName) {
    serverPod.setNodeName(nodeName);
  }

  public String getSchedulerName() {
    return serverPod.getSchedulerName();
  }

  void setSchedulerName(String schedulerName) {
    serverPod.setSchedulerName(schedulerName);
  }

  public List<V1Toleration> getTolerations() {
    return serverPod.getTolerations();
  }

  void addToleration(V1Toleration toleration) {
    serverPod.addToleration(toleration);
  }

  public List<V1HostAlias> getHostAliases() {
    return serverPod.getHostAliases();
  }

  public void setHostAliases(List<V1HostAlias> hostAliases) {
    serverPod.setHostAliases(hostAliases);
  }

  void addHostAlias(V1HostAlias hostAlias) {
    serverPod.addHostAlias(hostAlias);
  }

  public V1ResourceRequirements getResources() {
    return serverPod.getResourceRequirements();
  }

  void addRequestRequirement(String resource, String quantity) {
    serverPod.addRequestRequirement(resource, quantity);
  }

  void addLimitRequirement(String resource, String quantity) {
    serverPod.addLimitRequirement(resource, quantity);
  }

  void setShutdown(Shutdown shutdown) {
    serverPod.setShutdown(shutdown.getShutdownType(),
            shutdown.getTimeoutSeconds(), shutdown.getIgnoreSessions(), shutdown.getWaitForAllSessions(),
            shutdown.getSkipWaitingCohEndangeredState());
  }

  V1PodSecurityContext getPodSecurityContext() {
    return Optional.ofNullable(serverPod.getPodSecurityContext()).orElse(getDefaultPodSecurityContext());
  }

  void setPodSecurityContext(V1PodSecurityContext podSecurityContext) {
    serverPod.setPodSecurityContext(podSecurityContext);
  }

  V1SecurityContext getContainerSecurityContext() {
    return Optional.ofNullable(serverPod.getContainerSecurityContext())
        .orElseGet(() -> {
          if (serverPod.getPodSecurityContext() == null) {
            return getDefaultContainerSecurityContext();
          }
          return null;
        });
  }

  void setContainerSecurityContext(V1SecurityContext containerSecurityContext) {
    serverPod.setContainerSecurityContext(containerSecurityContext);
  }

  public List<V1Volume> getAdditionalVolumes() {
    return serverPod.getAdditionalVolumes();
  }

  void addAdditionalVolume(String name, String path) {
    serverPod.addAdditionalVolume(name, path);
  }

  void addAdditionalVolume(V1Volume volume) {
    serverPod.addAdditionalVolume(volume);
  }

  void addAdditionalPvClaimVolume(String name, String claimName) {
    serverPod.addAdditionalPvClaimVolume(name, claimName);
  }

  public List<V1VolumeMount> getAdditionalVolumeMounts() {
    return serverPod.getAdditionalVolumeMounts();
  }

  void addAdditionalVolumeMount(String name, String path) {
    serverPod.addAdditionalVolumeMount(name, path);
  }

  void addInitContainer(V1Container initContainer) {
    serverPod.addInitContainer(initContainer);
  }

  void addContainer(V1Container container) {
    serverPod.addContainer(container);
  }

  Map<String, String> getPodLabels() {
    return serverPod.getLabels();
  }

  void addPodLabel(String name, String value) {
    serverPod.addLabel(name, value);
  }

  Map<String, String> getPodAnnotations() {
    return serverPod.getAnnotations();
  }

  void addPodAnnotation(String name, String value) {
    serverPod.addAnnotations(name, value);
  }

  public Boolean isPrecreateServerService() {
    return serverService.isPrecreateService();
  }

  public void setPrecreateServerService(boolean value) {
    serverService.setIsPrecreateService(value);
  }

  public Map<String, String> getServiceLabels() {
    return serverService.getLabels();
  }

  void addServiceLabel(String name, String value) {
    serverService.addLabel(name, value);
  }

  public Map<String, String> getServiceAnnotations() {
    return serverService.getAnnotations();
  }

  public List<V1Container> getInitContainers() {
    return serverPod.getInitContainers();
  }

  public List<V1Container> getContainers() {
    return serverPod.getContainers();
  }

  void addServiceAnnotation(String name, String value) {
    serverService.addAnnotations(name, value);
  }

  String getRestartVersion() {
    return restartVersion;
  }

  public void setRestartVersion(String restartVersion) {
    this.restartVersion = restartVersion;
  }

  Long getMaximumReadyWaitTimeSeconds() {
    return serverPod.getMaxReadyWaitTimeSeconds();
  }

  Long getMaximumPendingWaitTimeSeconds() {
    return serverPod.getMaxPendingWaitTimeSeconds();
  }

  public void setMaxReadyWaitTimeSeconds(long waitTime) {
    serverPod.setMaxReadyWaitTimeSeconds(waitTime);
  }

  public void setMaxPendingWaitTimeSeconds(long waitTime) {
    serverPod.setMaxPendingWaitTimeSeconds(waitTime);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverPod", serverPod)
        .append("serverService", serverService)
        .append("restartVersion", restartVersion)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    BaseConfiguration that = (BaseConfiguration) o;

    return new EqualsBuilder()
        .append(serverPod, that.serverPod)
        .append(serverService, that.serverService)
        .append(restartVersion, that.restartVersion)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(serverPod)
        .append(serverService)
        .append(restartVersion)
        .toHashCode();
  }
}
