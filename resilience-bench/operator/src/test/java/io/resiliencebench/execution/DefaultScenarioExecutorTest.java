package io.resiliencebench.execution;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.resiliencebench.execution.steps.StepRegistry;
import io.resiliencebench.execution.steps.k6.K6JobFactory;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.workload.Workload;
import io.resiliencebench.support.CustomResourceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.resiliencebench.support.Annotations.OWNED_BY;
import static io.resiliencebench.support.Annotations.SCENARIO;
import static org.mockito.Mockito.*;

class DefaultScenarioExecutorTest {

  @Test
  void handlesJobCompletionOnlyOnce() {
    var stepRegistry = mock(StepRegistry.class);
    when(stepRegistry.getPostExecutionSteps()).thenReturn(List.of());

    @SuppressWarnings("unchecked")
    var scenarioRepository = (CustomResourceRepository<Scenario>) mock(CustomResourceRepository.class);
    @SuppressWarnings("unchecked")
    var executionRepository = (CustomResourceRepository<ExecutionQueue>) mock(CustomResourceRepository.class);
    @SuppressWarnings("unchecked")
    var workloadRepository = (CustomResourceRepository<Workload>) mock(CustomResourceRepository.class);

    var scenario = mock(Scenario.class, RETURNS_DEEP_STUBS);
    when(scenario.getMetadata().getAnnotations()).thenReturn(Map.of(OWNED_BY, "benchmark"));
    when(scenarioRepository.get("namespace", "scenario")).thenReturn(scenario);
    when(executionRepository.get("namespace", "benchmark")).thenReturn(mock(ExecutionQueue.class));

    var executor = new DefaultScenarioExecutor(
            mock(KubernetesClient.class),
            stepRegistry,
            mock(K6JobFactory.class),
            scenarioRepository,
            executionRepository,
            workloadRepository);
    var onCompletion = mock(Runnable.class);
    var watcher = executor.createJobCompletionWatcher(onCompletion);

    var job = new Job();
    var metadata = new ObjectMeta();
    metadata.setName("job");
    metadata.setNamespace("namespace");
    metadata.setAnnotations(Map.of(SCENARIO, "scenario"));
    job.setMetadata(metadata);
    var status = new JobStatus();
    status.setCompletionTime("2026-06-15T15:27:26Z");
    job.setStatus(status);

    watcher.eventReceived(Watcher.Action.MODIFIED, job);
    watcher.eventReceived(Watcher.Action.MODIFIED, job);

    verify(onCompletion, times(1)).run();
    verify(scenarioRepository, times(1)).get("namespace", "scenario");
  }
}
