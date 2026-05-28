package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.workload.Workload;

import java.util.List;

public interface ScenarioSelectionStrategy {

  List<Scenario> selectScenarios(List<Scenario> allScenarios, Benchmark benchmark, Workload workload);
}
