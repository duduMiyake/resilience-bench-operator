package io.resiliencebench.execution.steps.k6;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.resiliencebench.resources.queue.ExecutionQueueItem;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.scenario.ScenarioWorkload;
import io.resiliencebench.resources.workload.Workload;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.resiliencebench.support.Annotations.*;

@Service
public class K6JobFactory {

  public K6JobFactory() {
  }

  public Job create(Scenario scenario, Workload workload, ExecutionQueueItem executionQueueItem) {
    var meta = createMeta(scenario, workload);
    return new JobBuilder()
            .withMetadata(meta)
            .withNewSpec()
            .withNewTemplate()
            .withNewMetadata()
            .addToAnnotations("sidecar.istio.io/inject", "false")
            .endMetadata()
            .withNewSpec()
            .withRestartPolicy("Never")
            .withNodeSelector(resolveNodeSelector())
            .withContainers(createK6Container(scenario.getSpec().getWorkload(), workload, executionQueueItem))
            .withVolumes(createResultsVolume(), createScriptVolume(workload))
            .endSpec()
            .endTemplate()
            .withBackoffLimit(4)
            .endSpec()
            .build();
  }

  public ObjectMeta createMeta(Scenario scenario, Workload workload) {
    return new ObjectMetaBuilder()
            .withName(workload.getMetadata().getName() + "-" + scenario.getMetadata().getName())
            .withNamespace(workload.getMetadata().getNamespace())
            .withLabels(Map.of("app", "k6"))
            .addToAnnotations(CREATED_BY, "resiliencebench-operator")
            .addToAnnotations(SCENARIO, scenario.getMetadata().getName())
            .addToAnnotations(WORKLOAD, workload.getMetadata().getName())
            .build();
  }

  private List<EnvVar> resolveEnvVars(Workload workload, ScenarioWorkload scenarioWorkload, ExecutionQueueItem executionQueueItem) {
    List<EnvVar> envs = new ArrayList<>();

    envs.add(new EnvVar("OUTPUT_PATH", executionQueueItem.getResultFile(), null));
    envs.add(new EnvVar("VIRTUAL_USERS", String.valueOf(scenarioWorkload.getUsers()), null));
    for (var item : workload.getSpec().getOptions()) {
      envs.add(new EnvVar(item.getName(), item.getValue().asText(), null));
    }
    return envs;
  }

  private Map<String, String> resolveNodeSelector() {
    var rawNodeSelector = System.getenv("K6_NODE_SELECTOR");
    if (rawNodeSelector == null || rawNodeSelector.isBlank()) {
      return null;
    }

    Map<String, String> nodeSelector = new HashMap<>();
    for (var item : rawNodeSelector.split(",")) {
      var pair = item.trim().split("=", 2);
      if (pair.length == 2 && !pair[0].isBlank() && !pair[1].isBlank()) {
        nodeSelector.put(pair[0].trim(), pair[1].trim());
      }
    }
    return nodeSelector.isEmpty() ? null : nodeSelector;
  }

  public Container createK6Container(ScenarioWorkload scenarioWorkload, Workload workload, ExecutionQueueItem executionQueueItem) {
    var container = new ContainerBuilder()
            .withName("k6")
            .withImage(workload.getSpec().getK6ContainerImage())
            .withCommand("k6", "run", "/scripts/k6.js")
            .withImagePullPolicy("IfNotPresent")
            .withPorts(new ContainerPortBuilder().withContainerPort(6565).build())
            .withNewSecurityContext()
            .withRunAsUser(0L)
            .endSecurityContext()
            .withVolumeMounts(
                    new VolumeMount("/scripts", "None", "script-volume", false, null, null),
                    new VolumeMount("/results", "HostToContainer", "test-results", false, null, null)
            )
            .withEnv(resolveEnvVars(workload, scenarioWorkload, executionQueueItem));

    return container.build();
  }

  public Volume createResultsVolume() {
    return new VolumeBuilder()
            .withName("test-results") // TODO receive it from the workload
            .withNewPersistentVolumeClaim("test-results", false)
            .build();
  }

  public Volume createScriptVolume(Workload workload) {
    return new VolumeBuilder()
            .withName("script-volume") // TODO receive it from the workload
            .withNewConfigMap()
            .withName(workload.getSpec().getScript().getConfigMap().getName())
            .endConfigMap()
            .build();
  }
}
