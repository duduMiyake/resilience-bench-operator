package io.resiliencebench.execution.resultcache;

import io.resiliencebench.execution.io.FileProvider;
import io.resiliencebench.execution.io.FileProviderFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScenarioResultCacheBackfill {

  private final static Logger logger = LoggerFactory.getLogger(ScenarioResultCacheBackfill.class);

  private final FileProvider fileProvider;
  private final ScenarioCacheKeyFactory cacheKeyFactory;

  public ScenarioResultCacheBackfill(FileProviderFactory fileProviderFactory, ScenarioCacheKeyFactory cacheKeyFactory) {
    this.fileProvider = fileProviderFactory.create();
    this.cacheKeyFactory = cacheKeyFactory;
  }

  public int importAggregatedResults(Benchmark benchmark, String aggregatedResultFile) {
    var content = fileProvider.getFileAsString(aggregatedResultFile);
    if (content.isEmpty()) {
      logger.warn("Aggregated result file {} not found", aggregatedResultFile);
      return 0;
    }

    var results = new JsonObject(content.get()).getJsonArray("results", new JsonArray());
    int imported = 0;
    for (Object item : results) {
      if (!(item instanceof JsonObject result)) {
        continue;
      }
      var scenarioHash = cacheKeyFactory.hashFromResult(result);
      result.put("benchmark", benchmark.getMetadata().getName());
      result.put("scenarioHash", scenarioHash);
      fileProvider.writeToFile(ResultStoragePathFactory.cacheFile(benchmark, scenarioHash), result.encode());
      imported++;
    }
    return imported;
  }
}
