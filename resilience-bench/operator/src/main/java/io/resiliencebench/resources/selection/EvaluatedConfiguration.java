package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.selection.configuration.ResilienceConfigurationKey;
import io.vertx.core.json.JsonObject;

public class EvaluatedConfiguration {

  private final ResilienceConfigurationKey configurationKey;
  private final JsonObject metrics;
  private final int expectedContexts;
  private final int completedContexts;

  public EvaluatedConfiguration(ResilienceConfigurationKey configurationKey, JsonObject metrics,
                                int expectedContexts, int completedContexts) {
    this.configurationKey = configurationKey;
    this.metrics = metrics;
    this.expectedContexts = expectedContexts;
    this.completedContexts = completedContexts;
  }

  public ResilienceConfigurationKey getConfigurationKey() {
    return configurationKey;
  }

  public JsonObject getMetrics() {
    return metrics;
  }

  public int getExpectedContexts() {
    return expectedContexts;
  }

  public int getCompletedContexts() {
    return completedContexts;
  }

  public boolean isComplete() {
    return completedContexts >= expectedContexts;
  }
}
