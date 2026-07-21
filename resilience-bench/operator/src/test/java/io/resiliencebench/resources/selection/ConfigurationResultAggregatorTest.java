package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.ScenarioFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.ScenarioFaultTemplate;
import io.resiliencebench.resources.benchmark.ScenarioTemplate;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static io.resiliencebench.resources.ScenarioFactoryTest.createConnector;
import static io.resiliencebench.resources.ScenarioFactoryTest.createWorkload;
import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationResultAggregatorTest {

  private final ConfigurationResultAggregator aggregator = new ConfigurationResultAggregator();

  @Test
  void should_complete_configuration_only_after_all_contexts_have_results() {
    var benchmark = new Benchmark();
    benchmark.setSpec(new BenchmarkSpec("workload", of(new ScenarioTemplate(
            "scenario-1",
            of(createConnector("connector-1", of(1), of(100))),
            new ScenarioFaultTemplate("envoy", of(25, 50, 75), of("destination"))))));
    var scenarios = ScenarioFactory.create(benchmark, createWorkload(of(300, 500, 700)));
    var index = ScenarioConfigurationIndex.from(scenarios);
    var key = index.keys().get(0);
    var partialResults = index.scenariosFor(key).stream().limit(8)
            .map(scenario -> evaluated(scenario.getMetadata().getName(), 0.9, 100))
            .toList();

    var partial = aggregator.completeEvaluations(index, partialResults);
    assertTrue(partial.isEmpty());

    var completeResults = index.scenariosFor(key).stream()
            .map(scenario -> evaluated(scenario.getMetadata().getName(), 0.9, 100))
            .toList();
    var complete = aggregator.completeEvaluations(index, completeResults);

    assertEquals(1, complete.size());
    assertEquals(9, complete.get(0).getExpectedContexts());
    assertEquals(9, complete.get(0).getCompletedContexts());
    assertEquals(0.9, complete.get(0).getMetrics().getDouble("successRate"), 0.000001);
  }

  private static EvaluatedScenario evaluated(String scenarioName, double successRate, double p95Latency) {
    return new EvaluatedScenario(scenarioName, new JsonObject()
            .put("successRate", successRate)
            .put("p95Latency", p95Latency));
  }
}