package io.resiliencebench.resources.benchmark;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class ObjectiveMetricSpec {

  @JsonPropertyDescription("Metric name in the aggregated result")
  private String name;

  @JsonPropertyDescription("Metric direction: maximize or minimize")
  private String direction;

  @JsonPropertyDescription("Optional positive metric weight")
  private Double weight;

  @JsonPropertyDescription("Fixed normalization definition")
  private NormalizationSpec normalization;

  public ObjectiveMetricSpec() {
  }

  public ObjectiveMetricSpec(String name, String direction, Double weight, NormalizationSpec normalization) {
    this.name = name;
    this.direction = direction;
    this.weight = weight;
    this.normalization = normalization;
  }

  public String getName() { return name; }
  public String getDirection() { return direction; }
  public Double getWeight() { return weight; }
  public NormalizationSpec getNormalization() { return normalization; }
}
