package io.resiliencebench.resources.benchmark;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class ScenarioSelectionStrategySpec {

  public static final String EXHAUSTIVE = "exhaustive";
  public static final String RANDOM_SAMPLING = "randomSampling";
  public static final String KNN_ADAPTIVE = "knnAdaptive";
  public static final long DEFAULT_SEED = 42L;
  public static final double DEFAULT_SAMPLE_RATE = 0.5;
  public static final int DEFAULT_INITIAL_SAMPLES = 20;
  public static final int DEFAULT_NEIGHBORS = 3;
  public static final double DEFAULT_EXPLORATION_WEIGHT = 0.1;

  @JsonPropertyDescription("The scenario selection strategy type: exhaustive, randomSampling or knnAdaptive")
  private String type;

  @JsonPropertyDescription("Fraction of configurations to select. Valid range is (0, 1]")
  private Double sampleRate;

  @JsonPropertyDescription("Maximum number of scenarios to select. Deprecated for configuration-based selection; use maxConfigurations instead")
  private Integer maxScenarios;

  @JsonPropertyDescription("Maximum number of resilience configurations to select")
  private Integer maxConfigurations;

  @JsonPropertyDescription("Seed used by randomSampling for reproducible scenario selection")
  private Long seed;

  @JsonPropertyDescription("Number of initial space-filling configuration samples for adaptive strategies")
  private Integer initialSamples;

  @JsonPropertyDescription("Maximum number of configuration evaluations for adaptive strategies. Deprecated for configuration-based selection; use maxConfigurations instead")
  private Integer maxEvaluations;

  @JsonPropertyDescription("Objective metrics used to compare evaluated scenarios")
  private ObjectiveSpec objective;

  @JsonPropertyDescription("Number of evaluated nearest neighbors used by knnAdaptive")
  private Integer neighbors;

  @JsonPropertyDescription("Exploration bonus weight used by knnAdaptive")
  private Double explorationWeight;

  public ScenarioSelectionStrategySpec() {
  }

  public ScenarioSelectionStrategySpec(String type, Double sampleRate, Integer maxScenarios, Long seed) {
    this.type = type;
    this.sampleRate = sampleRate;
    this.maxScenarios = maxScenarios;
    this.seed = seed;
  }

  public ScenarioSelectionStrategySpec(String type, Double sampleRate, Integer maxScenarios, Long seed,
                                       Integer initialSamples, Integer maxEvaluations,
                                       ObjectiveSpec objective) {
    this(type, sampleRate, maxScenarios, seed);
    this.initialSamples = initialSamples;
    this.maxEvaluations = maxEvaluations;
    this.objective = objective;
  }

  public ScenarioSelectionStrategySpec(String type, Double sampleRate, Integer maxScenarios, Long seed,
                                       Integer initialSamples, Integer maxEvaluations,
                                       ObjectiveSpec objective,
                                       Integer neighbors, Double explorationWeight) {
    this(type, sampleRate, maxScenarios, seed, initialSamples, maxEvaluations, objective);
    this.neighbors = neighbors;
    this.explorationWeight = explorationWeight;
  }

  public String getType() {
    return type;
  }

  public Double getSampleRate() {
    return sampleRate;
  }

  public Integer getMaxScenarios() {
    return maxScenarios;
  }

  public Integer getMaxConfigurations() {
    return maxConfigurations;
  }

  public void setMaxConfigurations(Integer maxConfigurations) {
    this.maxConfigurations = maxConfigurations;
  }

  public Long getSeed() {
    return seed;
  }

  public Integer getInitialSamples() {
    return initialSamples;
  }

  public Integer getMaxEvaluations() {
    return maxEvaluations;
  }

  public ObjectiveSpec getObjective() {
    return objective;
  }

  public Integer getNeighbors() {
    return neighbors;
  }

  public Double getExplorationWeight() {
    return explorationWeight;
  }
}