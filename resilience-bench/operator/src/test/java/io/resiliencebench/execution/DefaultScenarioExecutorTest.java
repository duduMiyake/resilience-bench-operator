package io.resiliencebench.execution;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobList;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import io.resiliencebench.execution.fault.TemporalFaultService;
import io.resiliencebench.execution.fault.TemporalFaultSession;
import io.resiliencebench.execution.steps.EnvironmentPostStep;
import io.resiliencebench.execution.steps.ResultFileStep;
import io.resiliencebench.execution.steps.StepRegistry;
import io.resiliencebench.execution.steps.UpdateStatusQueueStep;
import io.resiliencebench.execution.steps.k6.K6JobFactory;
import io.resiliencebench.execution.steps.k6.TemporalK6Run;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.queue.ExecutionQueueItem;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.workload.Workload;
import io.resiliencebench.support.CustomResourceRepository;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.resiliencebench.support.Annotations.OWNED_BY;
import static io.resiliencebench.support.Annotations.SCENARIO;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
            workloadRepository,
            mock(TemporalFaultService.class),
            mock(TemporalK6Run.class));
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

  @Test
  void verifiedTemporalRunStoresResultsAndAdvancesQueueAfterCleanup() throws Exception {
    var fixture = new TemporalFixture();
    var metrics = new JsonObject().put("faultWindowsCompleted", 1);
    when(fixture.session.finish()).thenReturn(metrics);
    fixture.driveRun();

    fixture.execute();

    var sequence = inOrder(fixture.session, fixture.environment, fixture.results, fixture.update, fixture.onCompletion);
    sequence.verify(fixture.session).start();
    sequence.verify(fixture.session).finish();
    sequence.verify(fixture.environment).execute(fixture.scenario, fixture.queue);
    sequence.verify(fixture.results).storeResult(fixture.scenario, fixture.queue, metrics);
    sequence.verify(fixture.update).execute(fixture.scenario, fixture.queue);
    sequence.verify(fixture.onCompletion).run();
  }

  @Test
  void incompleteTemporalWindowStopsQueueWithoutStoringResults() throws Exception {
    var fixture = new TemporalFixture();
    when(fixture.session.finish()).thenThrow(new IllegalStateException("incomplete trial"));
    fixture.driveRun();

    fixture.execute();

    verify(fixture.session).cancel();
    verify(fixture.environment).execute(fixture.scenario, fixture.queue);
    verifyNoInteractions(fixture.results, fixture.update, fixture.onCompletion);
    assertEquals(ExecutionQueueItem.Status.FAILED, fixture.item.getStatus());
    assertEquals("java.lang.IllegalStateException: incomplete trial", fixture.item.getFailureReason());
    verify(fixture.queueRepository).update(fixture.queue);
    verify(fixture.queueRepository).updateStatus(fixture.queue);
  }

  @Test
  void failedJobRestoresEnvironmentAndDoesNotAdvanceQueue() {
    var fixture = new TemporalFixture();
    var scenarios = fixture.scenarioRepository;
    when(scenarios.get("benchmarking", "temporal")).thenReturn(fixture.scenario);
    when(fixture.scenario.getMetadata().getAnnotations()).thenReturn(Map.of(OWNED_BY, "queue"));
    fixture.job.getMetadata().setNamespace("benchmarking");
    fixture.job.getMetadata().setAnnotations(Map.of(SCENARIO, "temporal"));
    var status = new JobStatus();
    status.setConditions(List.of(new io.fabric8.kubernetes.api.model.batch.v1.JobConditionBuilder()
            .withType("Failed").withStatus("True").build()));
    fixture.job.setStatus(status);

    var watcher = fixture.executor().createJobCompletionWatcher(fixture.onCompletion);
    watcher.eventReceived(Watcher.Action.MODIFIED, fixture.job);
    watcher.eventReceived(Watcher.Action.MODIFIED, fixture.job);

    verify(fixture.environment, times(1)).execute(fixture.scenario, fixture.queue);
    verifyNoInteractions(fixture.results, fixture.update, fixture.onCompletion);
    assertEquals(ExecutionQueueItem.Status.FAILED, fixture.item.getStatus());
  }

  @SuppressWarnings("unchecked")
  private static class TemporalFixture {
    final KubernetesClient kube = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    final StepRegistry registry = mock(StepRegistry.class);
    final K6JobFactory factory = mock(K6JobFactory.class);
    final CustomResourceRepository<ExecutionQueue> queueRepository = mock(CustomResourceRepository.class);
    final CustomResourceRepository<Scenario> scenarioRepository = mock(CustomResourceRepository.class);
    final Workload workload = mock(Workload.class);
    final Scenario scenario = mock(Scenario.class, RETURNS_DEEP_STUBS);
    final ExecutionQueue queue = mock(ExecutionQueue.class, RETURNS_DEEP_STUBS);
    final ExecutionQueueItem item = new ExecutionQueueItem("temporal", "results.json");
    final TemporalFaultService service = mock(TemporalFaultService.class);
    final TemporalFaultSession session = mock(TemporalFaultSession.class);
    final TemporalK6Run k6 = mock(TemporalK6Run.class);
    final EnvironmentPostStep environment = mock(EnvironmentPostStep.class);
    final ResultFileStep results = mock(ResultFileStep.class);
    final UpdateStatusQueueStep update = mock(UpdateStatusQueueStep.class);
    final Runnable onCompletion = mock(Runnable.class);
    final Job job = new Job();

    TemporalFixture() {
      when(scenario.getMetadata().getNamespace()).thenReturn("benchmarking");
      when(scenario.getMetadata().getName()).thenReturn("temporal");
      when(queue.getMetadata().getName()).thenReturn("queue");
      when(queue.getItem("temporal")).thenReturn(item);
      when(queue.getSpec().getItems()).thenReturn(List.of(item));
      when(queueRepository.get("benchmarking", "queue")).thenReturn(queue);
      when(registry.getPreparationSteps()).thenReturn(List.of());
      when(registry.getPostExecutionSteps()).thenReturn(List.of(update, results, environment));
      when(service.prepare(scenario)).thenReturn(session);
      var meta = new ObjectMeta();
      meta.setName("test-job");
      meta.setNamespace("benchmarking");
      meta.setUid("test-job-uid");
      job.setMetadata(meta);
      when(factory.create(scenario, workload, item)).thenReturn(job);
      MixedOperation<Job, JobList, ScalableResource<Job>> jobs = mock(MixedOperation.class);
      when(kube.batch().v1().jobs()).thenReturn(jobs);
      ScalableResource<Job> jobResource = mock(ScalableResource.class);
      when(jobs.resource(job)).thenReturn(jobResource);
      when(jobResource.create()).thenReturn(job);
      when(jobs.inNamespace("benchmarking")).thenReturn(jobs);
      when(jobs.withName("test-job")).thenReturn(jobResource);
      when(jobResource.get()).thenReturn(job);
    }

    void driveRun() throws Exception {
      doAnswer(call -> {
        call.getArgument(1, Runnable.class).run();
        call.getArgument(2, Runnable.class).run();
        return null;
      }).when(k6).awaitPausedAndRun(eq(job), any(Runnable.class), any(Runnable.class));
    }

    DefaultScenarioExecutor executor() {
      return new DefaultScenarioExecutor(kube, registry, factory,
              scenarioRepository, queueRepository, mock(CustomResourceRepository.class), service, k6);
    }

    void execute() {
      executor().executeTemporal(scenario, queue, workload, onCompletion);
    }
  }

}
