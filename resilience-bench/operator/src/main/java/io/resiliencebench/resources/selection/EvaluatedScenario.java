package io.resiliencebench.resources.selection;

import io.vertx.core.json.JsonObject;

public class EvaluatedScenario {

  private final String scenarioName;
  private final JsonObject metrics;

  public EvaluatedScenario(String scenarioName, JsonObject metrics) {
    this.scenarioName = scenarioName;
    this.metrics = metrics;
  }

  public String getScenarioName() {
    return scenarioName;
  }

  public JsonObject getMetrics() {
    return metrics;
  }
}
