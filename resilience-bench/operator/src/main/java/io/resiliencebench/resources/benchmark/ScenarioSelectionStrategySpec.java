package io.resiliencebench.resources.benchmark;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class ScenarioSelectionStrategySpec {

  public static final String EXHAUSTIVE = "exhaustive";
  public static final String RANDOM_SAMPLING = "randomSampling";
  public static final String BAYESIAN_OPTIMIZATION = "bayesianOptimization";
  public static final long DEFAULT_SEED = 42L;
  public static final double DEFAULT_SAMPLE_RATE = 0.5;
  public static final int DEFAULT_INITIAL_SAMPLES = 20;
  public static final String EXPECTED_IMPROVEMENT = "expectedImprovement";

  @JsonPropertyDescription("The scenario selection strategy type: exhaustive, randomSampling or bayesianOptimization")
  private String type;

  @JsonPropertyDescription("Fraction of scenarios to select. Valid range is (0, 1]")
  private Double sampleRate;

  @JsonPropertyDescription("Maximum number of scenarios to select")
  private Integer maxScenarios;

  @JsonPropertyDescription("Seed used by randomSampling for reproducible scenario selection")
  private Long seed;

  @JsonPropertyDescription("Number of initial space-filling samples for bayesianOptimization")
  private Integer initialSamples;

  @JsonPropertyDescription("Maximum number of scenario evaluations for bayesianOptimization")
  private Integer maxEvaluations;

  @JsonPropertyDescription("Acquisition function used by bayesianOptimization. Currently supports expectedImprovement")
  private String acquisitionFunction;

  @JsonPropertyDescription("Objective metrics used to compare evaluated scenarios")
  private ObjectiveSpec objective;

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
                                       String acquisitionFunction, ObjectiveSpec objective) {
    this(type, sampleRate, maxScenarios, seed);
    this.initialSamples = initialSamples;
    this.maxEvaluations = maxEvaluations;
    this.acquisitionFunction = acquisitionFunction;
    this.objective = objective;
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

  public Long getSeed() {
    return seed;
  }

  public Integer getInitialSamples() {
    return initialSamples;
  }

  public Integer getMaxEvaluations() {
    return maxEvaluations;
  }

  public String getAcquisitionFunction() {
    return acquisitionFunction;
  }

  public ObjectiveSpec getObjective() {
    return objective;
  }
}
