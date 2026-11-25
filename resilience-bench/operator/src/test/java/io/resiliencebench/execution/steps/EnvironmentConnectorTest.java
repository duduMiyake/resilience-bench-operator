package io.resiliencebench.execution.steps;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.resiliencebench.resources.scenario.Connector;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.scenario.Service;
import io.resiliencebench.resources.service.ResilientService;
import io.resiliencebench.resources.service.ResilientServiceSpec;
import io.resiliencebench.support.CustomResourceRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.resiliencebench.support.Annotations.CONTAINER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnvironmentConnectorTest {
  private static final String PAYMENT = "GRPC_PAYMENT_MAX_ATTEMPTS";
  private static final String SHIPPING = "GRPC_SHIPPING_MAX_ATTEMPTS";

  @Test
  void appliesTwoPoliciesAndRestoresOnceBeforeBaselineScenario() {
    var f = new Fixture();
    var both = f.scenario("both", new Service("checkout", Map.of(PAYMENT, "2")),
        new Service("checkout", Map.of(SHIPPING, "5")));
    f.apply.execute(both, null);
    assertEquals(List.of(env(PAYMENT, "2"), env(SHIPPING, "5"), env("OTHER", "unchanged")), f.current());
    assertEquals(1, f.writes.size());
    assertEquals(f.original, f.checkout.getSpec().getEnvs());

    f.restore.execute(both, null);
    f.restore.execute(both, null);
    assertEquals(f.original, f.current());
    assertEquals(2, f.writes.size());

    var baseline = f.scenario("baseline", new Service("checkout"),
        new Service("checkout", Map.of(SHIPPING, "5")));
    f.apply.execute(baseline, null);
    assertEquals(List.of(env(PAYMENT, ""), env(SHIPPING, "5"), env("OTHER", "unchanged")), f.current());
  }

  @Test
  void resolvesAliasToSameActualContainerAndRejectsConflictsBeforeWrites() {
    var f = new Fixture();
    var conflict = f.scenario("conflict", new Service("checkout", Map.of(PAYMENT, "2")),
        new Service("alias", Map.of(PAYMENT, "5")));
    assertThrows(IllegalArgumentException.class, () -> f.apply.execute(conflict, null));
    verify(f.repository, never()).update(any());
    assertTrue(f.writes.isEmpty());

    var both = f.scenario("alias", new Service("checkout", Map.of(PAYMENT, "2")),
        new Service("alias", Map.of(SHIPPING, "5")));
    f.apply.execute(both, null);
    f.restore.execute(both, null);
    assertEquals(f.original, f.current());
    assertEquals(2, f.writes.size());
  }

  @Test
  void noChangesOrUndeclaredVariableDoNotOverwriteSnapshot() {
    var f = new Fixture();
    var noop = f.scenario("noop", new Service("checkout", Map.of(PAYMENT, "")), new Service("checkout"));
    f.apply.execute(noop, null);
    f.restore.execute(noop, null);
    var missing = f.scenario("missing", new Service("checkout", Map.of("MISSING", "2")), new Service("checkout"));
    assertThrows(IllegalArgumentException.class, () -> f.apply.execute(missing, null));
    assertTrue(f.writes.isEmpty());
    verify(f.repository, never()).update(any());
  }

  private static EnvVar env(String name, String value) {
    return new EnvVarBuilder().withName(name).withValue(value).build();
  }

  private static class Fixture {
    final List<EnvVar> original = List.of(env(PAYMENT, ""), env(SHIPPING, ""), env("OTHER", "unchanged"));
    final List<List<EnvVar>> writes = new ArrayList<>();
    final Deployment deployment = new DeploymentBuilder().withNewMetadata().withName("checkout")
        .withNamespace("test").endMetadata().withNewSpec().withNewTemplate().withNewSpec()
        .addNewContainer().withName("server").withEnv(original).endContainer()
        .endSpec().endTemplate().endSpec().build();
    final ResilientService checkout = service("checkout");
    final ResilientService alias = service("alias");
    final KubernetesClient client = mock(KubernetesClient.class);
    @SuppressWarnings("unchecked")
    final CustomResourceRepository<ResilientService> repository = mock(CustomResourceRepository.class);
    final EnvironmentStep apply = new EnvironmentStep(client, repository) {
      @Override protected Deployment requireEnvironmentDeployment(Scenario scenario, ResilientService service) {
        return deployment;
      }
      @Override protected void updateVariablesDeployment(Deployment target, String container, List<EnvVar> values) {
        write(values);
      }
      @Override protected void waitForEnvironmentRollout(Deployment target) {}
    };
    final EnvironmentPostStep restore = new EnvironmentPostStep(client, repository) {
      @Override protected Deployment requireEnvironmentDeployment(Scenario scenario, ResilientService service) {
        return deployment;
      }
      @Override protected void updateVariablesDeployment(Deployment target, String container, List<EnvVar> values) {
        write(values);
      }
      @Override protected void waitForEnvironmentRollout(Deployment target) {}
    };

    Fixture() {
      when(repository.get("test", "checkout")).thenReturn(checkout);
      when(repository.get("test", "alias")).thenReturn(alias);
    }

    List<EnvVar> current() {
      return deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
    }

    void write(List<EnvVar> values) {
      writes.add(values);
      deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(values);
    }

    Scenario scenario(String uid, Service payment, Service shipping) {
      var scenario = mock(Scenario.class, RETURNS_DEEP_STUBS);
      when(scenario.getMetadata().getNamespace()).thenReturn("test");
      when(scenario.getMetadata().getUid()).thenReturn(uid);
      var connectors = List.of(
          new Connector.Builder().name("payment").source(payment).destination(new Service("payment")).build(),
          new Connector.Builder().name("shipping").source(shipping).destination(new Service("shipping")).build());
      when(scenario.getSpec().getConnectors()).thenReturn(connectors);
      return scenario;
    }

    private static ResilientService service(String name) {
      var service = new ResilientService();
      service.setMetadata(new io.fabric8.kubernetes.api.model.ObjectMetaBuilder().withName(name).build());
      service.getMetadata().setAnnotations(Map.of(CONTAINER, "server"));
      service.setSpec(new ResilientServiceSpec());
      return service;
    }
  }
}
