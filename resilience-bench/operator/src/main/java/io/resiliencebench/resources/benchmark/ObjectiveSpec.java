package io.resiliencebench.resources.benchmark;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.List;

public class ObjectiveSpec {

  @JsonPropertyDescription("Metrics that should be maximized")
  private List<String> maximize = new ArrayList<>();

  @JsonPropertyDescription("Metrics that should be minimized")
  private List<String> minimize = new ArrayList<>();

  public ObjectiveSpec() {
  }

  public ObjectiveSpec(List<String> maximize, List<String> minimize) {
    this.maximize = maximize;
    this.minimize = minimize;
  }

  public List<String> getMaximize() {
    return maximize;
  }

  public List<String> getMinimize() {
    return minimize;
  }
}
