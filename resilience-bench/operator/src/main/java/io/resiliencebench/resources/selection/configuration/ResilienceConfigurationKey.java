package io.resiliencebench.resources.selection.configuration;

import io.resiliencebench.resources.scenario.Scenario;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Objects;

public final class ResilienceConfigurationKey {

  private final JsonObject normalized;
  private final String encoded;
  private final String hash;
  private final String summary;

  private ResilienceConfigurationKey(JsonObject normalized) {
    this.normalized = normalized;
    this.encoded = normalized.encode();
    this.hash = sha256(encoded);
    this.summary = createSummary(normalized);
  }

  public static ResilienceConfigurationKey from(Scenario scenario) {
    var normalized = new JsonObject()
            .put("connectors", sortedConnectors(scenario.getSpec().toConnectorsInJson()));
    return new ResilienceConfigurationKey(normalized);
  }

  public JsonObject normalized() {
    return new JsonObject(encoded);
  }

  public String encoded() {
    return encoded;
  }

  public String hash() {
    return hash;
  }

  public String summary() {
    return summary;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ResilienceConfigurationKey that)) {
      return false;
    }
    return encoded.equals(that.encoded);
  }

  @Override
  public int hashCode() {
    return Objects.hash(encoded);
  }

  @Override
  public String toString() {
    return summary + "#" + hash.substring(0, 12);
  }

  private static JsonArray sortedConnectors(JsonArray connectors) {
    var sorted = connectors.stream()
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .map(ResilienceConfigurationKey::normalizeConnector)
            .sorted(Comparator.comparing(JsonObject::encode))
            .toList();
    return new JsonArray(sorted);
  }

  private static JsonObject normalizeConnector(JsonObject connector) {
    var normalized = sortedJson(connector);
    normalized.put("strategy", hasResilienceSettings(normalized) ? "CONFIGURED" : "NONE");
    return sortedJson(normalized);
  }

  private static boolean hasResilienceSettings(JsonObject connector) {
    return connector.fieldNames().stream().anyMatch(key ->
            key.startsWith("source_env_")
                    || key.startsWith("destination_env_")
                    || "retry".equals(key) && !isEmptyJson(connector.getValue(key))
                    || "timeout".equals(key) && !isEmptyJson(connector.getValue(key))
                    || "circuitBreaker".equals(key) && !isEmptyJson(connector.getValue(key))
                    || "percentage".equals(key) && connector.getValue(key) != null
                    || "delay".equals(key) && !isEmptyJson(connector.getValue(key))
                    || "abort".equals(key) && !isEmptyJson(connector.getValue(key)));
  }

  private static boolean isEmptyJson(Object value) {
    if (value == null) {
      return true;
    }
    if (value instanceof JsonObject object) {
      return object.isEmpty();
    }
    if (value instanceof JsonArray array) {
      return array.isEmpty();
    }
    return false;
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

  private static String createSummary(JsonObject normalized) {
    var connectors = normalized.getJsonArray("connectors", new JsonArray()).stream()
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .map(connector -> connector.getString("name", "connector")
                    + ":" + connector.getString("source", "?")
                    + "->" + connector.getString("destination", "?")
                    + "=" + connector.getString("strategy", "CONFIGURED"))
            .toList();
    return String.join(",", connectors);
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
