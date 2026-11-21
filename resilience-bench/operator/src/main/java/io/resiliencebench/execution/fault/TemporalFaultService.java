package io.resiliencebench.execution.fault;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.scenario.ScenarioFault;
import io.resiliencebench.resources.service.ResilientService;
import io.resiliencebench.support.CustomResourceRepository;
import io.vertx.core.json.JsonObject;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static io.resiliencebench.support.Annotations.ENVOY_PORT;
import static io.resiliencebench.support.Annotations.ENVOY_SERVICE;

@Service
public class TemporalFaultService {
  private static final Duration ADMIN_TIMEOUT = Duration.ofSeconds(3);
  private static final String ABORT_PERCENT = "fault.http.abort.abort_percent";
  private static final String ABORT_STATUS = "fault.http.abort.http_status";
  private static final String GRPC_STATUS = "fault.http.abort.grpc_status";
  private static final String DELAY_PERCENT = "fault.http.delay.fixed_delay_percent";
  private static final String DELAY_DURATION = "fault.http.delay.fixed_duration_ms";

  private final KubernetesClient kubernetesClient;
  private final CustomResourceRepository<ResilientService> resilientServices;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(ADMIN_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NEVER).build();

  public TemporalFaultService(KubernetesClient kubernetesClient,
                              CustomResourceRepository<ResilientService> resilientServices) {
    this.kubernetesClient = kubernetesClient;
    this.resilientServices = resilientServices;
  }

  public TemporalFaultSession prepare(Scenario scenario) {
    var fault = scenario.getSpec().getFault();
    fault.validateWindowProfile();
    if (!fault.hasWindows()) {
      throw new IllegalArgumentException("Temporal injection requires fault windows");
    }
    String namespace = scenario.getMetadata().getNamespace();
    var targets = new LinkedHashSet<URI>();
    for (String name : fault.getServices()) {
      targets.add(resolveAdmin(namespace, name));
    }
    // Keep both injection modes disabled during initial setup and outside each window.
    var admins = List.copyOf(targets);
    try {
      apply(admins, runtimeValues(fault, 0));
      for (URI admin : admins) {
        requireDisabledRuntime(readRuntime(admin.resolve("/runtime")));
      }
    } catch (RuntimeException failure) {
      var interrupted = Thread.interrupted();
      try {
        apply(admins, clearedOverrides(fault, false));
      } catch (RuntimeException cleanup) {
        failure.addSuppressed(cleanup);
      } finally {
        if (interrupted) Thread.currentThread().interrupt();
      }
      throw failure;
    }
    var touchedParameters = new AtomicBoolean();
    return new TemporalFaultSession(fault.getWindows(), Boolean.TRUE.equals(fault.getRepeat()), fault.getPercentage(),
            percentage -> {
              if (percentage > 0) touchedParameters.set(true);
              apply(admins, runtimeValues(fault, percentage));
            },
            () -> apply(admins, clearedOverrides(fault, touchedParameters.get())));
  }

  private URI resolveAdmin(String namespace, String name) {
    var resilientService = resilientServices.get(namespace, name);
    String serviceName = Optional.ofNullable(resilientService.getMetadata().getAnnotations())
            .map(annotations -> annotations.get(ENVOY_SERVICE))
            .filter(value -> value.matches("[a-z0-9](?:[-a-z0-9]*[a-z0-9])?"))
            .orElseThrow(() -> new IllegalArgumentException("Missing Envoy Service annotation for " + name));
    var service = kubernetesClient.services().inNamespace(namespace).withName(serviceName).get();
    if (service == null || service.getSpec() == null || !"ClusterIP".equals(service.getSpec().getType())
            || service.getSpec().getSelector() == null || service.getSpec().getSelector().isEmpty()) {
      throw new IllegalArgumentException("Expected a selector-backed ClusterIP Envoy Service: " + serviceName);
    }
    var adminPort = service.getSpec().getPorts().stream()
            .filter(port -> ENVOY_PORT.equals(port.getName()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No named Envoy admin port on " + serviceName));
    var pods = kubernetesClient.pods().inNamespace(namespace).withLabels(service.getSpec().getSelector())
            .list().getItems().stream()
            .filter(pod -> pod.getMetadata().getDeletionTimestamp() == null)
            .filter(pod -> pod.getStatus() == null || !List.of("Succeeded", "Failed").contains(pod.getStatus().getPhase()))
            .toList();
    if (pods.size() != 1 || !ready(pods.get(0))) {
      throw new IllegalArgumentException("Temporal injection requires one ready Pod for " + serviceName);
    }

    var pod = pods.get(0);
    if (pod.getSpec() == null || Boolean.TRUE.equals(pod.getSpec().getHostNetwork())) {
      throw new IllegalArgumentException("Temporal injection does not support host-network Pods: " + serviceName);
    }
    int port = resolveTargetPort(pod, adminPort.getTargetPort(), adminPort.getPort());
    String ip = pod.getStatus().getPodIP();
    if (ip == null || !(ip.matches("[0-9]+(?:\\.[0-9]+){3}") || (ip.contains(":") && ip.matches("[0-9a-fA-F:]+")))
            || port < 1 || port > 65535) {
      throw new IllegalArgumentException("Invalid Envoy Pod IP or admin port for " + serviceName);
    }
    try {
      return new URI("http", null, ip, port, "/runtime_modify", null, null);
    } catch (URISyntaxException error) {
      throw new IllegalArgumentException("Invalid Envoy runtime address for " + serviceName, error);
    }
  }

  private static boolean ready(Pod pod) {
    return pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase())
            && pod.getStatus().getConditions().stream()
            .anyMatch(condition -> "Ready".equals(condition.getType()) && "True".equals(condition.getStatus()));
  }

  private static int resolveTargetPort(Pod pod, IntOrString targetPort, int defaultPort) {
    if (targetPort == null) {
      return defaultPort;
    }
    if (targetPort.getIntVal() != null) {
      return targetPort.getIntVal();
    }
    return pod.getSpec().getContainers().stream().flatMap(container -> container.getPorts().stream())
            .filter(port -> targetPort.getStrVal().equals(port.getName()))
            .mapToInt(port -> port.getContainerPort()).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Envoy target port missing on Pod " + pod.getMetadata().getName()));
  }

  static Map<String, String> runtimeValues(ScenarioFault fault, int percentage) {
    var values = new LinkedHashMap<String, String>();
    values.put(ABORT_PERCENT, "0");
    values.put(DELAY_PERCENT, "0");
    if (percentage != 0 && "abort".equals(fault.getType())) {
      values.put(ABORT_PERCENT, Integer.toString(percentage));
      values.put(ABORT_STATUS, fault.getStatus() == null ? "" : fault.getStatus().toString());
      // An empty override clears a previously set gRPC status so Envoy can derive it from HTTP.
      values.put(GRPC_STATUS, fault.getGrpcStatus() == null ? "" : fault.getGrpcStatus().toString());
    } else if (percentage != 0 && "delay".equals(fault.getType())) {
      values.put(DELAY_PERCENT, Integer.toString(percentage));
      values.put(DELAY_DURATION, fault.getDuration().toString());
    }
    return values;
  }

  static Map<String, String> clearedOverrides(ScenarioFault fault, boolean touchedParameters) {
    // Envoy treats an empty value as removing the admin override, revealing the bootstrap value.
    var values = new LinkedHashMap<String, String>();
    values.put(ABORT_PERCENT, "");
    values.put(DELAY_PERCENT, "");
    if (touchedParameters && "abort".equals(fault.getType())) {
      values.put(ABORT_STATUS, "");
      values.put(GRPC_STATUS, "");
    } else if (touchedParameters && "delay".equals(fault.getType())) {
      values.put(DELAY_DURATION, "");
    }
    return values;
  }

  private void apply(List<URI> targets, Map<String, String> values) {
    String query = values.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("&"));
    RuntimeException failure = null;
    for (URI target : targets) {
      try {
        post(URI.create(target + "?" + query));
      } catch (RuntimeException error) {
        if (failure == null) {
          failure = error;
        } else {
          failure.addSuppressed(error);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private void post(URI target) {
    send(HttpRequest.newBuilder(target).timeout(ADMIN_TIMEOUT).POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.discarding());
  }

  private JsonObject readRuntime(URI target) {
    return new JsonObject(send(HttpRequest.newBuilder(target).timeout(ADMIN_TIMEOUT).GET().build(),
            HttpResponse.BodyHandlers.ofString()));
  }

  static void requireDisabledRuntime(JsonObject runtime) {
    var entries = runtime.getJsonObject("entries", new JsonObject());
    for (String key : List.of(ABORT_PERCENT, DELAY_PERCENT)) {
      var entry = entries.getJsonObject(key);
      if (entry == null || !"0".equals(entry.getString("final_value"))) {
        throw new IllegalStateException("Envoy runtime did not report cleared fault percentage: " + key);
      }
    }
    // Snapshot readback does not prove a particular listener has the HTTP fault filter.
  }

  private <T> T send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    try {
      var response = http.send(request, handler);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("Envoy admin API returned HTTP " + response.statusCode());
      }
      return response.body();
    } catch (IOException exception) {
      throw new IllegalStateException("Envoy admin API did not acknowledge the request", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Envoy admin request interrupted", exception);
    }
  }
}
