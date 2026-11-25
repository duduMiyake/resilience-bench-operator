package io.resiliencebench.execution.steps;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.service.ResilientService;
import io.resiliencebench.support.CustomResourceRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

import static io.resiliencebench.support.Annotations.CONTAINER;

@Service
public class EnvironmentPostStep extends AbstractEnvironmentStep {

  public EnvironmentPostStep(KubernetesClient kubernetesClient,
      CustomResourceRepository<ResilientService> resilientServiceRepository) {
    super(kubernetesClient, resilientServiceRepository);
  }

  @Override
  protected boolean isApplicable(Scenario scenario) {
    return !connectorEnvironmentServices(scenario).isEmpty();
  }

  @Override
  protected void internalExecute(Scenario scenario, ExecutionQueue queue) {
    var namespace = scenario.getMetadata().getNamespace();
    RuntimeException failure = null;
    for (var name : connectorEnvironmentServices(scenario)) {
      try {
        var service = resilientServiceRepository.get(namespace, name);
        if (!snapshotOwner(scenario).equals(service.getMetadata().getAnnotations().get(ENV_SNAPSHOT))) {
          continue;
        }
        var deployment = requireEnvironmentDeployment(scenario, service);
        var container = service.getMetadata().getAnnotations().get(CONTAINER);
        updateVariablesDeployment(deployment, container, service.getSpec().getEnvs());
        waitForEnvironmentRollout(deployment);

        var annotations = new LinkedHashMap<>(service.getMetadata().getAnnotations());
        annotations.remove(ENV_SNAPSHOT);
        service.getMetadata().setAnnotations(annotations);
        service.getSpec().setEnvs(null);
        resilientServiceRepository.update(service);
      } catch (RuntimeException error) {
        if (failure == null) {
          failure = error;
        } else {
          failure.addSuppressed(error);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
