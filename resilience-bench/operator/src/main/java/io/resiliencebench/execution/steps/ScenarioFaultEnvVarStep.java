package io.resiliencebench.execution.steps;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.service.ResilientService;
import io.resiliencebench.support.CustomResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static io.resiliencebench.support.Annotations.CONTAINER;

@Service
public class ScenarioFaultEnvVarStep extends EnvironmentStep {
  private final static Logger logger = LoggerFactory.getLogger(ScenarioFaultEnvVarStep.class);

  public ScenarioFaultEnvVarStep(KubernetesClient kubernetesClient,
                           CustomResourceRepository<ResilientService> resilientServiceRepository) {
    super(kubernetesClient, resilientServiceRepository);
  }

  @Override
  protected boolean isApplicable(Scenario scenario) {
    var fault = scenario.getSpec().getFault();
    return fault != null && !fault.hasWindows();
  }

  // Does not matter what was set in fault.provider. we'll apply it as env var of envoy container.
  @Override
  protected void internalExecute(Scenario scenario, ExecutionQueue queue) {
    var fault = scenario.getSpec().getFault();

    for (var service : fault.getServices()) {
      var resilientService = resilientServiceRepository.get(scenario.getMetadata().getNamespace(), service);
      applyServiceFault(scenario, resilientService);
    }
  }

  public void applyServiceFault(Scenario scenario, ResilientService resilientService) {
    var deployment = kubernetesClient()
            .apps()
            .deployments()
            .inNamespace(scenario.getMetadata().getNamespace())
            .withLabelSelector(resilientService.getSpec().getSelector())
            .list()
            .getItems()
            .stream()
            .findFirst();

    if (deployment.isPresent()) {
      var targetDeployment = deployment.get();
      var containerName = resilientService.getMetadata().getAnnotations().get(CONTAINER);
      var containerEnvs = getActualContainerEnv(targetDeployment, containerName);
      containerEnvs.removeIf(env -> env != null && "FAULT_PERCENTAGE".equals(env.getName()));
      containerEnvs.add(
              new EnvVar("FAULT_PERCENTAGE", String.valueOf(scenario.getSpec().getFault().getPercentage()), null)
      );
      updateVariablesDeployment(targetDeployment, containerName, containerEnvs);
    } else {
      logger.warn("Deployment not found for ResilientService {}", resilientService.getMetadata().getName());
    }
  }
}
