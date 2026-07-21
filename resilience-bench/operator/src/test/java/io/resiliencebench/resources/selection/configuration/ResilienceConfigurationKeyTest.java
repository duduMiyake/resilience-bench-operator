package io.resiliencebench.resources.selection.configuration;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.resiliencebench.resources.scenario.Connector;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.scenario.ScenarioFault;
import io.resiliencebench.resources.scenario.ScenarioSpec;
import io.resiliencebench.resources.scenario.ScenarioWorkload;
import io.resiliencebench.resources.scenario.Service;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ResilienceConfigurationKeyTest {

  @Test
  void should_ignore_workload_and_scenario_fault_context() {
    var first = ResilienceConfigurationKey.from(scenario("s1", 300, 25, connector("c1", "2", "0.5s")));
    var second = ResilienceConfigurationKey.from(scenario("s2", 700, 75, connector("c1", "2", "0.5s")));

    assertEquals(first, second);
  }

  @Test
  void should_distinguish_retry_parameters_and_no_retry() {
    var retry2 = ResilienceConfigurationKey.from(scenario("s1", 300, 50, connector("c1", "2", "0.5s")));
    var retry3 = ResilienceConfigurationKey.from(scenario("s2", 300, 50, connector("c1", "3", "0.5s")));
    var noRetry = ResilienceConfigurationKey.from(scenario("s3", 300, 50, noRetryConnector("c1")));

    assertNotEquals(retry2, retry3);
    assertNotEquals(retry2, noRetry);
  }

  @Test
  void should_ignore_connector_and_env_order_when_content_is_equivalent() {
    var first = ResilienceConfigurationKey.from(scenario("s1", 300, 50,
            connector("c1", Map.of("A", "1", "B", "2")),
            connector("c2", Map.of("C", "3"))));
    var second = ResilienceConfigurationKey.from(scenario("s2", 300, 50,
            connector("c2", Map.of("C", "3")),
            connector("c1", Map.of("B", "2", "A", "1"))));

    assertEquals(first, second);
  }

  static Scenario scenario(String name, int users, int faultPercentage, Connector... connectors) {
    var scenario = new Scenario(new ScenarioSpec(
            name,
            new ScenarioWorkload("workload", users),
            List.of(connectors),
            new ScenarioFault("envoy", faultPercentage, List.of("destination"))));
    scenario.setMetadata(new ObjectMetaBuilder().withName(name).build());
    return scenario;
  }

  static Connector connector(String name, String attempts, String backoff) {
    return connector(name, Map.of("GRPC_MAX_ATTEMPTS", attempts, "GRPC_INITIAL_BACKOFF", backoff));
  }

  static Connector connector(String name, Map<String, Object> envs) {
    return new Connector.Builder()
            .name(name)
            .source(new Service("source", envs))
            .destination(new Service("destination"))
            .build();
  }

  static Connector noRetryConnector(String name) {
    return new Connector.Builder()
            .name(name)
            .source(new Service("source"))
            .destination(new Service("destination"))
            .build();
  }
}