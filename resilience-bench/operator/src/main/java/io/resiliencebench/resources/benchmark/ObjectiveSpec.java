package io.resiliencebench.resources.benchmark;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.List;

public class ObjectiveSpec {

  @JsonPropertyDescription("Metrics that should be maximized")
  private List<String> maximize = new ArrayList<>();

  @JsonPropertyDescription("Metrics that should be minimized")
  private List<String> minimize = new ArrayList<>();

  @JsonPropertyDescription("Structured normalized objective metrics")
  private List<ObjectiveMetricSpec> metrics;

  public ObjectiveSpec() {
  }

  public ObjectiveSpec(List<String> maximize, List<String> minimize) {
    this.maximize = maximize;
    this.minimize = minimize;
  }

  public static ObjectiveSpec structured(List<ObjectiveMetricSpec> metrics) {
    var objective = new ObjectiveSpec();
    objective.metrics = metrics;
    return objective;
  }

  public List<String> getMaximize() {
    return maximize == null ? List.of() : maximize;
  }

  public List<String> getMinimize() {
    return minimize == null ? List.of() : minimize;
  }

  public List<ObjectiveMetricSpec> getMetrics() {
    return metrics;
  }

  public void setMetrics(List<ObjectiveMetricSpec> metrics) {
    this.metrics = metrics;
  }
}
