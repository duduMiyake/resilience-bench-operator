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
import io.resiliencebench.resources.scenario.ScenarioSpec;
import io.resiliencebench.resources.scenario.ScenarioWorkload;
import io.resiliencebench.resources.scenario.Service;
import io.resiliencebench.support.CustomResourceRepository;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeuristicTraceWriterTest {

  @Test
  void should_write_knn_adaptive_selection_details_when_neighbors_are_available() {
    var fileProvider = new InMemoryFileProvider();
    var keyFactory = new ScenarioCacheKeyFactory();
    var resultCache = new ScenarioResultCache(() -> fileProvider, keyFactory);
    @SuppressWarnings("unchecked")
    var scenarioRepository = (CustomResourceRepository<Scenario>) mock(CustomResourceRepository.class);
    var writer = new HeuristicTraceWriter(() -> fileProvider, keyFactory, resultCache, scenarioRepository);
    var benchmark = benchmark();
    var evaluated = scenario("scenario-1", 2);
    var candidate = scenario("scenario-2", 5);
    when(scenarioRepository.list("default")).thenReturn(of(evaluated, candidate));
    fileProvider.writeToFile("/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/results.json",
            new JsonObject().put("results", new JsonArray().add(new JsonObject()
                    .put("scenario", "scenario-1")
                    .put("checkout_success_rate", 0.9)
                    .put("iteration_duration_p95", 0.1))).encode());

    writer.appendStep(benchmark, queue(), candidate, "adaptiveSelection", ScenarioResultCache.CACHE_HIT,
            new JsonObject().put("checkout_success_rate", 0.8).put("iteration_duration_p95", 0.2));

    var trace = new JsonObject(fileProvider.files.get("/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/trace.json"));
    var selection = trace.getJsonArray("steps").getJsonObject(0).getJsonObject("selection");
    assertNotNull(selection);
    assertEquals(1, selection.getJsonArray("nearestNeighbors").size());
    assertEquals("scenario-1", selection.getJsonArray("nearestNeighbors").getJsonObject(0).getString("scenario"));
  }

  private static Benchmark benchmark() {
    var benchmark = new Benchmark();
    var meta = new ObjectMeta();
    meta.setName("onlineboutique");
    benchmark.setMetadata(meta);
    benchmark.setSpec(new BenchmarkSpec("workload",
            new ScenarioSelectionStrategySpec("knnAdaptive", null, null, null,
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
            of(new ExecutionQueueItem("scenario-1", "/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/items/scenario-1.json"),
                    new ExecutionQueueItem("scenario-2", "/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/items/scenario-2.json")),
            "onlineboutique"), meta);
  }

  private static Scenario scenario(String name, int attempts) {
    var connector = new Connector.Builder()
            .name("connector")
            .source(new Service("source", Map.of("GRPC_MAX_ATTEMPTS", attempts)))
            .destination(new Service("destination"))
            .build();
    var scenario = new Scenario();
    scenario.setSpec(new ScenarioSpec(name, new ScenarioWorkload("workload", 300), of(connector)));
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
