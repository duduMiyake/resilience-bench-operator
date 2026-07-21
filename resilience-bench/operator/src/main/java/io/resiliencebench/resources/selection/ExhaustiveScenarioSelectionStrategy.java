package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.selection.configuration.ResilienceConfigurationKey;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import io.resiliencebench.resources.workload.Workload;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExhaustiveScenarioSelectionStrategy implements ConfigurationSelectionStrategy {

  @Override
  public List<Scenario> selectScenarios(List<Scenario> allScenarios, Benchmark benchmark, Workload workload) {
    return allScenarios;
  }

  @Override
  public List<ResilienceConfigurationKey> selectConfigurations(ScenarioConfigurationIndex configurationIndex,
                                                               Benchmark benchmark,
                                                               Workload workload) {
    return configurationIndex.keys();
  }
}