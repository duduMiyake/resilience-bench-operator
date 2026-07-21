package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.selection.configuration.ResilienceConfigurationKey;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import io.resiliencebench.resources.workload.Workload;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AdaptiveConfigurationSelectionStrategy extends ConfigurationSelectionStrategy {

  Optional<ResilienceConfigurationKey> selectNextConfiguration(ScenarioConfigurationIndex configurationIndex,
                                                               Set<ResilienceConfigurationKey> alreadyQueuedConfigurations,
                                                               List<EvaluatedConfiguration> evaluatedConfigurations,
                                                               Benchmark benchmark,
                                                               Workload workload);
}
