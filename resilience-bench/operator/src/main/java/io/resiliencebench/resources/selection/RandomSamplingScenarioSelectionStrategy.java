package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.workload.Workload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class RandomSamplingScenarioSelectionStrategy implements ScenarioSelectionStrategy {

  @Override
  public List<Scenario> selectScenarios(List<Scenario> allScenarios, Benchmark benchmark, Workload workload) {
    if (allScenarios.isEmpty()) {
      return List.of();
    }

    var strategy = benchmark.getSpec().getStrategy();
    var seed = strategy.getSeed() == null ? ScenarioSelectionStrategySpec.DEFAULT_SEED : strategy.getSeed();
    var sampleRate = strategy.getSampleRate();
    var maxScenarios = strategy.getMaxScenarios();

    if (sampleRate == null && maxScenarios == null) {
      sampleRate = ScenarioSelectionStrategySpec.DEFAULT_SAMPLE_RATE;
    }

    var sampleSize = allScenarios.size();
    if (sampleRate != null) {
      sampleSize = (int) Math.ceil(allScenarios.size() * sampleRate);
    }
    if (maxScenarios != null) {
      sampleSize = Math.min(sampleSize, maxScenarios);
    }
    sampleSize = Math.max(1, Math.min(sampleSize, allScenarios.size()));

    var shuffledScenarios = new ArrayList<>(allScenarios);
    java.util.Collections.shuffle(shuffledScenarios, new Random(seed));
    return List.copyOf(shuffledScenarios.subList(0, sampleSize));
  }
}
