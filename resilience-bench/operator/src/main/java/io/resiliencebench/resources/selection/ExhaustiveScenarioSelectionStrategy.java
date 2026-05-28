package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.workload.Workload;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExhaustiveScenarioSelectionStrategy implements ScenarioSelectionStrategy {

  @Override
  public List<Scenario> selectScenarios(List<Scenario> allScenarios, Benchmark benchmark, Workload workload) {
    return allScenarios;
  }
}
