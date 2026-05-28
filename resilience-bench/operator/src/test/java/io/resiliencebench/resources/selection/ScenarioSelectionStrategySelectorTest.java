package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import org.junit.jupiter.api.Test;

import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioSelectionStrategySelectorTest {

  private final ExhaustiveScenarioSelectionStrategy exhaustive = new ExhaustiveScenarioSelectionStrategy();
  private final RandomSamplingScenarioSelectionStrategy randomSampling = new RandomSamplingScenarioSelectionStrategy();
  private final BayesianOptimizationScenarioSelectionStrategy bayesianOptimization = new BayesianOptimizationScenarioSelectionStrategy();
  private final ScenarioSelectionStrategySelector selector = new ScenarioSelectionStrategySelector(exhaustive, randomSampling, bayesianOptimization);

  @Test
  void should_select_exhaustive_by_default_when_strategy_is_absent() {
    var benchmark = benchmark(null);

    var selectedStrategy = selector.select(benchmark);

    assertSame(exhaustive, selectedStrategy);
  }

  @Test
  void should_select_exhaustive_when_type_is_exhaustive() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("exhaustive", null, null, null));

    var selectedStrategy = selector.select(benchmark);

    assertSame(exhaustive, selectedStrategy);
  }

  @Test
  void should_select_random_sampling_when_type_is_random_sampling() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", 0.5, null, 42L));

    var selectedStrategy = selector.select(benchmark);

    assertSame(randomSampling, selectedStrategy);
  }

  @Test
  void should_select_bayesian_optimization_when_type_is_bayesian_optimization() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "bayesianOptimization", null, null, 42L,
            20, 100, "expectedImprovement", null));

    var selectedStrategy = selector.select(benchmark);

    assertSame(bayesianOptimization, selectedStrategy);
  }

  @Test
  void should_fail_for_unknown_strategy_type() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("unknown", null, null, null));

    var exception = assertThrows(IllegalArgumentException.class, () -> selector.select(benchmark));

    assertEquals("Unknown scenario selection strategy type: unknown", exception.getMessage());
  }

  @Test
  void should_fail_for_invalid_sample_rate() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", 0.0, null, null));

    var exception = assertThrows(IllegalArgumentException.class, () -> selector.select(benchmark));

    assertEquals("strategy.sampleRate must be in the range (0, 1]", exception.getMessage());
  }

  @Test
  void should_fail_for_invalid_max_scenarios() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec("randomSampling", null, 0, null));

    var exception = assertThrows(IllegalArgumentException.class, () -> selector.select(benchmark));

    assertEquals("strategy.maxScenarios must be greater than 0", exception.getMessage());
  }

  @Test
  void should_fail_for_invalid_initial_samples() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "bayesianOptimization", null, null, null,
            0, 100, "expectedImprovement", null));

    var exception = assertThrows(IllegalArgumentException.class, () -> selector.select(benchmark));

    assertEquals("strategy.initialSamples must be greater than 0", exception.getMessage());
  }

  @Test
  void should_fail_when_initial_samples_is_greater_than_max_evaluations() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "bayesianOptimization", null, null, null,
            101, 100, "expectedImprovement", null));

    var exception = assertThrows(IllegalArgumentException.class, () -> selector.select(benchmark));

    assertEquals("strategy.initialSamples must be less than or equal to strategy.maxEvaluations", exception.getMessage());
  }

  @Test
  void should_fail_for_unknown_acquisition_function() {
    var benchmark = benchmark(new ScenarioSelectionStrategySpec(
            "bayesianOptimization", null, null, null,
            20, 100, "probabilityOfImprovement", null));

    var exception = assertThrows(IllegalArgumentException.class, () -> selector.select(benchmark));

    assertEquals("strategy.acquisitionFunction must be expectedImprovement", exception.getMessage());
  }

  private Benchmark benchmark(ScenarioSelectionStrategySpec strategySpec) {
    var benchmark = new Benchmark();
    benchmark.setSpec(new BenchmarkSpec("workload", strategySpec, of()));
    return benchmark;
  }
}
