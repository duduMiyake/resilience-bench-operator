package io.resiliencebench.execution;

import static java.lang.String.format;

import io.resiliencebench.execution.io.FileProvider;
import io.resiliencebench.execution.io.FileProviderFactory;
import io.resiliencebench.execution.resultcache.HeuristicTraceWriter;
import io.resiliencebench.execution.resultcache.ScenarioResultCache;
import io.resiliencebench.resources.ExecutionQueueFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.queue.ExecutionQueueItem;
import io.resiliencebench.resources.queue.ExecutionQueueStatus;
import io.resiliencebench.resources.selection.ConfigurationResultAggregator;
import io.resiliencebench.resources.selection.EvaluatedScenario;
import io.resiliencebench.resources.selection.ScenarioSelectionStrategySelector;
import io.resiliencebench.resources.selection.configuration.ResilienceConfigurationKey;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import io.resiliencebench.resources.workload.Workload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.support.CustomResourceRepository;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
  private final ScenarioResultCache scenarioResultCache;
  private final HeuristicTraceWriter heuristicTraceWriter;
  private final ConfigurationResultAggregator configurationResultAggregator;

  public DefaultQueueExecutor(
          CustomResourceRepository<Scenario> scenarioRepository,
          CustomResourceRepository<ExecutionQueue> executionRepository,
          CustomResourceRepository<Benchmark> benchmarkRepository,
          CustomResourceRepository<Workload> workloadRepository,
          ScenarioExecutor scenarioExecutor,
          ScenarioSelectionStrategySelector scenarioSelectionStrategySelector,
          FileProviderFactory fileProviderFactory,
          ScenarioResultCache scenarioResultCache,
          HeuristicTraceWriter heuristicTraceWriter,
          ConfigurationResultAggregator configurationResultAggregator) {
    this.scenarioRepository = scenarioRepository;
    this.executionRepository = executionRepository;
    this.benchmarkRepository = benchmarkRepository;
    this.workloadRepository = workloadRepository;
    this.scenarioExecutor = scenarioExecutor;
    this.scenarioSelectionStrategySelector = scenarioSelectionStrategySelector;
    this.fileProvider = fileProviderFactory.create();
    this.scenarioResultCache = scenarioResultCache;
    this.heuristicTraceWriter = heuristicTraceWriter;
    this.configurationResultAggregator = configurationResultAggregator;
  }

  @Override
  public void execute(ExecutionQueue queue) {
    var queueToExecute = executionRepository.find(queue.getMetadata())
            .orElseThrow(() -> new RuntimeException("Queue not found " + queue.getMetadata().getName()));

    var nextItem = queueToExecute.getNextPendingItem();

    if (nextItem.isPresent() && nextItem.get().isPending()) {
      executeScenario(nextItem.get(), queueToExecute);
    } else if (appendNextAdaptiveConfiguration(queueToExecute).isPresent()) {
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
    if (scenario.isEmpty()) {
      throw new RuntimeException(format("Scenario not found: %s.%s", namespace, scenarioName));
    }

    var benchmark = benchmarkRepository.find(namespace, executionQueue.getSpec().getBenchmark());
    if (benchmark.isPresent() && tryReplayCachedResult(item, executionQueue, scenario.get(), benchmark.get())) {
      execute(executionQueue);
      return;
    }

    logger.info("Running scenario: {}", scenarioName);
    scenarioExecutor.execute(scenario.get(), executionQueue, () -> execute(executionQueue));
  }

  private boolean tryReplayCachedResult(ExecutionQueueItem item, ExecutionQueue executionQueue,
                                        Scenario scenario, Benchmark benchmark) {
    if (!scenarioResultCache.isReadWriteEnabled(benchmark)) {
      return false;
    }
    var cachedResult = scenarioResultCache.get(benchmark, scenario);
    if (cachedResult.isEmpty()) {
      return false;
    }

    logger.info("Replaying cached result for scenario: {}", scenario.getMetadata().getName());
    var replayedResult = scenarioResultCache.enrichResult(cachedResult.get(), benchmark, scenario, ScenarioResultCache.CACHE_HIT);
    scenarioResultCache.writeItemResult(executionQueue, scenario, replayedResult);
    heuristicTraceWriter.appendStep(benchmark, executionQueue, scenario,
            phase(executionQueue, benchmark, scenario.getMetadata().getName()),
            ScenarioResultCache.CACHE_HIT, replayedResult);
    scenarioResultCache.appendToRun(executionQueue, replayedResult);
    finishItem(executionQueue, item);
    return true;
  }

  private void finishItem(ExecutionQueue executionQueue, ExecutionQueueItem item) {
    var latestQueue = executionRepository.get(executionQueue.getMetadata().getNamespace(), executionQueue.getMetadata().getName());
    var latestItem = latestQueue.getItem(item.getScenario());
    var now = LocalDateTime.now(ZoneOffset.UTC).toString();
    latestItem.setStartedAt(now);
    latestItem.setStatus(FINISHED);
    latestItem.setFinishedAt(now);
    latestQueue.setStatus(createStatus(latestQueue));
    var updatedQueue = executionRepository.update(latestQueue);
    executionRepository.updateStatus(updatedQueue);
  }

  private Optional<ExecutionQueue> appendNextAdaptiveConfiguration(ExecutionQueue queue) {
    if (queue.getSpec().getItems().stream().anyMatch(ExecutionQueueItem::isRunning)) {
      return Optional.empty();
    }
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

    var evaluatedScenarios = readEvaluatedScenarios(queue);
    var allScenarios = scenarioRepository.list(namespace).stream()
            .filter(scenario -> scenario.getMetadata().getAnnotations() != null)
            .filter(scenario -> benchmarkName.equals(scenario.getMetadata().getAnnotations().get(OWNED_BY)))
            .toList();
    var configurationIndex = ScenarioConfigurationIndex.from(allScenarios);
    var alreadyQueuedConfigurations = queuedConfigurations(queue, configurationIndex);
    var evaluatedConfigurations = configurationResultAggregator.completeEvaluations(configurationIndex, evaluatedScenarios);

    var nextConfiguration = adaptiveStrategy.get().selectNextConfiguration(
            configurationIndex,
            alreadyQueuedConfigurations,
            evaluatedConfigurations,
            benchmark.get(),
            workload.get());

    if (nextConfiguration.isEmpty()) {
      return Optional.empty();
    }

    var scenariosToAppend = configurationIndex.scenariosFor(nextConfiguration.get()).stream()
            .filter(scenario -> queue.getItem(scenario.getMetadata().getName()) == null)
            .toList();
    if (scenariosToAppend.isEmpty()) {
      return Optional.empty();
    }

    var latestQueue = executionRepository.get(namespace, queue.getMetadata().getName());
    for (Scenario scenario : scenariosToAppend) {
      latestQueue.getSpec().getItems().add(new ExecutionQueueItem(
              scenario.getMetadata().getName(),
              ExecutionQueueFactory.createItemResultFile(latestQueue, scenario.getMetadata().getName())));
    }
    latestQueue.setStatus(createStatus(latestQueue));
    var updatedQueue = executionRepository.update(latestQueue);
    executionRepository.updateStatus(updatedQueue);
    logger.info("Adaptive strategy appended configuration {} with {} scenarios to queue {}",
            nextConfiguration.get().summary(),
            scenariosToAppend.size(),
            queue.getMetadata().getName());
    return Optional.of(updatedQueue);
  }

  private Set<ResilienceConfigurationKey> queuedConfigurations(ExecutionQueue queue,
                                                               ScenarioConfigurationIndex configurationIndex) {
    return queue.getSpec().getItems().stream()
            .map(ExecutionQueueItem::getScenario)
            .map(configurationIndex::keyForScenarioName)
            .flatMap(Optional::stream)
            .collect(Collectors.toSet());
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

  private static String phase(ExecutionQueue executionQueue, Benchmark benchmark, String scenarioName) {
    var strategy = benchmark.getSpec().getStrategy();
    var type = strategy == null || strategy.getType() == null ? ScenarioSelectionStrategySpec.EXHAUSTIVE : strategy.getType();
    if (ScenarioSelectionStrategySpec.EXHAUSTIVE.equalsIgnoreCase(type)) {
      return "exhaustive";
    }
    if (ScenarioSelectionStrategySpec.RANDOM_SAMPLING.equalsIgnoreCase(type)) {
      return "randomSampling";
    }
    if (ScenarioSelectionStrategySpec.KNN_ADAPTIVE.equalsIgnoreCase(type)) {
      var index = IntStream.range(0, executionQueue.getSpec().getItems().size())
              .filter(i -> scenarioName.equals(executionQueue.getSpec().getItems().get(i).getScenario()))
              .findFirst()
              .orElse(executionQueue.getSpec().getItems().size());
      var initialSamples = strategy.getInitialSamples() == null
              ? ScenarioSelectionStrategySpec.DEFAULT_INITIAL_SAMPLES
              : strategy.getInitialSamples();
      return index < initialSamples ? "initialSample" : "adaptiveSelection";
    }
    return "execution";
  }
}



