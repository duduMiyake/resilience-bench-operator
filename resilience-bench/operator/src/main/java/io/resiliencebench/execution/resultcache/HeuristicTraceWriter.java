package io.resiliencebench.execution.resultcache;

import io.resiliencebench.execution.io.FileProvider;
import io.resiliencebench.execution.io.FileProviderFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.selection.ConfigurationResultAggregator;
import io.resiliencebench.resources.selection.ConfigurationSelectionDecision;
import io.resiliencebench.resources.selection.EvaluatedConfiguration;
import io.resiliencebench.resources.selection.EvaluatedScenario;
import io.resiliencebench.resources.selection.configuration.ResilienceConfigurationKey;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import io.resiliencebench.support.CustomResourceRepository;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.objectiveScore;
import static io.resiliencebench.support.Annotations.OWNED_BY;

@Service
public class HeuristicTraceWriter {

  public static final int SCHEMA_VERSION = 2;

  private final FileProvider fileProvider;
  private final ScenarioCacheKeyFactory cacheKeyFactory;
  private final ScenarioResultCache scenarioResultCache;
  private final CustomResourceRepository<Scenario> scenarioRepository;
  private final ConfigurationResultAggregator configurationResultAggregator;

  public HeuristicTraceWriter(FileProviderFactory fileProviderFactory,
                              ScenarioCacheKeyFactory cacheKeyFactory,
                              ScenarioResultCache scenarioResultCache,
                              CustomResourceRepository<Scenario> scenarioRepository,
                              ConfigurationResultAggregator configurationResultAggregator) {
    this.fileProvider = fileProviderFactory.create();
    this.cacheKeyFactory = cacheKeyFactory;
    this.scenarioResultCache = scenarioResultCache;
    this.scenarioRepository = scenarioRepository;
    this.configurationResultAggregator = configurationResultAggregator;
  }

  public void recordSelectedConfigurations(Benchmark benchmark,
                                           ExecutionQueue queue,
                                           ScenarioConfigurationIndex configurationIndex,
                                           List<ConfigurationSelectionDecision> decisions,
                                           String phase,
                                           int evaluatedConfigurations,
                                           int remainingConfigurations) {
    if (!scenarioResultCache.isReadWriteEnabled(benchmark) || decisions.isEmpty()) {
      return;
    }
    var traceFile = traceFile(benchmark, queue);
    var trace = getTrace(traceFile, benchmark, queue);
    for (ConfigurationSelectionDecision decision : decisions) {
      appendConfigurationSelected(trace, configurationIndex, decision, phase,
              evaluatedConfigurations, remainingConfigurations);
    }
    updateSummary(trace);
    fileProvider.writeToFile(traceFile, trace.encode());
  }

  public void recordConfigurationSelected(Benchmark benchmark,
                                          ExecutionQueue queue,
                                          ScenarioConfigurationIndex configurationIndex,
                                          ConfigurationSelectionDecision decision,
                                          String phase,
                                          int evaluatedConfigurations,
                                          int remainingConfigurations) {
    recordSelectedConfigurations(benchmark, queue, configurationIndex, List.of(decision), phase,
            evaluatedConfigurations, remainingConfigurations);
  }

  public void recordScenarioCompleted(Benchmark benchmark, ExecutionQueue queue, Scenario scenario,
                                      String source, JsonObject result) {
    if (!scenarioResultCache.isReadWriteEnabled(benchmark)) {
      return;
    }
    var traceFile = traceFile(benchmark, queue);
    var trace = getTrace(traceFile, benchmark, queue);
    var configurationIndex = ScenarioConfigurationIndex.from(scenariosForBenchmark(queue, benchmark));
    var configurationKey = ResilienceConfigurationKey.from(scenario);
    var decision = findDecision(trace, configurationKey.hash())
            .orElseGet(() -> appendConfigurationSelected(trace, configurationIndex,
                    ConfigurationSelectionDecision.of(configurationKey, ResultStoragePathFactory.strategyType(benchmark)),
                    phaseForFallback(benchmark), 0, 0));

    appendScenarioCompletedEvent(trace, decision, scenario, source, result);
    appendConfigurationEvaluatedIfComplete(trace, benchmark, queue, configurationIndex, configurationKey, decision);
    updateSummary(trace);
    fileProvider.writeToFile(traceFile, trace.encode());
  }

  private JsonObject appendConfigurationSelected(JsonObject trace,
                                                 ScenarioConfigurationIndex configurationIndex,
                                                 ConfigurationSelectionDecision decision,
                                                 String phase,
                                                 int evaluatedConfigurations,
                                                 int remainingConfigurations) {
    var configurationKey = decision.getConfigurationKey();
    var existingDecision = findDecision(trace, configurationKey.hash());
    if (existingDecision.isPresent()) {
      return existingDecision.get();
    }

    var decisions = trace.getJsonArray("decisions", new JsonArray());
    var decisionNumber = decisions.size() + 1;
    var selectedAt = decision.getSelectedAt();
    var decisionJson = new JsonObject()
            .put("decision", decisionNumber)
            .put("phase", phase)
            .put("selectedAt", selectedAt)
            .put("evaluatedConfigurations", evaluatedConfigurations)
            .put("remainingConfigurations", remainingConfigurations)
            .put("configuration", configurationJson(configurationKey))
            .put("selection", new JsonObject()
                    .put("heuristic", decision.getHeuristic())
                    .put("metadata", decision.getMetadata()))
            .put("expectedScenarios", expectedScenarios(configurationIndex, configurationKey))
            .put("executions", new JsonArray());
    decisions.add(decisionJson);
    trace.put("decisions", decisions);
    appendEvent(trace, new JsonObject()
            .put("type", "CONFIGURATION_SELECTED")
            .put("decision", decisionNumber)
            .put("phase", phase)
            .put("configurationHash", configurationKey.hash())
            .put("selectedAt", selectedAt));
    return decisionJson;
  }

  private void appendScenarioCompletedEvent(JsonObject trace, JsonObject decision, Scenario scenario,
                                            String source, JsonObject result) {
    var executions = decision.getJsonArray("executions", new JsonArray());
    var scenarioName = scenario.getMetadata().getName();
    var alreadyRecorded = executions.stream()
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .anyMatch(execution -> scenarioName.equals(execution.getString("scenario")));
    if (alreadyRecorded) {
      return;
    }

    var completedAt = now();
    var execution = scenarioContext(scenario)
            .put("source", source)
            .put("scenarioHash", cacheKeyFactory.hash(scenario))
            .put("resultScore", scenarioResultCache.resultScore(result))
            .put("completedAt", completedAt);
    executions.add(execution);
    decision.put("executions", executions);
    appendEvent(trace, new JsonObject()
            .put("type", "SCENARIO_COMPLETED")
            .put("decision", decision.getInteger("decision"))
            .put("scenario", scenarioName)
            .put("source", source)
            .put("resultScore", execution.getDouble("resultScore"))
            .put("completedAt", completedAt));
  }

  private void appendConfigurationEvaluatedIfComplete(JsonObject trace, Benchmark benchmark, ExecutionQueue queue,
                                                      ScenarioConfigurationIndex configurationIndex,
                                                      ResilienceConfigurationKey configurationKey,
                                                      JsonObject decision) {
    if (decision.getJsonObject("aggregatedResult") != null) {
      return;
    }

    var resultByScenarioName = evaluatedScenarios(queue);
    Optional<EvaluatedConfiguration> aggregated = configurationResultAggregator.aggregate(
            configurationIndex, configurationKey, resultByScenarioName);
    if (aggregated.isEmpty() || !aggregated.get().isComplete()) {
      return;
    }

    double currentScore = objectiveScore(aggregated.get(), benchmark);
    var previousBest = bestEvaluatedConfiguration(trace);
    boolean improvedBest = previousBest.isEmpty() || currentScore > previousBest.get().score();
    var best = improvedBest
            ? new BestConfiguration(configurationKey.hash(), configurationKey.summary(), currentScore)
            : previousBest.get();

    var evaluatedAt = now();
    decision.put("aggregatedResult", new JsonObject()
            .put("evaluatedAt", evaluatedAt)
            .put("score", currentScore)
            .put("currentScore", currentScore)
            .put("bestScoreSoFar", best.score())
            .put("bestConfigurationSoFar", new JsonObject()
                    .put("hash", best.hash())
                    .put("summary", best.summary()))
            .put("improvedBest", improvedBest)
            .put("metrics", aggregated.get().getMetrics().copy()));
    appendEvent(trace, new JsonObject()
            .put("type", "CONFIGURATION_EVALUATED")
            .put("decision", decision.getInteger("decision"))
            .put("configurationHash", configurationKey.hash())
            .put("score", currentScore)
            .put("bestScoreSoFar", best.score())
            .put("improvedBest", improvedBest)
            .put("evaluatedAt", evaluatedAt));
  }

  private Map<String, EvaluatedScenario> evaluatedScenarios(ExecutionQueue queue) {
    Map<String, EvaluatedScenario> resultByScenarioName = new LinkedHashMap<>();
    var results = fileProvider.getFileAsString(queue.getSpec().getResultFile());
    if (results.isEmpty()) {
      return resultByScenarioName;
    }
    new JsonObject(results.get()).getJsonArray("results", new JsonArray()).stream()
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .filter(result -> result.getString("scenario") != null)
            .forEach(result -> resultByScenarioName.put(
                    result.getString("scenario"),
                    new EvaluatedScenario(result.getString("scenario"), result)));
    return resultByScenarioName;
  }

  private List<Scenario> scenariosForBenchmark(ExecutionQueue queue, Benchmark benchmark) {
    return scenarioRepository.list(queue.getMetadata().getNamespace()).stream()
            .filter(scenario -> scenario.getMetadata().getAnnotations() != null)
            .filter(scenario -> benchmark.getMetadata().getName().equals(scenario.getMetadata().getAnnotations().get(OWNED_BY)))
            .toList();
  }

  private Optional<JsonObject> findDecision(JsonObject trace, String configurationHash) {
    return trace.getJsonArray("decisions", new JsonArray()).stream()
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .filter(decision -> configurationHash.equals(decision.getJsonObject("configuration", new JsonObject()).getString("hash")))
            .findFirst();
  }

  private Optional<BestConfiguration> bestEvaluatedConfiguration(JsonObject trace) {
    return trace.getJsonArray("decisions", new JsonArray()).stream()
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .map(decision -> new JsonObject()
                    .put("configuration", decision.getJsonObject("configuration", new JsonObject()))
                    .put("aggregatedResult", decision.getJsonObject("aggregatedResult")))
            .filter(item -> item.getJsonObject("aggregatedResult") != null)
            .map(item -> new BestConfiguration(
                    item.getJsonObject("configuration").getString("hash"),
                    item.getJsonObject("configuration").getString("summary"),
                    item.getJsonObject("aggregatedResult").getDouble("currentScore")))
            .max(java.util.Comparator.comparingDouble(BestConfiguration::score));
  }

  private void appendEvent(JsonObject trace, JsonObject event) {
    var events = trace.getJsonArray("events", new JsonArray());
    event.put("sequence", events.size() + 1);
    events.add(event);
    trace.put("events", events);
  }

  private void updateSummary(JsonObject trace) {
    var decisions = trace.getJsonArray("decisions", new JsonArray());
    int totalConfigurationsEvaluated = 0;
    int totalScenariosExecuted = 0;
    int totalCacheHits = 0;
    for (Object value : decisions) {
      if (!(value instanceof JsonObject decision)) {
        continue;
      }
      if (decision.getJsonObject("aggregatedResult") != null) {
        totalConfigurationsEvaluated++;
      }
      for (Object executionValue : decision.getJsonArray("executions", new JsonArray())) {
        if (!(executionValue instanceof JsonObject execution)) {
          continue;
        }
        if (ScenarioResultCache.CACHE_HIT.equals(execution.getString("source"))) {
          totalCacheHits++;
        }
        if (ScenarioResultCache.EXECUTED.equals(execution.getString("source"))) {
          totalScenariosExecuted++;
        }
      }
    }
    trace.put("totalConfigurationsEvaluated", totalConfigurationsEvaluated);
    trace.put("totalScenariosExecuted", totalScenariosExecuted);
    trace.put("totalCacheHits", totalCacheHits);
    if (totalConfigurationsEvaluated == decisions.size() && trace.getString("finishedAt") == null) {
      trace.put("finishedAt", now());
    }
  }

  private JsonArray expectedScenarios(ScenarioConfigurationIndex configurationIndex,
                                      ResilienceConfigurationKey configurationKey) {
    return new JsonArray(configurationIndex.scenariosFor(configurationKey).stream()
            .map(HeuristicTraceWriter::scenarioContext)
            .toList());
  }

  private static JsonObject scenarioContext(Scenario scenario) {
    var json = new JsonObject()
            .put("scenario", scenario.getMetadata().getName())
            .put("workload", new JsonObject()
                    .put("name", scenario.getSpec().getWorkload().getWorkloadName())
                    .put("users", scenario.getSpec().getWorkload().getUsers()));
    if (scenario.getSpec().getFault() != null) {
      json.put("fault", new JsonObject()
              .put("provider", scenario.getSpec().getFault().getProvider())
              .put("percentage", scenario.getSpec().getFault().getPercentage())
              .put("services", new JsonArray(scenario.getSpec().getFault().getServices())));
    }
    return json;
  }

  private static JsonObject configurationJson(ResilienceConfigurationKey configurationKey) {
    return new JsonObject()
            .put("hash", configurationKey.hash())
            .put("summary", configurationKey.summary())
            .put("normalized", configurationKey.normalized());
  }

  private JsonObject getTrace(String traceFile, Benchmark benchmark, ExecutionQueue queue) {
    return fileProvider.getFileAsString(traceFile)
            .map(JsonObject::new)
            .orElseGet(() -> createTrace(benchmark, queue));
  }

  private static JsonObject createTrace(Benchmark benchmark, ExecutionQueue queue) {
    var strategy = benchmark.getSpec().getStrategy();
    var trace = new JsonObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("benchmark", benchmark.getMetadata().getName())
            .put("heuristic", ResultStoragePathFactory.strategyType(benchmark))
            .put("runId", runIdFromResultFile(queue.getSpec().getResultFile()))
            .put("resultFile", queue.getSpec().getResultFile())
            .put("startedAt", now())
            .put("decisions", new JsonArray())
            .put("events", new JsonArray())
            .put("totalConfigurationsEvaluated", 0)
            .put("totalScenariosExecuted", 0)
            .put("totalCacheHits", 0);
    if (strategy != null) {
      trace.put("initialSamples", strategy.getInitialSamples());
      trace.put("maxEvaluations", strategy.getMaxEvaluations());
      trace.put("maxConfigurations", strategy.getMaxConfigurations());
    }
    return trace;
  }

  private static String phaseForFallback(Benchmark benchmark) {
    var type = ResultStoragePathFactory.strategyType(benchmark);
    if (ScenarioSelectionStrategySpec.KNN_ADAPTIVE.equalsIgnoreCase(type)) {
      return "adaptiveSelection";
    }
    return type;
  }

  private static String traceFile(Benchmark benchmark, ExecutionQueue queue) {
    return ResultStoragePathFactory.traceFile(benchmark, runIdFromResultFile(queue.getSpec().getResultFile()));
  }

  public static String runIdFromResultFile(String resultFile) {
    var normalized = resultFile.replace("\\", "/");
    if (normalized.endsWith("/results.json")) {
      var parent = normalized.substring(0, normalized.length() - "/results.json".length());
      return parent.substring(parent.lastIndexOf('/') + 1);
    }
    var fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
    return fileName.replace("-results.json", "");
  }

  private static String now() {
    return LocalDateTime.now(ZoneOffset.UTC).toString();
  }

  private record BestConfiguration(String hash, String summary, double score) {
  }
}
