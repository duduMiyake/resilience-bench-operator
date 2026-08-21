package io.resiliencebench.resources.benchmark;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class NormalizationSpec {

  @JsonPropertyDescription("Normalization type: minMax or reciprocal")
  private String type;

  private Double min;
  private Double max;
  private Double scale;

  public NormalizationSpec() {
  }

  public NormalizationSpec(String type, Double min, Double max, Double scale) {
    this.type = type;
    this.min = min;
    this.max = max;
    this.scale = scale;
  }

  public String getType() { return type; }
  public Double getMin() { return min; }
  public Double getMax() { return max; }
  public Double getScale() { return scale; }
}
