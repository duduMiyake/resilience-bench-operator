package io.resiliencebench.resources.benchmark;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.List;

public class BenchmarkSpec {

  @JsonPropertyDescription("The workload name to be used for the benchmark")
  private String workload;

  @JsonPropertyDescription("The strategy used to select scenarios for execution")
  private ScenarioSelectionStrategySpec strategy;

  @JsonPropertyDescription("The set of scenarios templates to be processed and then generated as scenarios")
  private List<ScenarioTemplate> scenarios = new ArrayList<>();

  public BenchmarkSpec() {
  }

  public BenchmarkSpec(String workload, List<ScenarioTemplate> scenarioTemplates) {
    this();
    this.workload = workload;
    this.scenarios = scenarioTemplates;
  }

  public BenchmarkSpec(String workload, ScenarioSelectionStrategySpec strategy, List<ScenarioTemplate> scenarioTemplates) {
    this(workload, scenarioTemplates);
    this.strategy = strategy;
  }

  public String getWorkload() {
    return workload;
  }

  public ScenarioSelectionStrategySpec getStrategy() {
    return strategy;
  }

  public List<ScenarioTemplate> getScenarios() {
    return scenarios;
  }
}
