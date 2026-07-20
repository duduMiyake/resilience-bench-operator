package io.resiliencebench.execution.resultcache;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.ResultCacheSpec;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.scenario.Connector;
import io.resiliencebench.resources.scenario.IstioPattern;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.scenario.ScenarioSpec;
import io.resiliencebench.resources.scenario.ScenarioWorkload;
import io.resiliencebench.resources.scenario.Service;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ScenarioCacheKeyFactoryTest {

  private final ScenarioCacheKeyFactory keyFactory = new ScenarioCacheKeyFactory();

  @Test
  void should_generate_same_hash_for_same_configuration_even_when_scenario_name_changes() {
    var first = scenario("scenario-a", 300, 3);
    var second = scenario("scenario-b", 300, 3);

    assertEquals(keyFactory.hash(first), keyFactory.hash(second));
  }

  @Test
  void should_change_hash_when_workload_changes() {
    var first = scenario("scenario", 300, 3);
    var second = scenario("scenario", 400, 3);

    assertNotEquals(keyFactory.hash(first), keyFactory.hash(second));
  }

  @Test
  void should_change_hash_when_connector_configuration_changes() {
    var first = scenario("scenario", 300, 3);
    var second = scenario("scenario", 300, 4);

    assertNotEquals(keyFactory.hash(first), keyFactory.hash(second));
  }

  private static Scenario scenario(String name, int users, int maxAttempts) {
    var connector = new Connector.Builder()
            .name("retry-frontend-checkout")
            .source(new Service("frontend", Map.of("GRPC_MAX_ATTEMPTS", maxAttempts)))
            .destination(new Service("checkout"))
            .istio(new IstioPattern(Map.of("attempts", maxAttempts), Map.of(), Map.of()))
            .build();
    var scenario = new Scenario();
    scenario.setSpec(new ScenarioSpec(name, new ScenarioWorkload("k6-loadtest", users), of(connector)));
    var meta = new ObjectMeta();
    meta.setName(name);
    scenario.setMetadata(meta);
    return scenario;
  }
}
