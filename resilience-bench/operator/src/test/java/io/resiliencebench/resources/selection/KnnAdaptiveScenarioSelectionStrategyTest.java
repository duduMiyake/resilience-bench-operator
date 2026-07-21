package io.resiliencebench.resources.selection;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.scenario.Connector;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.scenario.ScenarioFault;
import io.resiliencebench.resources.scenario.ScenarioSpec;
import io.resiliencebench.resources.scenario.ScenarioWorkload;
import io.resiliencebench.resources.scenario.Service;
import io.resiliencebench.resources.selection.configuration.ResilienceConfigurationKey;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import io.resiliencebench.resources.workload.Workload;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.encode;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnnAdaptiveScenarioSelectionStrategyTest {

  private final KnnAdaptiveScenarioSelectionStrategy strategy = new KnnAdaptiveScenarioSelectionStrategy();

  @Test
  void should_select_initial_configuration_samples_first() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "knnAdaptive", null, null, 42L,
            2, 5, null, 3, 0.1));
    var scenarios = of(
            scenario("s-1", 1),
            scenario("s-2", 2),
            scenario("s-3", 3),
            scenario("s-4", 4));
    var index = ScenarioConfigurationIndex.from(scenarios);

    var selectedConfigurations = strategy.selectConfigurations(index, benchmark, new Workload());

    assertEquals(2, selectedConfigurations.size());
  }

  @Test
  void should_respect_configuration_budget() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "knnAdaptive", null, null, 42L,
            2, 2, null, 3, 0.1));
    var scenarios = of(
            scenario("evaluated-1", 1),
            scenario("evaluated-2", 2),
            scenario("candidate", 3));
    var index = ScenarioConfigurationIndex.from(scenarios);
    var evaluated1 = key(index, "evaluated-1");
    var evaluated2 = key(index, "evaluated-2");

    var nextConfiguration = strategy.selectNextConfiguration(
            index,
            Set.of(evaluated1, evaluated2),
            of(evaluated(evaluated1, 1), evaluated(evaluated2, 2)),
            benchmark,
            new Workload());

    assertTrue(nextConfiguration.isEmpty());
  }

  @Test
  void should_select_configuration_closest_to_high_scoring_neighbor() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "knnAdaptive", null, null, 42L,
            2, 5, null, 1, 0.0));
    var scenarios = of(
            scenario("good", 0),
            scenario("bad", 10),
            scenario("near-good", 1),
            scenario("near-bad", 9));
    var index = ScenarioConfigurationIndex.from(scenarios);
    var good = key(index, "good");
    var bad = key(index, "bad");

    var nextConfiguration = strategy.selectNextConfiguration(
            index,
            Set.of(good, bad),
            of(evaluated(good, 1), evaluated(bad, 0)),
            benchmark,
            new Workload());

    assertTrue(nextConfiguration.isPresent());
    assertEquals(key(index, "near-good"), nextConfiguration.get());
  }

  @Test
  void should_not_select_already_queued_configuration() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "knnAdaptive", null, null, 42L,
            2, 5, null, 1, 0.0));
    var scenarios = of(
            scenario("good", 0),
            scenario("bad", 10),
            scenario("near-good", 1),
            scenario("near-bad", 9));
    var index = ScenarioConfigurationIndex.from(scenarios);
    var good = key(index, "good");
    var bad = key(index, "bad");
    var nearGood = key(index, "near-good");

    var nextConfiguration = strategy.selectNextConfiguration(
            index,
            Set.of(good, bad, nearGood),
            of(evaluated(good, 1), evaluated(bad, 0)),
            benchmark,
            new Workload());

    assertTrue(nextConfiguration.isPresent());
    assertEquals(key(index, "near-bad"), nextConfiguration.get());
  }

  @Test
  void should_use_exploration_weight_as_bonus_for_distant_configurations() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "knnAdaptive", null, null, 42L,
            2, 5, null, 2, 2.0));
    var scenarios = of(
            scenario("good", 0),
            scenario("bad", 10),
            scenario("near-good", 1),
            scenario("middle", 5));
    var index = ScenarioConfigurationIndex.from(scenarios);
    var good = key(index, "good");
    var bad = key(index, "bad");

    var nextConfiguration = strategy.selectNextConfiguration(
            index,
            Set.of(good, bad),
            of(evaluated(good, 1), evaluated(bad, 0)),
            benchmark,
            new Workload());

    assertTrue(nextConfiguration.isPresent());
    assertEquals(key(index, "middle"), nextConfiguration.get());
  }

  @Test
  void should_not_change_feature_vector_when_only_workload_or_fault_changes() {
    var first = scenario("same-config-1", 2, 300, 25);
    var second = scenario("same-config-2", 2, 700, 75);

    var vectors = encode(of(first, second));

    assertEquals(vectors.get("same-config-1"), vectors.get("same-config-2"));
  }

  private static Benchmark benchmark(ScenarioSelectionStrategySpec strategySpec) {
    var benchmark = new Benchmark();
    benchmark.setSpec(new BenchmarkSpec("workload", strategySpec, of()));
    return benchmark;
  }

  private static EvaluatedConfiguration evaluated(ResilienceConfigurationKey key, double successRate) {
    return new EvaluatedConfiguration(key,
            new JsonObject().put("successRate", successRate).put("p95Latency", 0),
            1,
            1);
  }

  private static ResilienceConfigurationKey key(ScenarioConfigurationIndex index, String scenarioName) {
    return index.keyForScenarioName(scenarioName).orElseThrow();
  }

  private static Scenario scenario(String name, int setting) {
    return scenario(name, setting, 10, 50);
  }

  private static Scenario scenario(String name, int setting, int users, int faultPercentage) {
    var connector = new Connector.Builder()
            .name("connector")
            .source(new Service("source", java.util.Map.of("SETTING", setting)))
            .destination(new Service("destination"))
            .build();
    var scenario = new Scenario(new ScenarioSpec(
            name,
            new ScenarioWorkload("workload", users),
            List.of(connector),
            new ScenarioFault("envoy", faultPercentage, List.of("destination"))));
    scenario.setMetadata(new ObjectMetaBuilder().withName(name).build());
    return scenario;
  }
}