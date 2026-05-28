
package io.resiliencebench.execution;

import static java.lang.String.format;

import io.resiliencebench.execution.io.FileProvider;
import io.resiliencebench.execution.io.FileProviderFactory;
import io.resiliencebench.resources.ExecutionQueueFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.queue.ExecutionQueueItem;
import io.resiliencebench.resources.queue.ExecutionQueueStatus;
import io.resiliencebench.resources.selection.EvaluatedScenario;
import io.resiliencebench.resources.selection.ScenarioSelectionStrategySelector;
import io.resiliencebench.resources.workload.Workload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.support.CustomResourceRepository;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.resiliencebench.support.Annotations.OWNED_BY;
import static io.resiliencebench.resources.queue.ExecutionQueueItem.Status.*;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

@Service
public class DefaultQueueExecutor implements QueueExecutor {

  private final static Logger logger = LoggerFactory.getLogger(DefaultQueueExecutor.class);

  private final CustomResourceRepository<Scenario> scenarioRepository;
  private final CustomResourceRepository<ExecutionQueue> executionRepository;
  private final CustomResourceRepository<Benchmark> benchmarkRepository;
  private final CustomResourceRepository<Workload> workloadRepository;

  private final ScenarioExecutor scenarioExecutor;
  private final ScenarioSelectionStrategySelector scenarioSelectionStrategySelector;
  private final FileProvider fileProvider;

  public DefaultQueueExecutor(
          CustomResourceRepository<Scenario> scenarioRepository,
          CustomResourceRepository<ExecutionQueue> executionRepository,
          CustomResourceRepository<Benchmark> benchmarkRepository,
          CustomResourceRepository<Workload> workloadRepository,
          ScenarioExecutor scenarioExecutor,
          ScenarioSelectionStrategySelector scenarioSelectionStrategySelector,
          FileProviderFactory fileProviderFactory) {
    this.scenarioRepository = scenarioRepository;
    this.executionRepository = executionRepository;
    this.benchmarkRepository = benchmarkRepository;
    this.workloadRepository = workloadRepository;
    this.scenarioExecutor = scenarioExecutor;
    this.scenarioSelectionStrategySelector = scenarioSelectionStrategySelector;
    this.fileProvider = fileProviderFactory.create();
  }

  @Override
  public void execute(ExecutionQueue queue) {
    var queueToExecute = executionRepository.find(queue.getMetadata())
            .orElseThrow(() -> new RuntimeException("Queue not found " + queue.getMetadata().getName()));

    var nextItem = queueToExecute.getNextPendingItem();

    if (nextItem.isPresent() && nextItem.get().isPending()) {
      executeScenario(nextItem.get(), queueToExecute);
    } else if (appendNextAdaptiveScenario(queueToExecute).isPresent()) {
      execute(queueToExecute);
    } else {
      logger.info("No item available for queue: {}", queueToExecute.getMetadata().getName());
      if (queueToExecute.isDone()) {
        logger.info("All items finished for: {}", queueToExecute.getMetadata().getName());
      }
    }
  }

  private void executeScenario(ExecutionQueueItem item, ExecutionQueue executionQueue) {
    var scenarioName = item.getScenario();
    var namespace = executionQueue.getMetadata().getNamespace();
    var scenario = scenarioRepository.find(namespace, scenarioName);
    if (scenario.isPresent()) {
      logger.info("Running scenario: {}", scenarioName);
      scenarioExecutor.execute(scenario.get(), executionQueue, () -> execute(executionQueue));
    } else {
      throw new RuntimeException(format("Scenario not found: %s.%s", namespace, scenarioName));
    }
  }

  private Optional<ExecutionQueue> appendNextAdaptiveScenario(ExecutionQueue queue) {
    var namespace = queue.getMetadata().getNamespace();
    var benchmarkName = queue.getSpec().getBenchmark();
    var benchmark = benchmarkRepository.find(namespace, benchmarkName);
    if (benchmark.isEmpty()) {
      logger.warn("Benchmark not found for adaptive queue: {}.{}", namespace, benchmarkName);
      return Optional.empty();
    }

    var adaptiveStrategy = scenarioSelectionStrategySelector.selectAdaptive(benchmark.get());
    if (adaptiveStrategy.isEmpty()) {
      return Optional.empty();
    }

    var workloadName = benchmark.get().getSpec().getWorkload();
    var workload = workloadRepository.find(namespace, workloadName);
    if (workload.isEmpty()) {
      logger.warn("Workload not found for adaptive queue: {}.{}", namespace, workloadName);
      return Optional.empty();
    }

    var queuedScenarioNames = queue.getSpec().getItems().stream()
            .map(ExecutionQueueItem::getScenario)
            .collect(Collectors.toSet());
    var evaluatedScenarios = readEvaluatedScenarios(queue);
    var allScenarios = scenarioRepository.list(namespace).stream()
            .filter(scenario -> scenario.getMetadata().getAnnotations() != null)
            .filter(scenario -> benchmarkName.equals(scenario.getMetadata().getAnnotations().get(OWNED_BY)))
            .toList();

    var nextScenario = adaptiveStrategy.get().selectNextScenario(
            allScenarios,
            queuedScenarioNames,
            evaluatedScenarios,
            benchmark.get(),
            workload.get());

    if (nextScenario.isEmpty()) {
      return Optional.empty();
    }

    var latestQueue = executionRepository.get(namespace, queue.getMetadata().getName());
    latestQueue.getSpec().getItems().add(new ExecutionQueueItem(
            nextScenario.get().getMetadata().getName(),
            ExecutionQueueFactory.createItemResultFile(latestQueue, nextScenario.get().getMetadata().getName())));
    latestQueue.setStatus(createStatus(latestQueue));
    var updatedQueue = executionRepository.update(latestQueue);
    executionRepository.updateStatus(updatedQueue);
    logger.info("Adaptive strategy appended scenario {} to queue {}",
            nextScenario.get().getMetadata().getName(),
            queue.getMetadata().getName());
    return Optional.of(updatedQueue);
  }

  private List<EvaluatedScenario> readEvaluatedScenarios(ExecutionQueue queue) {
    var results = fileProvider.getFileAsString(queue.getSpec().getResultFile());
    if (results.isEmpty()) {
      return java.util.List.of();
    }
    var resultsJson = new JsonObject(results.get());
    var resultsArray = resultsJson.getJsonArray("results", new JsonArray());
    return resultsArray.stream()
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .filter(result -> result.getString("scenario") != null)
            .map(result -> new EvaluatedScenario(result.getString("scenario"), result))
            .toList();
  }

  private static ExecutionQueueStatus createStatus(ExecutionQueue queue) {
    var statusCounts = queue.getSpec().getItems().stream().collect(groupingBy(ExecutionQueueItem::getStatus, counting()));
    return new ExecutionQueueStatus(
            statusCounts.getOrDefault(RUNNING, 0L),
            statusCounts.getOrDefault(PENDING, 0L),
            statusCounts.getOrDefault(FINISHED, 0L)
    );
  }
}
