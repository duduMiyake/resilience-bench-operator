package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ResultCacheSpec;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ScenarioSelectionStrategySelector {

  private final ExhaustiveScenarioSelectionStrategy exhaustiveScenarioSelectionStrategy;
  private final RandomSamplingScenarioSelectionStrategy randomSamplingScenarioSelectionStrategy;
  private final KnnAdaptiveScenarioSelectionStrategy knnAdaptiveScenarioSelectionStrategy;

  public ScenarioSelectionStrategySelector(ExhaustiveScenarioSelectionStrategy exhaustiveScenarioSelectionStrategy,
                                           RandomSamplingScenarioSelectionStrategy randomSamplingScenarioSelectionStrategy,
                                           KnnAdaptiveScenarioSelectionStrategy knnAdaptiveScenarioSelectionStrategy) {
    this.exhaustiveScenarioSelectionStrategy = exhaustiveScenarioSelectionStrategy;
    this.randomSamplingScenarioSelectionStrategy = randomSamplingScenarioSelectionStrategy;
    this.knnAdaptiveScenarioSelectionStrategy = knnAdaptiveScenarioSelectionStrategy;
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

    if (ScenarioSelectionStrategySpec.KNN_ADAPTIVE.equalsIgnoreCase(strategy.getType())) {
      return knnAdaptiveScenarioSelectionStrategy;
    }

    throw new IllegalArgumentException("Unknown scenario selection strategy type: " + strategy.getType());
  }

  public Optional<AdaptiveConfigurationSelectionStrategy> selectAdaptive(Benchmark benchmark) {
    validate(benchmark);

    var strategy = benchmark.getSpec().getStrategy();
    if (strategy != null
            && ScenarioSelectionStrategySpec.KNN_ADAPTIVE.equalsIgnoreCase(strategy.getType())) {
      return Optional.of(knnAdaptiveScenarioSelectionStrategy);
    }
    return Optional.empty();
  }

  public void validate(Benchmark benchmark) {
    validateResultCache(benchmark);
    var strategy = benchmark.getSpec().getStrategy();
    if (strategy == null) {
      return;
    }

    var type = strategy.getType();
    if (type != null && !type.isBlank()
            && !ScenarioSelectionStrategySpec.EXHAUSTIVE.equalsIgnoreCase(type)
            && !ScenarioSelectionStrategySpec.RANDOM_SAMPLING.equalsIgnoreCase(type)
            && !ScenarioSelectionStrategySpec.KNN_ADAPTIVE.equalsIgnoreCase(type)) {
      throw new IllegalArgumentException("Unknown scenario selection strategy type: " + type);
    }

    if (strategy.getSampleRate() != null
            && (strategy.getSampleRate() <= 0 || strategy.getSampleRate() > 1)) {
      throw new IllegalArgumentException("strategy.sampleRate must be in the range (0, 1]");
    }

    if (strategy.getMaxScenarios() != null && strategy.getMaxScenarios() <= 0) {
      throw new IllegalArgumentException("strategy.maxScenarios must be greater than 0");
    }

    if (strategy.getMaxConfigurations() != null && strategy.getMaxConfigurations() <= 0) {
      throw new IllegalArgumentException("strategy.maxConfigurations must be greater than 0");
    }

    if (strategy.getInitialSamples() != null && strategy.getInitialSamples() <= 0) {
      throw new IllegalArgumentException("strategy.initialSamples must be greater than 0");
    }

    if (strategy.getMaxEvaluations() != null && strategy.getMaxEvaluations() <= 0) {
      throw new IllegalArgumentException("strategy.maxEvaluations must be greater than 0");
    }

    if (strategy.getInitialSamples() != null && strategy.getMaxConfigurations() != null
            && strategy.getInitialSamples() > strategy.getMaxConfigurations()) {
      throw new IllegalArgumentException("strategy.initialSamples must be less than or equal to strategy.maxConfigurations");
    }

    if (strategy.getInitialSamples() != null && strategy.getMaxConfigurations() == null
            && strategy.getMaxEvaluations() != null
            && strategy.getInitialSamples() > strategy.getMaxEvaluations()) {
      throw new IllegalArgumentException("strategy.initialSamples must be less than or equal to strategy.maxEvaluations");
    }

    if (strategy.getNeighbors() != null && strategy.getNeighbors() <= 0) {
      throw new IllegalArgumentException("strategy.neighbors must be greater than 0");
    }

    if (strategy.getExplorationWeight() != null && strategy.getExplorationWeight() < 0) {
      throw new IllegalArgumentException("strategy.explorationWeight must be greater than or equal to 0");
    }
  }

  private static void validateResultCache(Benchmark benchmark) {
    var resultCache = benchmark.getSpec().getResultCache();
    if (resultCache == null || !resultCache.isEnabled()) {
      return;
    }
    if (!ResultCacheSpec.READ_WRITE.equalsIgnoreCase(resultCache.resolvedMode())) {
      throw new IllegalArgumentException("resultCache.mode must be readWrite");
    }
  }
}