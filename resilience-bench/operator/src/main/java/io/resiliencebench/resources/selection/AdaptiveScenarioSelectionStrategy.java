package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.workload.Workload;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AdaptiveScenarioSelectionStrategy extends ScenarioSelectionStrategy {

  Optional<Scenario> selectNextScenario(List<Scenario> allScenarios,
                                        Set<String> alreadyQueuedScenarioNames,
                                        List<EvaluatedScenario> evaluatedScenarios,
                                        Benchmark benchmark,
                                        Workload workload);
}
