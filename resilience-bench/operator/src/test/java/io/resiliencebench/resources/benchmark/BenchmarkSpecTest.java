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
  }
}
