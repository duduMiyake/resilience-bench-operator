package io.resiliencebench.execution;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.resiliencebench.execution.steps.StepRegistry;
import io.resiliencebench.execution.steps.k6.K6JobFactory;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.workload.Workload;
import io.resiliencebench.support.CustomResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.resiliencebench.support.Annotations.*;
import static java.util.Objects.nonNull;

@Service
public class DefaultScenarioExecutor implements ScenarioExecutor {

  private final static Logger logger = LoggerFactory.getLogger(DefaultScenarioExecutor.class);
  private final KubernetesClient kubernetesClient;
  private final StepRegistry stepRegistry;
  private final K6JobFactory k6JobFactory;

  private final CustomResourceRepository<Scenario> scenarioRepository;
  private final CustomResourceRepository<ExecutionQueue> executionRepository;
  private final CustomResourceRepository<Workload> workloadRepository;

  public DefaultScenarioExecutor(KubernetesClient kubernetesClient,
                                 StepRegistry stepRegistry,
                                 K6JobFactory k6JobFactory,
                                 CustomResourceRepository<Scenario> scenarioRepository,
                                 CustomResourceRepository<ExecutionQueue> executionRepository,
                                 CustomResourceRepository<Workload> workloadRepository) {
    this.kubernetesClient = kubernetesClient;
    this.stepRegistry = stepRegistry;
    this.k6JobFactory = k6JobFactory;
    this.scenarioRepository = scenarioRepository;
    this.executionRepository = executionRepository;
    this.workloadRepository = workloadRepository;
  }

  @Override
  public void execute(Scenario scenario, ExecutionQueue executionQueue, Runnable onCompletion) {
    var ns = scenario.getMetadata().getNamespace();
    var workloadName = scenario.getSpec().getWorkload().getWorkloadName();
    var workload = workloadRepository.find(ns, workloadName)
            .orElseThrow(() -> new IllegalArgumentException("Workload does not exists: %s".formatted(workloadName)));

    executePreparationSteps(scenario, executionQueue);
    var executionQueueItem = executionQueue.getItem(scenario.getMetadata().getName());
    var job = k6JobFactory.create(scenario, workload, executionQueueItem);
    var jobsClient = kubernetesClient.batch().v1().jobs();
    jobsClient.inNamespace(job.getMetadata().getNamespace()).withName(job.getMetadata().getName()).delete();
    jobsClient.resource(job).create();
    logger.info("Job created: {}", job.getMetadata().getName());
    jobsClient.resource(job).watch(createJobCompletionWatcher(onCompletion));
  }

  Watcher<Job> createJobCompletionWatcher(Runnable onCompletion) {
    var completionHandled = new AtomicBoolean(false);
    return new Watcher<>() {
      @Override
      public void eventReceived(Action action, Job resource) {
        if (action.equals(Action.MODIFIED) && nonNull(resource.getStatus().getCompletionTime())) {
          if (!completionHandled.compareAndSet(false, true)) {
            logger.debug("Ignoring duplicate completion event for job: {}", resource.getMetadata().getName());
            return;
          }
          logger.info("Finished job: {}", resource.getMetadata().getName());
          var namespace = resource.getMetadata().getNamespace();
          var scenarioName = resource.getMetadata().getAnnotations().get(SCENARIO);
          var scenario = scenarioRepository.get(namespace, scenarioName);
          var executionQueue = executionRepository.get(
                  namespace,
                  scenario.getMetadata().getAnnotations().get(OWNED_BY)
          );
          executePostExecutionSteps(scenario, executionQueue);
          onCompletion.run();
        }
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
    stepRegistry.getPostExecutionSteps().forEach(step -> step.execute(scenario, queue));
  }
}
