package io.resiliencebench.execution.resultcache;

import io.resiliencebench.resources.scenario.Scenario;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;

@Component
public class ScenarioCacheKeyFactory {

  public String hash(Scenario scenario) {
    var normalized = normalizedScenario(scenario).encode();
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
      var builder = new StringBuilder();
      for (byte b : bytes) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  public JsonObject normalizedScenario(Scenario scenario) {
    var spec = scenario.getSpec();
    var json = new JsonObject();
    json.put("workload_name", spec.getWorkload().getWorkloadName());
    json.put("workload_users", spec.getWorkload().getUsers());
    if (spec.getFault() != null) {
      json.put("fault", new JsonObject()
              .put("provider", spec.getFault().getProvider())
              .put("percentage", spec.getFault().getPercentage())
              .put("services", new JsonArray(spec.getFault().getServices())));
    }
    json.put("connectors", sortedConnectors(spec.toConnectorsInJson()));
    return json;
  }

  public String hashFromResult(JsonObject result) {
    if (result.getString("scenarioHash") != null) {
      return result.getString("scenarioHash");
    }
    var normalized = new JsonObject();
    normalized.put("workload_name", result.getString("workload_name"));
    normalized.put("workload_users", result.getValue("workload_users"));
    if (result.containsKey("fault_percentage") || result.containsKey("fault_provider") || result.containsKey("fault_services")) {
      normalized.put("fault", new JsonObject()
              .put("provider", result.getValue("fault_provider"))
              .put("percentage", result.getValue("fault_percentage"))
              .put("services", result.getValue("fault_services")));
    }
    normalized.put("connectors", sortedConnectors(result.getJsonArray("connectors", new JsonArray())));
    return sha256(normalized.encode());
  }

  private static JsonArray sortedConnectors(JsonArray connectors) {
    var sorted = connectors.stream()
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .map(ScenarioCacheKeyFactory::sortedJson)
            .sorted(Comparator.comparing(JsonObject::encode))
            .toList();
    return new JsonArray(sorted);
  }

  private static JsonObject sortedJson(JsonObject json) {
    var sorted = new JsonObject();
    json.fieldNames().stream().sorted().forEach(key -> {
      var value = json.getValue(key);
      if (value instanceof JsonObject object) {
        sorted.put(key, sortedJson(object));
      } else if (value instanceof JsonArray array) {
        sorted.put(key, sortedArray(array));
      } else {
        sorted.put(key, value);
      }
    });
    return sorted;
  }

  private static JsonArray sortedArray(JsonArray array) {
    var normalized = array.stream()
            .map(value -> value instanceof JsonObject object ? sortedJson(object) : value)
            .sorted(Comparator.comparing(String::valueOf))
            .toList();
    return new JsonArray(normalized);
  }

  private static String sha256(String value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      var builder = new StringBuilder();
      for (byte b : bytes) {
        builder.append(String.format("%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
