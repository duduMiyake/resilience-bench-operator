package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.ScenarioFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.benchmark.ScenarioTemplate;
import org.junit.jupiter.api.Test;

import static io.resiliencebench.resources.ScenarioFactoryTest.createConnector;
import static io.resiliencebench.resources.ScenarioFactoryTest.createWorkload;
import static java.util.List.of;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BayesianOptimizationScenarioSelectionStrategyTest {

  private final BayesianOptimizationScenarioSelectionStrategy strategy = new BayesianOptimizationScenarioSelectionStrategy();

  @Test
  void should_select_initial_samples_first() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "bayesianOptimization", null, null, 42L,
            2, 5, "expectedImprovement", null));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(12, allScenarios.size());
    assertEquals(2, selectedScenarios.size());
  }

  @Test
  void should_select_same_scenarios_for_same_seed() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "bayesianOptimization", null, null, 42L,
            2, 5, "expectedImprovement", null));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var firstSelection = strategy.selectScenarios(allScenarios, benchmark, workload);
    var secondSelection = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(names(firstSelection), names(secondSelection));
  }

  @Test
  void should_cap_initial_samples_by_max_scenarios_when_max_evaluations_is_absent() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "bayesianOptimization", null, 3, 42L,
            2, null, "expectedImprovement", null));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(2, selectedScenarios.size());
  }

  @Test
  void should_select_next_scenario_from_not_queued_candidates() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "bayesianOptimization", null, null, 42L,
            2, 5, "expectedImprovement", null));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);
    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);
    var queuedNames = selectedScenarios.stream()
            .map(scenario -> scenario.getMetadata().getName())
            .collect(toSet());

    var nextScenario = strategy.selectNextScenario(allScenarios, queuedNames, of(), benchmark, workload);

    assertTrue(nextScenario.isPresent());
    assertTrue(!queuedNames.contains(nextScenario.get().getMetadata().getName()));
  }

  private Benchmark benchmark(ScenarioSelectionStrategySpec strategySpec) {
    var benchmark = new Benchmark();
    benchmark.setSpec(new BenchmarkSpec("workload", strategySpec,
            of(new ScenarioTemplate("scenario-1", of(createConnector("connector-1"))))));
    return benchmark;
  }

  private java.util.List<String> names(java.util.List<io.resiliencebench.resources.scenario.Scenario> scenarios) {
    return scenarios.stream().map(scenario -> scenario.getMetadata().getName()).toList();
  }
}
