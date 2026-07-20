package io.resiliencebench.execution.resultcache;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ResultCacheSpec;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.scenario.Scenario;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ResultStoragePathFactory {

  private ResultStoragePathFactory() {
    throw new IllegalStateException("Utility class");
  }

  public static String runId() {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
  }

  public static boolean cacheEnabled(Benchmark benchmark) {
    return benchmark.getSpec().getResultCache() != null && benchmark.getSpec().getResultCache().isEnabled();
  }

  public static String resultFile(Benchmark benchmark, String runId) {
    if (!cacheEnabled(benchmark)) {
      return "/results/%s-results.json".formatted(runId);
    }
    return runBasePath(benchmark, runId) + "/results.json";
  }

  public static String itemResultFile(Benchmark benchmark, String runId, String scenarioName) {
    if (!cacheEnabled(benchmark)) {
      return resultFile(benchmark, runId).replace("-results.json", "-%s.json".formatted(scenarioName));
    }
    return runBasePath(benchmark, runId) + "/items/%s.json".formatted(scenarioName);
  }

  public static String traceFile(Benchmark benchmark, String runId) {
    return runBasePath(benchmark, runId) + "/trace.json";
  }

  public static String cacheFile(Benchmark benchmark, String scenarioHash) {
    var cacheSpec = benchmark.getSpec().getResultCache();
    var prefix = cacheSpec == null ? ResultCacheSpec.DEFAULT_CACHE_PREFIX : cacheSpec.resolvedCachePrefix();
    return "%s/%s/%s.json".formatted(prefix, benchmark.getMetadata().getName(), scenarioHash);
  }

  public static String strategyType(Benchmark benchmark) {
    var strategy = benchmark.getSpec().getStrategy();
    if (strategy == null || strategy.getType() == null || strategy.getType().isBlank()) {
      return ScenarioSelectionStrategySpec.EXHAUSTIVE;
    }
    return strategy.getType();
  }

  private static String runBasePath(Benchmark benchmark, String runId) {
    var cacheSpec = benchmark.getSpec().getResultCache();
    var prefix = cacheSpec == null ? ResultCacheSpec.DEFAULT_RUNS_PREFIX : cacheSpec.resolvedRunsPrefix();
    var benchmarkName = benchmark.getMetadata().getName();
    var strategyType = strategyType(benchmark);
    if (ScenarioSelectionStrategySpec.EXHAUSTIVE.equalsIgnoreCase(strategyType)) {
      return "%s/exhaustive/%s/%s".formatted(prefix, benchmarkName, runId);
    }
    return "%s/heuristics/%s/%s/%s".formatted(prefix, benchmarkName, strategyType, runId);
  }
}
