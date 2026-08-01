package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.selection.configuration.ResilienceConfigurationKey;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class ConfigurationSelectionDecision {

  private final ResilienceConfigurationKey configurationKey;
  private final String heuristic;
  private final JsonObject metadata;
  private final String selectedAt;

  private ConfigurationSelectionDecision(ResilienceConfigurationKey configurationKey,
                                         String heuristic,
                                         JsonObject metadata,
                                         String selectedAt) {
    this.configurationKey = configurationKey;
    this.heuristic = heuristic;
    this.metadata = metadata == null ? new JsonObject() : metadata.copy();
    this.selectedAt = selectedAt;
  }

  public static ConfigurationSelectionDecision of(ResilienceConfigurationKey configurationKey,
                                                  String heuristic) {
    return new ConfigurationSelectionDecision(configurationKey, heuristic, new JsonObject(), now());
  }

  public static ConfigurationSelectionDecision of(ResilienceConfigurationKey configurationKey,
                                                  String heuristic,
                                                  JsonObject metadata) {
    return new ConfigurationSelectionDecision(configurationKey, heuristic, metadata, now());
  }

  public ResilienceConfigurationKey getConfigurationKey() {
    return configurationKey;
  }

  public String getHeuristic() {
    return heuristic;
  }

  public JsonObject getMetadata() {
    return metadata.copy();
  }

  public String getSelectedAt() {
    return selectedAt;
  }

  private static String now() {
    return LocalDateTime.now(ZoneOffset.UTC).toString();
  }
}
