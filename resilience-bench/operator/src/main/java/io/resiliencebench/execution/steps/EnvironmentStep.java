package io.resiliencebench.execution.steps;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.service.ResilientService;
import io.resiliencebench.support.CustomResourceRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.resiliencebench.support.Annotations.CONTAINER;

@Service
public class EnvironmentStep extends AbstractEnvironmentStep {

  public EnvironmentStep(KubernetesClient kubernetesClient,
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
    var changes = new LinkedHashMap<Target, Change>();
    connectorEnvironments(scenario).forEach((name, values) -> {
      var service = resilientServiceRepository.get(namespace, name);
      if (service.getMetadata().getAnnotations().containsKey(ENV_SNAPSHOT)) {
        throw new IllegalStateException("Unrestored environment snapshot for " + name);
      }
      var container = service.getMetadata().getAnnotations().get(CONTAINER);
      var deployment = requireEnvironmentDeployment(scenario, service);
      var target = new Target(deployment.getMetadata().getName(), container);
      var change = changes.computeIfAbsent(target, ignored -> new Change(deployment, service, new LinkedHashMap<>()));
      values.forEach((key, value) -> mergeEnvironmentValue(change.values(), key, value, target.toString()));
    });

    // Resolve and validate every change before touching any Deployment.
    var prepared = new LinkedHashMap<Target, Prepared>();
    changes.forEach((target, change) -> {
      var original = getActualContainerEnv(change.deployment(), target.container()).stream()
          .map(variable -> new EnvVarBuilder(variable).build()).toList();
      var seen = new HashSet<String>();
      var updated = original.stream().map(variable -> {
        if (!change.values().containsKey(variable.getName())) {
          return variable;
        }
        if (!seen.add(variable.getName())) {
          throw new IllegalArgumentException("Duplicate environment variable " + variable.getName() + " on " + target);
        }
        return new EnvVarBuilder(variable).withValue(change.values().get(variable.getName())).withValueFrom(null).build();
      }).toList();
      for (var key : change.values().keySet()) {
        if (!seen.contains(key)) {
          throw new IllegalArgumentException("Environment variable " + key + " is not declared on " + target);
        }
      }
      if (!original.equals(updated)) {
        prepared.put(target, new Prepared(change, original, updated));
      }
    });

    prepared.forEach((target, plan) -> {
      var service = plan.change().owner();
      service.getSpec().setEnvs(plan.original());
      setSnapshotOwner(service, scenario);
      resilientServiceRepository.update(service);
      updateVariablesDeployment(plan.change().deployment(), target.container(), plan.updated());
    });
    prepared.values().stream().map(plan -> plan.change().deployment()).distinct()
        .forEach(this::waitForEnvironmentRollout);
  }

  private record Target(String deployment, String container) {}
  private record Change(Deployment deployment, ResilientService owner, Map<String, String> values) {}
  private record Prepared(Change change, List<EnvVar> original, List<EnvVar> updated) {}
}
