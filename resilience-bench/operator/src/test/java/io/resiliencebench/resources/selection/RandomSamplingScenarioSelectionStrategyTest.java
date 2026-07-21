package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.ScenarioFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.ScenarioFaultTemplate;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.benchmark.ScenarioTemplate;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import org.junit.jupiter.api.Test;

import static io.resiliencebench.resources.ScenarioFactoryTest.createConnector;
import static io.resiliencebench.resources.ScenarioFactoryTest.createWorkload;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.*;

class RandomSamplingScenarioSelectionStrategyTest {

  private final RandomSamplingScenarioSelectionStrategy strategy = new RandomSamplingScenarioSelectionStrategy();

  @Test
  void should_select_ceil_of_sample_rate_as_configurations() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", 0.5, null, 42L));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(36, allScenarios.size());
    assertEquals(18, selectedScenarios.size());
    assertEquals(2, ScenarioConfigurationIndex.from(selectedScenarios).totalConfigurations());
  }

  @Test
  void should_select_at_least_one_complete_configuration_when_sample_rate_rounds_below_one() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", 0.01, null, 42L));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(9, selectedScenarios.size());
    assertEquals(1, ScenarioConfigurationIndex.from(selectedScenarios).totalConfigurations());
  }

  @Test
  void should_limit_by_max_configurations() {
    var strategySpec = new ScenarioSelectionStrategySpec("randomSampling", null, null, 42L);
    strategySpec.setMaxConfigurations(2);
    var benchmark = benchmark(strategySpec);
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(18, selectedScenarios.size());
    assertEquals(2, ScenarioConfigurationIndex.from(selectedScenarios).totalConfigurations());
  }

  @Test
  void should_use_max_scenarios_as_legacy_configuration_limit() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", null, 3, 42L));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(27, selectedScenarios.size());
    assertEquals(3, ScenarioConfigurationIndex.from(selectedScenarios).totalConfigurations());
  }

  @Test
  void should_apply_sample_rate_before_max_configurations() {
    var strategySpec = new ScenarioSelectionStrategySpec("randomSampling", 0.5, null, 42L);
    strategySpec.setMaxConfigurations(1);
    var benchmark = benchmark(strategySpec);
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(9, selectedScenarios.size());
    assertEquals(1, ScenarioConfigurationIndex.from(selectedScenarios).totalConfigurations());
  }

  @Test
  void should_select_same_configurations_for_same_seed() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", 0.5, null, 42L));
    var workload = createWorkload(of(10, 20, 30));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var firstSelection = strategy.selectScenarios(allScenarios, benchmark, workload);
    var secondSelection = strategy.selectScenarios(allScenarios, benchmark, workload);

    assertEquals(names(firstSelection), names(secondSelection));
  }

  @Test
  void should_be_able_to_select_different_configurations_for_different_seeds() {
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

    assertEquals(18, selectedScenarios.size());
    assertEquals(2, ScenarioConfigurationIndex.from(selectedScenarios).totalConfigurations());
  }

  private Benchmark benchmark(ScenarioSelectionStrategySpec strategySpec) {
    var benchmark = new Benchmark();
    benchmark.setSpec(new BenchmarkSpec("workload", strategySpec,
            of(new ScenarioTemplate("scenario-1", of(createConnector("connector-1")),
                    new ScenarioFaultTemplate("envoy", of(25, 50, 75), of("destination"))))));
    return benchmark;
  }

  private java.util.List<String> names(java.util.List<io.resiliencebench.resources.scenario.Scenario> scenarios) {
    return scenarios.stream().map(scenario -> scenario.getMetadata().getName()).toList();
  }
}