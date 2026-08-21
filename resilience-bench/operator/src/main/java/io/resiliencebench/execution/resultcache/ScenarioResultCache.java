package io.resiliencebench.execution.resultcache;

import io.resiliencebench.execution.io.FileProvider;
import io.resiliencebench.execution.io.FileProviderFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ResultCacheSpec;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.selection.ObjectiveScorer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ScenarioResultCache {

  public static final String CACHE_HIT = "cacheHit";
  public static final String EXECUTED = "executed";

  private final FileProvider fileProvider;
  private final ScenarioCacheKeyFactory cacheKeyFactory;

  public ScenarioResultCache(FileProviderFactory fileProviderFactory, ScenarioCacheKeyFactory cacheKeyFactory) {
    this.fileProvider = fileProviderFactory.create();
    this.cacheKeyFactory = cacheKeyFactory;
  }

  public boolean isReadWriteEnabled(Benchmark benchmark) {
    var resultCache = benchmark.getSpec().getResultCache();
    return resultCache != null
            && resultCache.isEnabled()
            && ResultCacheSpec.READ_WRITE.equalsIgnoreCase(resultCache.resolvedMode());
  }

  public String scenarioHash(Scenario scenario) {
    return cacheKeyFactory.hash(scenario);
  }

  public Optional<JsonObject> get(Benchmark benchmark, Scenario scenario) {
    if (!isReadWriteEnabled(benchmark)) {
      return Optional.empty();
    }
    return fileProvider.getFileAsString(ResultStoragePathFactory.cacheFile(benchmark, scenarioHash(scenario)))
            .map(JsonObject::new);
  }

  public JsonObject enrichResult(JsonObject rawResult, Benchmark benchmark, Scenario scenario, String source) {
    var result = rawResult.copy();
    var scenarioHash = scenarioHash(scenario);
    result.put("benchmark", benchmark.getMetadata().getName());
    result.put("scenario", scenario.getMetadata().getName());
    result.put("scenarioHash", scenarioHash);
    result.put("resultSource", source);

    if (scenario.getSpec().getFault() != null) {
      for (var json : scenario.getSpec().getFault().toJson()) {
        result.put(json.getKey(), json.getValue());
      }
    }
    result.put("workload_name", scenario.getSpec().getWorkload().getWorkloadName());
    result.put("workload_users", scenario.getSpec().getWorkload().getUsers());
    result.put("connectors", scenario.getSpec().toConnectorsInJson());
    return result;
  }

  public JsonObject appendToRun(ExecutionQueue queue, JsonObject result) {
    var resultsJson = getRunResults(queue);
    resultsJson.getJsonArray("results").add(result);
    fileProvider.writeToFile(queue.getSpec().getResultFile(), resultsJson.encode());
    return resultsJson;
  }

  public void writeItemResult(ExecutionQueue queue, Scenario scenario, JsonObject result) {
    var item = queue.getItem(scenario.getMetadata().getName());
    if (item != null) {
      fileProvider.writeToFile(item.getResultFile(), result.encode());
    }
  }

  public void saveToCache(Benchmark benchmark, Scenario scenario, JsonObject result) {
    if (!isReadWriteEnabled(benchmark)) {
      return;
    }
    fileProvider.writeToFile(ResultStoragePathFactory.cacheFile(benchmark, scenarioHash(scenario)), result.encode());
  }

  public JsonObject getRunResults(ExecutionQueue queue) {
    return fileProvider.getFileAsString(queue.getSpec().getResultFile())
            .map(JsonObject::new)
            .orElseGet(() -> new JsonObject().put("results", new JsonArray()));
  }

  public double resultScore(Benchmark benchmark, JsonObject metrics) {
    return ObjectiveScorer.score(metrics, benchmark);
  }
}
