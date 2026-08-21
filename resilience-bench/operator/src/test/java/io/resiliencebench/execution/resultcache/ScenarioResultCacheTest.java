package io.resiliencebench.execution.resultcache;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.resiliencebench.execution.io.FileProvider;
import io.resiliencebench.execution.io.FileProviderFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.NormalizationSpec;
import io.resiliencebench.resources.benchmark.ObjectiveMetricSpec;
import io.resiliencebench.resources.benchmark.ObjectiveSpec;
import io.resiliencebench.resources.benchmark.ResultCacheSpec;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.queue.ExecutionQueueItem;
import io.resiliencebench.resources.queue.ExecutionQueueSpec;
import io.resiliencebench.resources.scenario.Connector;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.scenario.ScenarioSpec;
import io.resiliencebench.resources.scenario.ScenarioWorkload;
import io.resiliencebench.resources.scenario.Service;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioResultCacheTest {

  @Test
  void should_read_cache_hit_and_append_result_to_current_run() {
    var fileProvider = new InMemoryFileProvider();
    var keyFactory = new ScenarioCacheKeyFactory();
    var cache = new ScenarioResultCache(() -> fileProvider, keyFactory);
    var benchmark = benchmark();
    var scenario = scenario();
    var cached = new JsonObject().put("checkout_success_rate", 0.9).put("iteration_duration_p95", 0.1);
    fileProvider.writeToFile(ResultStoragePathFactory.cacheFile(benchmark, keyFactory.hash(scenario)), cached.encode());

    var hit = cache.get(benchmark, scenario);
    var queue = queue();
    var enriched = cache.enrichResult(hit.orElseThrow(), benchmark, scenario, ScenarioResultCache.CACHE_HIT);
    cache.writeItemResult(queue, scenario, enriched);
    cache.appendToRun(queue, enriched);

    assertTrue(hit.isPresent());
    assertTrue(fileProvider.files.containsKey("/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/items/scenario-1.json"));
    var run = new JsonObject(fileProvider.files.get("/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/results.json"));
    assertEquals(1, run.getJsonArray("results").size());
    assertEquals("cacheHit", run.getJsonArray("results").getJsonObject(0).getString("resultSource"));
  }

  @Test
  void should_save_executed_result_to_cache() {
    var fileProvider = new InMemoryFileProvider();
    var keyFactory = new ScenarioCacheKeyFactory();
    var cache = new ScenarioResultCache(() -> fileProvider, keyFactory);
    var benchmark = benchmark();
    var scenario = scenario();
    var result = cache.enrichResult(new JsonObject().put("successRate", 1.0), benchmark, scenario, ScenarioResultCache.EXECUTED);

    cache.saveToCache(benchmark, scenario, result);

    assertTrue(fileProvider.files.containsKey(ResultStoragePathFactory.cacheFile(benchmark, keyFactory.hash(scenario))));
  }

  @Test
  void result_score_uses_the_configured_objective_definition() {
    var cache = new ScenarioResultCache(InMemoryFileProvider::new, new ScenarioCacheKeyFactory());
    var benchmark = benchmark(ObjectiveSpec.structured(of(
            new ObjectiveMetricSpec("checkout_success_rate", "maximize", 0.5,
                    new NormalizationSpec("minMax", 0.0, 1.0, null)),
            new ObjectiveMetricSpec("iteration_duration_p(95)", "minimize", 0.5,
                    new NormalizationSpec("reciprocal", null, null, 22450.0)))));

    var result = cache.resultScore(benchmark, new JsonObject()
            .put("checkout_success_rate", 0.9)
            .put("iteration_duration_p(95)", 22450));

    assertEquals(0.7, result);
  }

  private static Benchmark benchmark() {
    return benchmark(null);
  }

  private static Benchmark benchmark(ObjectiveSpec objective) {
    var benchmark = new Benchmark();
    var meta = new ObjectMeta();
    meta.setName("onlineboutique");
    benchmark.setMetadata(meta);
    benchmark.setSpec(new BenchmarkSpec("workload",
            new ScenarioSelectionStrategySpec("knnAdaptive", null, null, null,
                    null, null, objective),
            new ResultCacheSpec(true, "readWrite", "/results/cache", "/results/runs"), of()));
    return benchmark;
  }

  private static ExecutionQueue queue() {
    var meta = new ObjectMeta();
    meta.setName("onlineboutique");
    meta.setNamespace("default");
    return new ExecutionQueue(new ExecutionQueueSpec(
            "/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/results.json",
            of(new ExecutionQueueItem("scenario-1", "/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/items/scenario-1.json")),
            "onlineboutique"), meta);
  }

  private static Scenario scenario() {
    var connector = new Connector.Builder()
            .name("connector")
            .source(new Service("source", Map.of("GRPC_MAX_ATTEMPTS", 3)))
            .destination(new Service("destination"))
            .build();
    var scenario = new Scenario();
    scenario.setSpec(new ScenarioSpec("scenario-1", new ScenarioWorkload("workload", 300), of(connector)));
    var meta = new ObjectMeta();
    meta.setName("scenario-1");
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

