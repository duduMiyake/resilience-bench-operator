package io.resiliencebench.resources.selection.configuration;

import io.resiliencebench.resources.ScenarioFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.ScenarioFaultTemplate;
import io.resiliencebench.resources.benchmark.ScenarioTemplate;
import org.junit.jupiter.api.Test;

import static io.resiliencebench.resources.ScenarioFactoryTest.createConnector;
import static io.resiliencebench.resources.ScenarioFactoryTest.createWorkload;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScenarioConfigurationIndexTest {

  @Test
  void should_group_all_contexts_by_resilience_configuration() {
    var benchmark = new Benchmark();
    benchmark.setSpec(new BenchmarkSpec("workload", of(new ScenarioTemplate(
            "scenario-1",
            of(createConnector("connector-1")),
            new ScenarioFaultTemplate("envoy", of(25, 50, 75), of("destination"))))));
    var scenarios = ScenarioFactory.create(benchmark, createWorkload(of(300, 500, 700)));

    var index = ScenarioConfigurationIndex.from(scenarios);

    assertEquals(36, scenarios.size());
    assertEquals(4, index.totalConfigurations());
    assertEquals(36, index.totalScenarios());
    index.keys().forEach(key -> assertEquals(9, index.scenariosFor(key).size()));
  }
}