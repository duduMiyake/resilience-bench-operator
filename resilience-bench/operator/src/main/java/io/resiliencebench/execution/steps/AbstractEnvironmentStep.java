package io.resiliencebench.execution.steps;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.service.ResilientService;
import io.resiliencebench.support.CustomResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.concurrent.TimeUnit;

abstract class AbstractEnvironmentStep extends ExecutorStep {

  private final static Logger logger = LoggerFactory.getLogger(AbstractEnvironmentStep.class);
  protected final CustomResourceRepository<ResilientService> resilientServiceRepository;

  public AbstractEnvironmentStep(KubernetesClient kubernetesClient,
      CustomResourceRepository<ResilientService> resilientServiceRepository) {
    super(kubernetesClient);
    this.resilientServiceRepository = resilientServiceRepository;
  }

  protected Deployment getDeployment(Scenario scenario, ResilientService resilientService) {
    return kubernetesClient()
        .apps()
        .deployments()
        .inNamespace(scenario.getMetadata().getNamespace())
        .withLabelSelector(resilientService.getSpec().getSelector())
        .list()
        .getItems()
        .stream()
        .findFirst()
        .orElse(null);
  }

  protected static final String ENV_SNAPSHOT = "resiliencebench.io/environment-snapshot";

  protected static String snapshotOwner(Scenario scenario) {
    var metadata = scenario.getMetadata();
    return metadata.getUid() != null ? metadata.getUid() : metadata.getName();
  }

  protected static void setSnapshotOwner(ResilientService service, Scenario scenario) {
    var annotations = new LinkedHashMap<>(service.getMetadata().getAnnotations());
    annotations.put(ENV_SNAPSHOT, snapshotOwner(scenario));
    service.getMetadata().setAnnotations(annotations);
  }

  private static Stream<io.resiliencebench.resources.scenario.Service> configuredEndpoints(Scenario scenario) {
    return scenario.getSpec().getConnectors().stream()
        .flatMap(connector -> Stream.of(connector.getSource(), connector.getDestination()))
        .filter(service -> service.getEnvs() != null && !service.getEnvs().isEmpty());
  }

  protected static Set<String> connectorEnvironmentServices(Scenario scenario) {
    var services = new LinkedHashSet<String>();
    configuredEndpoints(scenario).forEach(service -> services.add(service.getName()));
    return services;
  }

  protected static Map<String, Map<String, String>> connectorEnvironments(Scenario scenario) {
    var environments = new LinkedHashMap<String, Map<String, String>>();
    configuredEndpoints(scenario).forEach(service -> {
      var values = environments.computeIfAbsent(service.getName(), ignored -> new LinkedHashMap<>());
      service.getEnvs().forEach((key, value) -> mergeEnvironmentValue(values, key, value.asText(), service.getName()));
    });
    return environments;
  }

  protected static void mergeEnvironmentValue(Map<String, String> values, String key, String value, String target) {
    var prior = values.putIfAbsent(key, value);
    if (prior != null && !prior.equals(value)) {
      throw new IllegalArgumentException("Conflicting environment variable " + key + " on " + target);
    }
  }

  protected Deployment requireEnvironmentDeployment(Scenario scenario, ResilientService service) {
    var deployments = kubernetesClient().apps().deployments()
        .inNamespace(scenario.getMetadata().getNamespace())
        .withLabelSelector(service.getSpec().getSelector()).list().getItems();
    if (deployments.size() != 1) {
      throw new IllegalArgumentException("Expected exactly one Deployment for " + service.getMetadata().getName());
    }
    return deployments.get(0);
  }

  private void internalUpdateVariablesDeployment(Deployment targetDeployment, String containerName, List<EnvVar> envVars) {
    var deployment = kubernetesClient()
            .apps()
            .deployments()
            .inNamespace(targetDeployment.getMetadata().getNamespace())
            .withName(targetDeployment.getMetadata().getName())
            .get();

    var container = deployment
            .getSpec()
            .getTemplate()
            .getSpec()
            .getContainers()
            .stream()
            .filter(c -> c.getName().equals(containerName))
            .findFirst();

    var actualContainer = container.orElseThrow(() -> new IllegalStateException("Container not found: " + containerName));
    actualContainer.setEnv(envVars);
    kubernetesClient().apps().deployments().inNamespace(targetDeployment.getMetadata().getNamespace()).resource(deployment).update();
  }

  protected void updateVariablesDeployment(Deployment targetDeployment, String containerName, List<EnvVar> envVars) {
    Retry.of("updateVariablesDeployment", RetryConfig.custom().maxAttempts(3).build())
            .executeRunnable(() -> internalUpdateVariablesDeployment(targetDeployment, containerName, envVars));
  }

  protected void waitForEnvironmentRollout(Deployment target) {
    var metadata = target.getMetadata();
    var resource = kubernetesClient().apps().deployments()
        .inNamespace(metadata.getNamespace()).withName(metadata.getName());
    var updated = resource.get();
    if (updated == null) {
      throw new IllegalStateException("Deployment not found: " + metadata.getName());
    }
    var generation = updated.getMetadata().getGeneration();
    int replicas = updated.getSpec().getReplicas() == null ? 1 : updated.getSpec().getReplicas();
    resource.waitUntilCondition(deployment -> deployment != null && deployment.getStatus() != null
        && generation != null && deployment.getStatus().getObservedGeneration() != null
        && deployment.getStatus().getObservedGeneration() >= generation
        && count(deployment.getStatus().getReplicas()) == replicas
        && count(deployment.getStatus().getUpdatedReplicas()) == replicas
        && count(deployment.getStatus().getAvailableReplicas()) == replicas, 2, TimeUnit.MINUTES);
  }

  private static int count(Integer replicas) {
    return replicas != null ? replicas : 0;
  }

  protected void waitUntilReady(Deployment targetDeployment) {
    logger.info("Restarting deployment: {}", targetDeployment.getMetadata().getName());
    getPods(targetDeployment).waitUntilCondition(this::waitUntilCondition, 2, TimeUnit.MINUTES);
    logger.info("Deployment restarted: {}", targetDeployment.getMetadata().getName());
  }

  protected List<EnvVar> getActualContainerEnv(Deployment targetDeployment, String containerName) {
    return targetDeployment.getSpec().getTemplate().getSpec().getContainers().stream()
        .filter(c -> c.getName().equals(containerName))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Container not found: " + containerName))
        .getEnv();
  }

  public FilterWatchListDeletable<Pod, PodList, PodResource> getPods(Deployment targetDeployment) {
    return kubernetesClient().pods()
        .inNamespace(targetDeployment.getMetadata().getNamespace())
        .withLabel("app", targetDeployment.getMetadata().getName());
  }

   /**
   * Test if the pod is ready, if yes return true, otherwise return false
   */
  public boolean waitUntilCondition(Pod pod) {
    var isMarkedForDeletion = pod.getMetadata().getDeletionTimestamp() != null;
    if (isMarkedForDeletion) {
      logger.info("Pod marked for deletion {}", pod.getMetadata().getName());
      return false;
    }
    var isReady = pod.getStatus()
            .getConditions()
            .stream()
            .anyMatch(condition -> "Ready".equals(condition.getType()) && "True".equals(condition.getStatus()));
    if (isReady) {
      logger.info("Pod ready {}", pod.getMetadata().getName());
    }

    return isReady;
  }
}