package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ScenarioSelectionStrategySelector {

  private final ExhaustiveScenarioSelectionStrategy exhaustiveScenarioSelectionStrategy;
  private final RandomSamplingScenarioSelectionStrategy randomSamplingScenarioSelectionStrategy;
  private final BayesianOptimizationScenarioSelectionStrategy bayesianOptimizationScenarioSelectionStrategy;

  public ScenarioSelectionStrategySelector(ExhaustiveScenarioSelectionStrategy exhaustiveScenarioSelectionStrategy,
                                           RandomSamplingScenarioSelectionStrategy randomSamplingScenarioSelectionStrategy,
                                           BayesianOptimizationScenarioSelectionStrategy bayesianOptimizationScenarioSelectionStrategy) {
    this.exhaustiveScenarioSelectionStrategy = exhaustiveScenarioSelectionStrategy;
    this.randomSamplingScenarioSelectionStrategy = randomSamplingScenarioSelectionStrategy;
    this.bayesianOptimizationScenarioSelectionStrategy = bayesianOptimizationScenarioSelectionStrategy;
  }

  public ScenarioSelectionStrategy select(Benchmark benchmark) {
    validate(benchmark);

    var strategy = benchmark.getSpec().getStrategy();
    if (strategy == null || strategy.getType() == null || strategy.getType().isBlank()
            || ScenarioSelectionStrategySpec.EXHAUSTIVE.equalsIgnoreCase(strategy.getType())) {
      return exhaustiveScenarioSelectionStrategy;
    }

    if (ScenarioSelectionStrategySpec.RANDOM_SAMPLING.equalsIgnoreCase(strategy.getType())) {
      return randomSamplingScenarioSelectionStrategy;
    }

    if (ScenarioSelectionStrategySpec.BAYESIAN_OPTIMIZATION.equalsIgnoreCase(strategy.getType())) {
      return bayesianOptimizationScenarioSelectionStrategy;
    }

    throw new IllegalArgumentException("Unknown scenario selection strategy type: " + strategy.getType());
  }

  public Optional<AdaptiveScenarioSelectionStrategy> selectAdaptive(Benchmark benchmark) {
    validate(benchmark);

    var strategy = benchmark.getSpec().getStrategy();
    if (strategy != null
            && ScenarioSelectionStrategySpec.BAYESIAN_OPTIMIZATION.equalsIgnoreCase(strategy.getType())) {
      return Optional.of(bayesianOptimizationScenarioSelectionStrategy);
    }
    return Optional.empty();
  }

  public void validate(Benchmark benchmark) {
    var strategy = benchmark.getSpec().getStrategy();
    if (strategy == null) {
      return;
    }

    var type = strategy.getType();
    if (type != null && !type.isBlank()
            && !ScenarioSelectionStrategySpec.EXHAUSTIVE.equalsIgnoreCase(type)
            && !ScenarioSelectionStrategySpec.RANDOM_SAMPLING.equalsIgnoreCase(type)
            && !ScenarioSelectionStrategySpec.BAYESIAN_OPTIMIZATION.equalsIgnoreCase(type)) {
      throw new IllegalArgumentException("Unknown scenario selection strategy type: " + type);
    }

    if (strategy.getSampleRate() != null
            && (strategy.getSampleRate() <= 0 || strategy.getSampleRate() > 1)) {
      throw new IllegalArgumentException("strategy.sampleRate must be in the range (0, 1]");
    }

    if (strategy.getMaxScenarios() != null && strategy.getMaxScenarios() <= 0) {
      throw new IllegalArgumentException("strategy.maxScenarios must be greater than 0");
    }

    if (strategy.getInitialSamples() != null && strategy.getInitialSamples() <= 0) {
      throw new IllegalArgumentException("strategy.initialSamples must be greater than 0");
    }

    if (strategy.getMaxEvaluations() != null && strategy.getMaxEvaluations() <= 0) {
      throw new IllegalArgumentException("strategy.maxEvaluations must be greater than 0");
    }

    if (strategy.getInitialSamples() != null && strategy.getMaxEvaluations() != null
            && strategy.getInitialSamples() > strategy.getMaxEvaluations()) {
      throw new IllegalArgumentException("strategy.initialSamples must be less than or equal to strategy.maxEvaluations");
    }

    if (strategy.getAcquisitionFunction() != null
            && !ScenarioSelectionStrategySpec.EXPECTED_IMPROVEMENT.equalsIgnoreCase(strategy.getAcquisitionFunction())) {
      throw new IllegalArgumentException("strategy.acquisitionFunction must be expectedImprovement");
    }
  }
}
