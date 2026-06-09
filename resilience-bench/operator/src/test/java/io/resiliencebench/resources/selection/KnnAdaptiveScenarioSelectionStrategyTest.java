package io.resiliencebench.resources.selection;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.scenario.Connector;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.scenario.ScenarioSpec;
import io.resiliencebench.resources.scenario.ScenarioWorkload;
import io.resiliencebench.resources.scenario.Service;
import io.resiliencebench.resources.workload.Workload;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnnAdaptiveScenarioSelectionStrategyTest {

  private final KnnAdaptiveScenarioSelectionStrategy strategy = new KnnAdaptiveScenarioSelectionStrategy();

  @Test
  void should_select_initial_samples_first() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "knnAdaptive", null, null, 42L,
            2, 5, null, 3, 0.1));
    var scenarios = of(
            scenario("s-1", 1),
            scenario("s-2", 2),
            scenario("s-3", 3),
            scenario("s-4", 4));

    var selectedScenarios = strategy.selectScenarios(scenarios, benchmark, new Workload());

    assertEquals(2, selectedScenarios.size());
  }

  @Test
  void should_respect_evaluation_budget() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "knnAdaptive", null, null, 42L,
            2, 2, null, 3, 0.1));
    var scenarios = of(
            scenario("evaluated-1", 1),
            scenario("evaluated-2", 2),
            scenario("candidate", 3));

    var nextScenario = strategy.selectNextScenario(
            scenarios,
            Set.of("evaluated-1", "evaluated-2"),
            of(evaluated("evaluated-1", 1), evaluated("evaluated-2", 2)),
            benchmark,
            new Workload());

    assertTrue(nextScenario.isEmpty());
  }

  @Test
  void should_select_candidate_closest_to_high_scoring_neighbor() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "knnAdaptive", null, null, 42L,
            2, 5, null, 1, 0.0));
    var scenarios = of(
            scenario("good", 0),
            scenario("bad", 10),
            scenario("near-good", 1),
            scenario("near-bad", 9));

    var nextScenario = strategy.selectNextScenario(
            scenarios,
            Set.of("good", "bad"),
            of(evaluated("good", 1), evaluated("bad", 0)),
            benchmark,
            new Workload());

    assertTrue(nextScenario.isPresent());
    assertEquals("near-good", nextScenario.get().getMetadata().getName());
  }

  @Test
  void should_not_select_already_queued_candidate() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "knnAdaptive", null, null, 42L,
            2, 5, null, 1, 0.0));
    var scenarios = of(
            scenario("good", 0),
            scenario("bad", 10),
            scenario("near-good", 1),
            scenario("near-bad", 9));

    var nextScenario = strategy.selectNextScenario(
            scenarios,
            Set.of("good", "bad", "near-good"),
            of(evaluated("good", 1), evaluated("bad", 0)),
            benchmark,
            new Workload());

    assertTrue(nextScenario.isPresent());
    assertEquals("near-bad", nextScenario.get().getMetadata().getName());
  }

  @Test
  void should_use_exploration_weight_as_bonus_for_distant_candidates() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "knnAdaptive", null, null, 42L,
            2, 5, null, 2, 2.0));
    var scenarios = of(
            scenario("good", 0),
            scenario("bad", 10),
            scenario("near-good", 1),
            scenario("middle", 5));

    var nextScenario = strategy.selectNextScenario(
            scenarios,
            Set.of("good", "bad"),
            of(evaluated("good", 1), evaluated("bad", 0)),
            benchmark,
            new Workload());

    assertTrue(nextScenario.isPresent());
    assertEquals("middle", nextScenario.get().getMetadata().getName());
  }

  private static Benchmark benchmark(ScenarioSelectionStrategySpec strategySpec) {
    var benchmark = new Benchmark();
    benchmark.setSpec(new BenchmarkSpec("workload", strategySpec, of()));
    return benchmark;
  }

  private static EvaluatedScenario evaluated(String scenarioName, double successRate) {
    return new EvaluatedScenario(scenarioName, new JsonObject().put("successRate", successRate).put("p95Latency", 0));
  }

  private static Scenario scenario(String name, int setting) {
    var connector = new Connector.Builder()
            .name("connector")
            .source(new Service("source", java.util.Map.of("SETTING", setting)))
            .destination(new Service("destination"))
            .build();
    var scenario = new Scenario(new ScenarioSpec(
            name,
            new ScenarioWorkload("workload", 10),
            List.of(connector)));
    scenario.setMetadata(new ObjectMetaBuilder().withName(name).build());
    return scenario;
  }
}
