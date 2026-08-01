package io.resiliencebench.execution.steps;

import io.resiliencebench.execution.io.FileProvider;
import io.resiliencebench.execution.io.FileProviderFactory;
import io.resiliencebench.execution.resultcache.HeuristicTraceWriter;
import io.resiliencebench.execution.resultcache.ScenarioResultCache;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.support.CustomResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.scenario.Scenario;
import io.vertx.core.json.JsonObject;


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
      scenarioResultCache.appendToRun(executionQueue, currentResultsJson);
      heuristicTraceWriter.recordScenarioCompleted(benchmark.get(), executionQueue, scenario,
              ScenarioResultCache.EXECUTED, currentResultsJson);
      scenarioResultCache.saveToCache(benchmark.get(), scenario, currentResultsJson);
    } else {
      logger.warn("No results found for {}", executionQueueItem.getResultFile());
    }
  }

  public JsonObject getJsonResults(ExecutionQueue executionQueue) {
    return scenarioResultCache.getRunResults(executionQueue);
  }

}

