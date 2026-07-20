package io.resiliencebench.execution.resultcache;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.ResultCacheSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ScenarioResultCacheBackfillRunner implements ApplicationRunner {

  private final static Logger logger = LoggerFactory.getLogger(ScenarioResultCacheBackfillRunner.class);

  private final Environment environment;
  private final ScenarioResultCacheBackfill backfill;

  public ScenarioResultCacheBackfillRunner(Environment environment, ScenarioResultCacheBackfill backfill) {
    this.environment = environment;
    this.backfill = backfill;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!Boolean.parseBoolean(environment.getProperty("RESULT_CACHE_BACKFILL_ENABLED", "false"))) {
      return;
    }

    var benchmarkName = requiredProperty("RESULT_CACHE_BACKFILL_BENCHMARK");
    var sourceFile = requiredProperty("RESULT_CACHE_BACKFILL_FILE");
    var cachePrefix = environment.getProperty("RESULT_CACHE_BACKFILL_CACHE_PREFIX", ResultCacheSpec.DEFAULT_CACHE_PREFIX);
    var runsPrefix = environment.getProperty("RESULT_CACHE_BACKFILL_RUNS_PREFIX", ResultCacheSpec.DEFAULT_RUNS_PREFIX);

    var benchmark = benchmark(benchmarkName, cachePrefix, runsPrefix);
    logger.info("Starting result cache backfill for benchmark {} from {}", benchmarkName, sourceFile);
    var imported = backfill.importAggregatedResults(benchmark, sourceFile);
    logger.info("Finished result cache backfill for benchmark {}. Imported {} results.", benchmarkName, imported);
  }

  private String requiredProperty(String name) {
    var value = environment.getProperty(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must be set when RESULT_CACHE_BACKFILL_ENABLED=true");
    }
    return value;
  }

  private static Benchmark benchmark(String benchmarkName, String cachePrefix, String runsPrefix) {
    var benchmark = new Benchmark();
    var metadata = new ObjectMeta();
    metadata.setName(benchmarkName);
    benchmark.setMetadata(metadata);
    benchmark.setSpec(new BenchmarkSpec(
            null,
            null,
            new ResultCacheSpec(true, ResultCacheSpec.READ_WRITE, cachePrefix, runsPrefix),
            List.of()));
    return benchmark;
  }
}
