package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.selection.configuration.ResilienceConfigurationKey;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import io.resiliencebench.resources.workload.Workload;

import java.util.List;

public interface ConfigurationSelectionStrategy extends ScenarioSelectionStrategy {

  List<ResilienceConfigurationKey> selectConfigurations(ScenarioConfigurationIndex configurationIndex,
                                                        Benchmark benchmark,
                                                        Workload workload);

  @Override
  default List<Scenario> selectScenarios(List<Scenario> allScenarios, Benchmark benchmark, Workload workload) {
    var configurationIndex = ScenarioConfigurationIndex.from(allScenarios);
    return configurationIndex.expand(selectConfigurations(configurationIndex, benchmark, workload));
  }
}
