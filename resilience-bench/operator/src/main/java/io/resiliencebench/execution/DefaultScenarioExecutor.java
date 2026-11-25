package io.resiliencebench.execution;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
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
import io.resiliencebench.resources.queue.ExecutionQueueStatus;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.workload.Workload;
import io.resiliencebench.support.CustomResourceRepository;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.resiliencebench.support.Annotations.*;

@Service
public class DefaultScenarioExecutor implements ScenarioExecutor {

  private final static Logger logger = LoggerFactory.getLogger(DefaultScenarioExecutor.class);
  private final KubernetesClient kubernetesClient;
  private final StepRegistry stepRegistry;
  private final K6JobFactory k6JobFactory;
  private final TemporalFaultService temporalFaultService;
  private final TemporalK6Run temporalK6Run;
  private final Set<String> temporalNamespaces = ConcurrentHashMap.newKeySet();
  private final java.util.concurrent.ExecutorService temporalWorkers = Executors.newCachedThreadPool(task -> {
    var thread = new Thread(task, "resiliencebench-temporal-scenario");
    thread.setDaemon(true);
    return thread;
  });

  private final CustomResourceRepository<Scenario> scenarioRepository;
  private final CustomResourceRepository<ExecutionQueue> executionRepository;
  private final CustomResourceRepository<Workload> workloadRepository;

  public DefaultScenarioExecutor(KubernetesClient kubernetesClient,
                                 StepRegistry stepRegistry,
                                 K6JobFactory k6JobFactory,
                                 CustomResourceRepository<Scenario> scenarioRepository,
                                 CustomResourceRepository<ExecutionQueue> executionRepository,
                                 CustomResourceRepository<Workload> workloadRepository,
                                 TemporalFaultService temporalFaultService,
                                 TemporalK6Run temporalK6Run) {
    this.kubernetesClient = kubernetesClient;
    this.stepRegistry = stepRegistry;
    this.k6JobFactory = k6JobFactory;
    this.scenarioRepository = scenarioRepository;
    this.executionRepository = executionRepository;
    this.workloadRepository = workloadRepository;
    this.temporalFaultService = temporalFaultService;
    this.temporalK6Run = temporalK6Run;
  }

  @Override
  public void execute(Scenario scenario, ExecutionQueue executionQueue, Runnable onCompletion) {
    var ns = scenario.getMetadata().getNamespace();
    var workloadName = scenario.getSpec().getWorkload().getWorkloadName();
    var workload = workloadRepository.find(ns, workloadName)
            .orElseThrow(() -> new IllegalArgumentException("Workload does not exists: %s".formatted(workloadName)));

    var fault = scenario.getSpec().getFault();
    if (fault != null && fault.hasWindows()) {
      // Targets in the same namespace share experimental infrastructure.
      if (!temporalNamespaces.add(ns)) {
        return;
      }
      try {
        temporalWorkers.execute(() -> executeTemporal(scenario, executionQueue, workload, onCompletion));
      } catch (RuntimeException e) {
        temporalNamespaces.remove(ns);
        throw e;
      }
      return;
    }

    try {
      executePreparationSteps(scenario, executionQueue);
      var executionQueueItem = executionQueue.getItem(scenario.getMetadata().getName());
      var job = k6JobFactory.create(scenario, workload, executionQueueItem);
      var jobsClient = kubernetesClient.batch().v1().jobs();
      jobsClient.inNamespace(job.getMetadata().getNamespace()).withName(job.getMetadata().getName()).delete();
      var handle = jobsClient.resource(job);
      handle.create();
      try {
        handle.watch(createJobCompletionWatcher(onCompletion));
      } catch (RuntimeException watchError) {
        try {
          handle.delete();
        } catch (RuntimeException cleanup) {
          watchError.addSuppressed(cleanup);
        }
        throw watchError;
      }
      logger.info("Job created: {}", job.getMetadata().getName());
    } catch (RuntimeException error) {
      handleScenarioFailure(scenario, executionQueue, error);
      throw error;
    }
  }

  void executeTemporal(Scenario scenario, ExecutionQueue queue, Workload workload, Runnable onCompletion) {
    var namespace = scenario.getMetadata().getNamespace();
    var scenarioName = scenario.getMetadata().getName();
    var jobs = kubernetesClient.batch().v1().jobs();
    Job created = null;
    TemporalFaultSession fault = null;
    boolean completed = false;
    try {
      executePreparationSteps(scenario, queue);
      fault = temporalFaultService.prepare(scenario);
      var job = k6JobFactory.create(scenario, workload, queue.getItem(scenarioName));
      jobs.inNamespace(namespace).withName(job.getMetadata().getName()).delete();
      created = jobs.resource(job).create();

      var session = fault;
      var metrics = new AtomicReference<JsonObject>();
      temporalK6Run.awaitPausedAndRun(created, session::start, () -> metrics.set(session.finish()));
      var resultMetrics = metrics.get();
      if (resultMetrics == null) {
        throw new IllegalStateException("k6 completed without a verified temporal fault timeline");
      }

      restoreEnvironment(scenario, queue);
      for (var step : stepRegistry.getPostExecutionSteps()) {
        if (step instanceof ResultFileStep resultFileStep) {
          resultFileStep.storeResult(scenario, queue, resultMetrics);
        }
      }
      for (var step : stepRegistry.getPostExecutionSteps()) {
        if (step instanceof UpdateStatusQueueStep) {
          step.execute(scenario, queue);
        }
      }
      completed = true;
    } catch (Exception error) {
      // Clear interrupt while issuing bounded cleanup requests; restore it before returning.
      var interrupted = Thread.interrupted() || error instanceof InterruptedException;
      try {
        if (fault != null) {
          try {
            fault.cancel();
          } catch (RuntimeException cleanup) {
            error.addSuppressed(cleanup);
          }
        }
        if (created != null) {
          try {
            var resource = jobs.inNamespace(namespace).withName(created.getMetadata().getName());
            var current = resource.get();
            if (current != null && created.getMetadata().getUid().equals(current.getMetadata().getUid())) {
              resource.delete();
            }
          } catch (RuntimeException cleanup) {
            error.addSuppressed(cleanup);
          }
        }
        handleScenarioFailure(scenario, queue, error);
        logger.error("Temporal fault scenario {} failed; stopping its queue without collecting a result",
                scenarioName, error);
      } finally {
        if (interrupted) {
          Thread.currentThread().interrupt();
        }
      }
    } finally {
      temporalNamespaces.remove(namespace);
    }
    if (completed) {
      onCompletion.run();
    }
  }

  private void restoreEnvironment(Scenario scenario, ExecutionQueue queue) {
    stepRegistry.getPostExecutionSteps().stream()
            .filter(EnvironmentPostStep.class::isInstance)
            .forEach(step -> step.execute(scenario, queue));
  }

  private void handleScenarioFailure(Scenario scenario, ExecutionQueue queue, Exception error) {
    try {
      restoreEnvironment(scenario, queue);
    } catch (RuntimeException cleanup) {
      error.addSuppressed(cleanup);
    }
    try {
      markScenarioFailed(scenario.getMetadata().getNamespace(), queue.getMetadata().getName(),
              scenario.getMetadata().getName(), error);
    } catch (RuntimeException cleanup) {
      error.addSuppressed(cleanup);
    }
  }

  private void markScenarioFailed(String namespace, String queueName, String scenarioName, Exception error) {
    var latest = executionRepository.get(namespace, queueName);
    var item = latest.getItem(scenarioName);
    item.setStatus(ExecutionQueueItem.Status.FAILED);
    item.setFailureReason(error.toString());
    item.setFinishedAt(Instant.now().toString());
    var items = latest.getSpec().getItems();
    latest.setStatus(new ExecutionQueueStatus(
            items.stream().filter(ExecutionQueueItem::isRunning).count(),
            items.stream().filter(ExecutionQueueItem::isPending).count(),
            items.stream().filter(ExecutionQueueItem::isFinished).count(),
            items.stream().filter(ExecutionQueueItem::isFailed).count()));
    executionRepository.update(latest);
    executionRepository.updateStatus(latest);
  }

  @PreDestroy
  void stopTemporalWorkers() {
    temporalWorkers.shutdownNow();
  }

  Watcher<Job> createJobCompletionWatcher(Runnable onCompletion) {
    var completionHandled = new AtomicBoolean(false);
    return new Watcher<>() {
      @Override
      public void eventReceived(Action action, Job resource) {
        var status = resource.getStatus();
        if ((action != Action.MODIFIED && action != Action.ADDED) || status == null) {
          return;
        }
        var failed = status.getConditions().stream()
                .anyMatch(condition -> "Failed".equals(condition.getType()) && "True".equals(condition.getStatus()));
        if (!failed && status.getCompletionTime() == null) {
          return;
        }
        if (!completionHandled.compareAndSet(false, true)) {
          logger.debug("Ignoring duplicate completion event for job: {}", resource.getMetadata().getName());
          return;
        }
        var namespace = resource.getMetadata().getNamespace();
        var scenarioName = resource.getMetadata().getAnnotations().get(SCENARIO);
        var scenario = scenarioRepository.get(namespace, scenarioName);
        var queue = executionRepository.get(namespace, scenario.getMetadata().getAnnotations().get(OWNED_BY));
        if (failed) {
          handleScenarioFailure(scenario, queue, new IllegalStateException("k6 Job failed: " + resource.getMetadata().getName()));
          return;
        }
        try {
          executePostExecutionSteps(scenario, queue);
        } catch (RuntimeException error) {
          handleScenarioFailure(scenario, queue, error);
          logger.error("Scenario {} failed during result collection or cleanup", scenarioName, error);
          return;
        }
        onCompletion.run();
      }
      @Override
      public void onClose(WatcherException cause) {
        logger.info("Job watcher closed", cause); // TODO pesquisar sobre o evento de close
      }
    };
  }


  private void executePreparationSteps(Scenario scenario, ExecutionQueue queue) {
    stepRegistry.getPreparationSteps().forEach(step -> step.execute(scenario, queue));
  }

  private void executePostExecutionSteps(Scenario scenario, ExecutionQueue queue) {
    restoreEnvironment(scenario, queue);
    stepRegistry.getPostExecutionSteps().stream()
            .filter(ResultFileStep.class::isInstance)
            .forEach(step -> step.execute(scenario, queue));
    stepRegistry.getPostExecutionSteps().stream()
            .filter(step -> !(step instanceof EnvironmentPostStep) && !(step instanceof ResultFileStep))
            .forEach(step -> step.execute(scenario, queue));
  }
}
