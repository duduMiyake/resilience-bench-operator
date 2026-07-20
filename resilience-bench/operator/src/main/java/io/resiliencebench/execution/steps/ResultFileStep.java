package io.resiliencebench.execution.steps;

import io.resiliencebench.execution.io.FileProvider;
import io.resiliencebench.execution.io.FileProviderFactory;
import io.resiliencebench.execution.resultcache.HeuristicTraceWriter;
import io.resiliencebench.execution.resultcache.ScenarioResultCache;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.support.CustomResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.scenario.Scenario;
import io.vertx.core.json.JsonObject;

import java.util.stream.IntStream;

@Service
public class ResultFileStep extends ExecutorStep {

  private final static Logger logger = LoggerFactory.getLogger(ResultFileStep.class);

  private final FileProvider fileProvider;
  private final ScenarioResultCache scenarioResultCache;
  private final HeuristicTraceWriter heuristicTraceWriter;
  private final CustomResourceRepository<Benchmark> benchmarkRepository;

  public ResultFileStep(KubernetesClient kubernetesClient,
                        FileProviderFactory fileProviderFactory,
                        ScenarioResultCache scenarioResultCache,
                        HeuristicTraceWriter heuristicTraceWriter,
                        CustomResourceRepository<Benchmark> benchmarkRepository) {
    super(kubernetesClient);
    this.fileProvider = fileProviderFactory.create();
    this.scenarioResultCache = scenarioResultCache;
    this.heuristicTraceWriter = heuristicTraceWriter;
    this.benchmarkRepository = benchmarkRepository;
  }

  @Override
  protected boolean isApplicable(Scenario scenario) {
    return true;
  }

  @Override
  protected void internalExecute(Scenario scenario, ExecutionQueue executionQueue) {
    var executionQueueItem = executionQueue.getItem(scenario.getMetadata().getName());
    var currentResults = fileProvider.getFileAsString(executionQueueItem.getResultFile());
    if (currentResults.isPresent()) {
      var benchmark = benchmarkRepository.find(executionQueue.getMetadata().getNamespace(), executionQueue.getSpec().getBenchmark());
      if (benchmark.isEmpty()) {
        logger.warn("Benchmark {} not found while writing results", executionQueue.getSpec().getBenchmark());
        return;
      }
      var currentResultsJson = scenarioResultCache.enrichResult(
              new JsonObject(currentResults.get()),
              benchmark.get(),
              scenario,
              ScenarioResultCache.EXECUTED);
      scenarioResultCache.writeItemResult(executionQueue, scenario, currentResultsJson);
      heuristicTraceWriter.appendStep(benchmark.get(), executionQueue, scenario,
              phase(executionQueue, benchmark.get(), scenario.getMetadata().getName()),
              ScenarioResultCache.EXECUTED, currentResultsJson);
      scenarioResultCache.appendToRun(executionQueue, currentResultsJson);
      scenarioResultCache.saveToCache(benchmark.get(), scenario, currentResultsJson);
    } else {
      logger.warn("No results found for {}", executionQueueItem.getResultFile());
    }
  }

  public JsonObject getJsonResults(ExecutionQueue executionQueue) {
    return scenarioResultCache.getRunResults(executionQueue);
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


