package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.selection.configuration.ResilienceConfigurationKey;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import io.resiliencebench.resources.workload.Workload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class RandomSamplingScenarioSelectionStrategy implements ConfigurationSelectionStrategy {

  @Override
  public List<ResilienceConfigurationKey> selectConfigurations(ScenarioConfigurationIndex configurationIndex,
                                                               Benchmark benchmark,
                                                               Workload workload) {
    var allConfigurations = configurationIndex.keys();
    if (allConfigurations.isEmpty()) {
      return List.of();
    }

    var strategy = benchmark.getSpec().getStrategy();
    var seed = strategy.getSeed() == null ? ScenarioSelectionStrategySpec.DEFAULT_SEED : strategy.getSeed();
    var sampleRate = strategy.getSampleRate();
    var maxConfigurations = strategy.getMaxConfigurations() == null
            ? strategy.getMaxScenarios()
            : strategy.getMaxConfigurations();

    if (sampleRate == null && maxConfigurations == null) {
      sampleRate = ScenarioSelectionStrategySpec.DEFAULT_SAMPLE_RATE;
    }

    var sampleSize = allConfigurations.size();
    if (sampleRate != null) {
      sampleSize = (int) Math.ceil(allConfigurations.size() * sampleRate);
    }
    if (maxConfigurations != null) {
      sampleSize = Math.min(sampleSize, maxConfigurations);
    }
    sampleSize = Math.max(1, Math.min(sampleSize, allConfigurations.size()));

    var shuffledConfigurations = new ArrayList<>(allConfigurations);
    java.util.Collections.shuffle(shuffledConfigurations, new Random(seed));
    return List.copyOf(shuffledConfigurations.subList(0, sampleSize));
  }
}