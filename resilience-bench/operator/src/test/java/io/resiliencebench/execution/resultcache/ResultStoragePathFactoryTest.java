package io.resiliencebench.execution.resultcache;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.ResultCacheSpec;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import org.junit.jupiter.api.Test;

import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultStoragePathFactoryTest {

  @Test
  void should_keep_legacy_timestamped_paths_when_cache_is_disabled() {
    var benchmark = benchmark("exhaustive", null);

    assertEquals("/results/run-1-results.json", ResultStoragePathFactory.resultFile(benchmark, "run-1"));
    assertEquals("/results/run-1-scenario-1.json", ResultStoragePathFactory.itemResultFile(benchmark, "run-1", "scenario-1"));
  }

  @Test
  void should_use_exhaustive_run_prefix_when_cache_is_enabled() {
    var benchmark = benchmark("exhaustive", new ResultCacheSpec(true, "readWrite", "/results/cache", "/results/runs"));

    assertEquals("/results/runs/exhaustive/onlineboutique/run-1/results.json",
            ResultStoragePathFactory.resultFile(benchmark, "run-1"));
    assertEquals("/results/runs/exhaustive/onlineboutique/run-1/items/scenario-1.json",
            ResultStoragePathFactory.itemResultFile(benchmark, "run-1", "scenario-1"));
  }

  @Test
  void should_use_heuristic_run_prefix_when_cache_is_enabled() {
    var benchmark = benchmark("knnAdaptive", new ResultCacheSpec(true, "readWrite", "/results/cache", "/results/runs"));

    assertEquals("/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/results.json",
            ResultStoragePathFactory.resultFile(benchmark, "run-1"));
    assertEquals("/results/runs/heuristics/onlineboutique/knnAdaptive/run-1/trace.json",
            ResultStoragePathFactory.traceFile(benchmark, "run-1"));
  }

  @Test
  void should_use_cache_prefix_for_stable_scenario_result() {
    var benchmark = benchmark("knnAdaptive", new ResultCacheSpec(true, "readWrite", "/results/cache", "/results/runs"));

    assertEquals("/results/cache/onlineboutique/hash.json", ResultStoragePathFactory.cacheFile(benchmark, "hash"));
  }

  private static Benchmark benchmark(String strategyType, ResultCacheSpec resultCache) {
    var benchmark = new Benchmark();
    var meta = new ObjectMeta();
    meta.setName("onlineboutique");
    benchmark.setMetadata(meta);
    benchmark.setSpec(new BenchmarkSpec("workload", new ScenarioSelectionStrategySpec(strategyType, null, null, null), resultCache, of()));
    return benchmark;
  }
}
