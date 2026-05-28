package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.ScenarioFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.ScenarioTemplate;
import org.junit.jupiter.api.Test;

import static io.resiliencebench.resources.ScenarioFactoryTest.createConnector;
import static io.resiliencebench.resources.ScenarioFactoryTest.createWorkload;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExhaustiveScenarioSelectionStrategyTest {

  @Test
  void should_return_all_generated_scenarios() {
    var benchmark = new Benchmark();
    benchmark.setSpec(new BenchmarkSpec("workload", of(new ScenarioTemplate("scenario-1", of(createConnector("connector-1"))))));
    var workload = createWorkload(of(10));
    var allScenarios = ScenarioFactory.create(benchmark, workload);

    var selectedScenarios = new ExhaustiveScenarioSelectionStrategy()
            .selectScenarios(allScenarios, benchmark, workload);

    assertSame(allScenarios, selectedScenarios);
  }
}
