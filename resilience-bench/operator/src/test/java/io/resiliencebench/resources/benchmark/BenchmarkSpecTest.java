package io.resiliencebench.resources.benchmark;

import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkSpecTest {

  @Test
  void should_load_strategy_from_yaml() {
    var benchmark = Serialization.unmarshal("""
            apiVersion: resiliencebench.io/v1beta1
            kind: Benchmark
            metadata:
              name: sample
            spec:
              workload: fixed-iterations-loadtest
              strategy:
                type: randomSampling
                sampleRate: 0.5
                maxScenarios: 10
                seed: 42
              scenarios: []
            """, Benchmark.class);

    var strategy = benchmark.getSpec().getStrategy();

    assertNotNull(strategy);
    assertEquals("randomSampling", strategy.getType());
    assertEquals(0.5, strategy.getSampleRate());
    assertEquals(10, strategy.getMaxScenarios());
    assertEquals(42L, strategy.getSeed());
  }

  @Test
  void should_keep_strategy_optional_for_legacy_yaml() {
    var benchmark = Serialization.unmarshal("""
            apiVersion: resiliencebench.io/v1beta1
            kind: Benchmark
            metadata:
              name: sample
            spec:
              workload: fixed-iterations-loadtest
              scenarios: []
            """, Benchmark.class);

    assertNull(benchmark.getSpec().getStrategy());
    assertNull(benchmark.getSpec().getResultCache());
  }

  @Test
  void should_load_knn_adaptive_strategy_from_yaml() {
    var benchmark = Serialization.unmarshal("""
            apiVersion: resiliencebench.io/v1beta1
            kind: Benchmark
            metadata:
              name: sample
            spec:
              workload: fixed-iterations-loadtest
              strategy:
                type: knnAdaptive
                initialSamples: 20
                maxEvaluations: 100
                neighbors: 3
                explorationWeight: 0.1
              scenarios: []
            """, Benchmark.class);

    var strategy = benchmark.getSpec().getStrategy();

    assertNotNull(strategy);
    assertEquals("knnAdaptive", strategy.getType());
    assertEquals(20, strategy.getInitialSamples());
    assertEquals(100, strategy.getMaxEvaluations());
    assertEquals(3, strategy.getNeighbors());
    assertEquals(0.1, strategy.getExplorationWeight());
  }

  @Test
  void should_load_structured_normalized_objective_from_yaml() {
    var benchmark = Serialization.unmarshal("""
            apiVersion: resiliencebench.io/v1beta1
            kind: Benchmark
            metadata:
              name: sample
            spec:
              workload: fixed-iterations-loadtest
              strategy:
                type: knnAdaptive
                objective:
                  metrics:
                    - name: checkout_success_rate
                      direction: maximize
                      weight: 0.5
                      normalization:
                        type: minMax
                        min: 0
                        max: 1
                    - name: iteration_duration_p(95)
                      direction: minimize
                      normalization:
                        type: reciprocal
                        scale: 22450
              scenarios: []
            """, Benchmark.class);

    var metrics = benchmark.getSpec().getStrategy().getObjective().getMetrics();

    assertEquals(2, metrics.size());
    assertEquals("iteration_duration_p(95)", metrics.get(1).getName());
    assertEquals("reciprocal", metrics.get(1).getNormalization().getType());
    assertEquals(22450.0, metrics.get(1).getNormalization().getScale());
  }

  @Test
  void should_load_result_cache_from_yaml() {
    var benchmark = Serialization.unmarshal("""
            apiVersion: resiliencebench.io/v1beta1
            kind: Benchmark
            metadata:
              name: sample
            spec:
              workload: fixed-iterations-loadtest
              resultCache:
                enabled: true
                mode: readWrite
                cachePrefix: /results/cache
                runsPrefix: /results/runs
              scenarios: []
            """, Benchmark.class);

    var resultCache = benchmark.getSpec().getResultCache();

    assertNotNull(resultCache);
    assertTrue(resultCache.isEnabled());
    assertEquals("readWrite", resultCache.getMode());
    assertEquals("/results/cache", resultCache.resolvedCachePrefix());
    assertEquals("/results/runs", resultCache.resolvedRunsPrefix());
  }
}
