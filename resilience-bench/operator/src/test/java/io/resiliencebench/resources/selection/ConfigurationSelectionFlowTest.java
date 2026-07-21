package io.resiliencebench.resources.selection;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.resiliencebench.resources.ExecutionQueueFactory;
import io.resiliencebench.resources.ScenarioFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.ScenarioFaultTemplate;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.benchmark.ScenarioTemplate;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import org.junit.jupiter.api.Test;

import static io.resiliencebench.resources.ScenarioFactoryTest.createConnector;
import static io.resiliencebench.resources.ScenarioFactoryTest.createWorkload;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigurationSelectionFlowTest {

  @Test
  void should_expand_each_selected_configuration_to_all_contexts_before_creating_queue() {
    var strategySpec = new ScenarioSelectionStrategySpec("randomSampling", null, null, 42L);
    strategySpec.setMaxConfigurations(2);
    var benchmark = benchmark(strategySpec);
    var workload = createWorkload(of(300, 500, 700));
    var allScenarios = ScenarioFactory.create(benchmark, workload);
    var strategy = new RandomSamplingScenarioSelectionStrategy();

    var selectedScenarios = strategy.selectScenarios(allScenarios, benchmark, workload);
    var selectedIndex = ScenarioConfigurationIndex.from(selectedScenarios);
    var queue = ExecutionQueueFactory.create(benchmark, selectedScenarios);

    assertEquals(2, selectedIndex.totalConfigurations());
    assertEquals(18, selectedScenarios.size());
    assertEquals(18, queue.getSpec().getItems().size());
    selectedIndex.keys().forEach(key -> assertEquals(9, selectedIndex.scenariosFor(key).size()));
  }

  private static Benchmark benchmark(ScenarioSelectionStrategySpec strategySpec) {
    var benchmark = new Benchmark();
    var metadata = new ObjectMeta();
    metadata.setName("benchmark");
    metadata.setNamespace("namespace");
    benchmark.setMetadata(metadata);
    benchmark.setSpec(new BenchmarkSpec("workload", strategySpec,
            of(new ScenarioTemplate("scenario-1", of(createConnector("connector-1")),
                    new ScenarioFaultTemplate("envoy", of(25, 50, 75), of("destination"))))));
    return benchmark;
  }
}