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
import static org.junit.jupiter.api.Assertions.*;

class RandomSamplingScenarioSelectionStrategyTest {

  private final RandomSamplingScenarioSelectionStrategy strategy = new RandomSamplingScenarioSelectionStrategy();

  @Test
  void should_select_ceil_of_sample_rate() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", 0.5, null, 42L));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(12, allScenarios.size());
    assertEquals(6, selectedScenarios.size());
  }

  @Test
  void should_select_at_least_one_scenario_when_sample_rate_rounds_below_one() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", 0.01, null, 42L));
    var workload = createWorkload(of(10));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(1, selectedScenarios.size());
  }

  @Test
  void should_limit_by_max_scenarios() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", null, 3, 42L));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(3, selectedScenarios.size());
  }

  @Test
  void should_apply_sample_rate_before_max_scenarios() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", 0.5, 2, 42L));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(2, selectedScenarios.size());
  }

  @Test
  void should_select_same_scenarios_for_same_seed() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", 0.5, null, 42L));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var firstSelection = strategy.selectScenarios(allScenarios, benchmark, workload);
    var secondSelection = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(names(firstSelection), names(secondSelection));
  }

  @Test
  void should_be_able_to_select_different_scenarios_for_different_seeds() {
    var firstBenchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", 0.5, null, 42L));
    var secondBenchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", 0.5, null, 99L));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(firstBenchmark, workload);

    var firstSelection = strategy.selectScenarios(allScenarios, firstBenchmark, workload);
    var secondSelection = strategy.selectScenarios(allScenarios, secondBenchmark, workload);

    assertNotEquals(names(firstSelection), names(secondSelection));
  }

  @Test
  void should_use_documented_defaults_when_limits_and_seed_are_absent() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", null, null, null));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(6, selectedScenarios.size());
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
