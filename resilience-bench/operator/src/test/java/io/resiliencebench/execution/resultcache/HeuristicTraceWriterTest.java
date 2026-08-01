package io.resiliencebench.execution.resultcache;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.resiliencebench.execution.io.FileProvider;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.ResultCacheSpec;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.queue.ExecutionQueueItem;
import io.resiliencebench.resources.queue.ExecutionQueueSpec;
import io.resiliencebench.resources.scenario.Connector;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.scenario.ScenarioFault;
import io.resiliencebench.resources.scenario.ScenarioSpec;
import io.resiliencebench.resources.scenario.ScenarioWorkload;
import io.resiliencebench.resources.scenario.Service;
import io.resiliencebench.resources.selection.ConfigurationResultAggregator;
import io.resiliencebench.resources.selection.ConfigurationSelectionDecision;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import io.resiliencebench.support.CustomResourceRepository;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeuristicTraceWriterTest {

  @Test
  void should_record_one_decision_for_configuration_with_all_context_scenarios() {
    var fileProvider = new InMemoryFileProvider();
    var keyFactory = new ScenarioCacheKeyFactory();
    var resultCache = new ScenarioResultCache(() -> fileProvider, keyFactory);
    @SuppressWarnings("unchecked")
    var scenarioRepository = (CustomResourceRepository<Scenario>) mock(CustomResourceRepository.class);
    var writer = new HeuristicTraceWriter(() -> fileProvider, keyFactory, resultCache, scenarioRepository,
            new ConfigurationResultAggregator());
    var benchmark = benchmark("knnAdaptive");
    var first = scenario("scenario-100-25", 2, 100, 25);
    var second = scenario("scenario-300-50", 2, 300, 50);
    var index = ScenarioConfigurationIndex.from(of(first, second));
    var configuration = index.keyForScenarioName("scenario-100-25").orElseThrow();
    when(scenarioRepository.list("default")).thenReturn(of(first, second));

    writer.recordSelectedConfigurations(benchmark, queue(), index,
            of(ConfigurationSelectionDecision.of(configuration, "knnAdaptive")),
            "initialSample", 0, 3);

    var firstResult = resultCache.enrichResult(new JsonObject()
                    .put("checkout_success_rate", 0.8)
                    .put("iteration_duration_p95", 0.1),
            benchmark, first, ScenarioResultCache.CACHE_HIT);
    resultCache.appendToRun(queue(), firstResult);
    writer.recordScenarioCompleted(benchmark, queue(), first, ScenarioResultCache.CACHE_HIT, firstResult);

    var secondResult = resultCache.enrichResult(new JsonObject()
                    .put("checkout_success_rate", 1.0)
                    .put("iteration_duration_p95", 0.2),
            benchmark, second, ScenarioResultCache.EXECUTED);
    resultCache.appendToRun(queue(), secondResult);
    writer.recordScenarioCompleted(benchmark, queue(), second, ScenarioResultCache.EXECUTED, secondResult);

    var trace = trace(fileProvider);
    assertEquals(2, trace.getInteger("schemaVersion"));
    assertEquals(1, trace.getJsonArray("decisions").size());
    var decision = trace.getJsonArray("decisions").getJsonObject(0);
    assertEquals("initialSample", decision.getString("phase"));
    assertEquals(2, decision.getJsonArray("expectedScenarios").size());
    assertEquals(2, decision.getJsonArray("executions").size());
    assertEquals("cacheHit", decision.getJsonArray("executions").getJsonObject(0).getString("source"));
    assertEquals("executed", decision.getJsonArray("executions").getJsonObject(1).getString("source"));
    assertNotNull(decision.getJsonObject("aggregatedResult"));
    assertEquals(0.75, decision.getJsonObject("aggregatedResult").getDouble("score"), 0.000001);
    assertEquals(1, trace.getInteger("totalConfigurationsEvaluated"));
    assertEquals(1, trace.getInteger("totalScenariosExecuted"));
    assertEquals(1, trace.getInteger("totalCacheHits"));
    assertEquals("CONFIGURATION_SELECTED", trace.getJsonArray("events").getJsonObject(0).getString("type"));
    assertEquals("SCENARIO_COMPLETED", trace.getJsonArray("events").getJsonObject(1).getString("type"));
    assertEquals("CONFIGURATION_EVALUATED", trace.getJsonArray("events").getJsonObject(3).getString("type"));
  }

  @Test
  void should_preserve_knn_metadata_produced_by_strategy() {
    var fileProvider = new InMemoryFileProvider();
    var keyFactory = new ScenarioCacheKeyFactory();
    var resultCache = new ScenarioResultCache(() -> fileProvider, keyFactory);
    @SuppressWarnings("unchecked")
    var scenarioRepository = (CustomResourceRepository<Scenario>) mock(CustomResourceRepository.class);
    var writer = new HeuristicTraceWriter(() -> fileProvider, keyFactory, resultCache, scenarioRepository,
            new ConfigurationResultAggregator());
    var benchmark = benchmark("knnAdaptive");
    var candidate = scenario("candidate", 5, 300, 50);
    var index = ScenarioConfigurationIndex.from(of(candidate));
    var metadata = new JsonObject()
            .put("predictedScore", 0.123456)
            .put("uncertainty", 0.5)
            .put("explorationBonus", 0.05)
            .put("selectionScore", 0.173456)
            .put("nearestNeighbors", new JsonArray().add(new JsonObject()
                    .put("configurationHash", "neighbor")
                    .put("distance", 0.5)
                    .put("realScore", 0.2)));

    writer.recordSelectedConfigurations(benchmark, queue(), index,
            of(ConfigurationSelectionDecision.of(index.keyForScenarioName("candidate").orElseThrow(),
                    "knnAdaptive", metadata)),
            "adaptiveSelection", 20, 10);

    var selection = trace(fileProvider).getJsonArray("decisions").getJsonObject(0).getJsonObject("selection");
    assertEquals("knnAdaptive", selection.getString("heuristic"));
    assertEquals(metadata, selection.getJsonObject("metadata"));
  }

  @Test
  void should_keep_trace_valid_without_specific_heuristic_metadata() {
    var fileProvider = new InMemoryFileProvider();
    var keyFactory = new ScenarioCacheKeyFactory();
    var resultCache = new ScenarioResultCache(() -> fileProvider, keyFactory);
    @SuppressWarnings("unchecked")
    var scenarioRepository = (CustomResourceRepository<Scenario>) mock(CustomResourceRepository.class);
    var writer = new HeuristicTraceWriter(() -> fileProvider, keyFactory, resultCache, scenarioRepository,
            new ConfigurationResultAggregator());
    var benchmark = benchmark("randomSampling");
    var candidate = scenario("candidate", 5, 300, 50);
    var index = ScenarioConfigurationIndex.from(of(candidate));

    writer.recordSelectedConfigurations(benchmark, queue(), index,
            of(ConfigurationSelectionDecision.of(index.keyForScenarioName("candidate").orElseThrow(),
                    "randomSampling")),
            "randomSampling", 0, 0);

    var selection = trace(fileProvider).getJsonArray("decisions").getJsonObject(0).getJsonObject("selection");
    assertEquals("randomSampling", selection.getString("heuristic"));
    assertTrue(selection.getJsonObject("metadata").isEmpty());
  }

  private static JsonObject trace(InMemoryFileProvider fileProvider) {
    return new JsonObject(fileProvider.files.entrySet().stream()
            .filter(entry -> entry.getKey().endsWith("/trace.json"))
            .findFirst()
            .orElseThrow()
            .getValue());
  }

  private static Benchmark benchmark(String strategyType) {
    var benchmark = new Benchmark();
    var meta = new ObjectMeta();
    meta.setName("onlineboutique");
    benchmark.setMetadata(meta);
    benchmark.setSpec(new BenchmarkSpec("workload",
            new ScenarioSelectionStrategySpec(strategyType, null, null, null,
                    1, 2, null, 1, 0.1),
            new ResultCacheSpec(true, "readWrite", "/results/cache", "/results/runs"), of()));
    return benchmark;
  }

  private static ExecutionQueue queue() {
    var meta = new ObjectMeta();
    meta.setName("onlineboutique");
    meta.setNamespace("default");
    return new ExecutionQueue(new ExecutionQueueSpec(
            "/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/results.json",
            of(new ExecutionQueueItem("scenario-100-25", "/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/items/scenario-100-25.json"),
                    new ExecutionQueueItem("scenario-300-50", "/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/items/scenario-300-50.json"),
                    new ExecutionQueueItem("candidate", "/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/items/candidate.json")),
            "onlineboutique"), meta);
  }

  private static Scenario scenario(String name, int attempts, int users, int faultPercentage) {
    var connector = new Connector.Builder()
            .name("connector")
            .source(new Service("source", Map.of("GRPC_MAX_ATTEMPTS", attempts)))
            .destination(new Service("destination"))
            .build();
    var scenario = new Scenario();
    scenario.setSpec(new ScenarioSpec(name, new ScenarioWorkload("workload", users), of(connector),
            new ScenarioFault("envoy", faultPercentage, of("destination"))));
    var meta = new ObjectMeta();
    meta.setName(name);
    meta.setAnnotations(Map.of("resiliencebench.io/owned-by", "onlineboutique"));
    scenario.setMetadata(meta);
    return scenario;
  }

  private static class InMemoryFileProvider implements FileProvider {
    private final Map<String, String> files = new HashMap<>();

    @Override
    public void writeToFile(String resultFile, String content) {
      files.put(resultFile, content);
    }

    @Override
    public Optional<String> getFileAsString(String resultFile) {
      return Optional.ofNullable(files.get(resultFile));
    }
  }
}
