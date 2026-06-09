package io.resiliencebench.resources.selection;

import com.fasterxml.jackson.databind.JsonNode;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.scenario.Connector;
import io.resiliencebench.resources.scenario.Scenario;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class ScenarioSelectionSupport {

  private ScenarioSelectionSupport() {
    throw new IllegalStateException("Utility class");
  }

  static int evaluationBudget(List<Scenario> allScenarios, Benchmark benchmark) {
    ScenarioSelectionStrategySpec strategy = benchmark.getSpec().getStrategy();
    Integer maxEvaluations = strategy.getMaxEvaluations() == null
            ? strategy.getMaxScenarios()
            : strategy.getMaxEvaluations();
    if (maxEvaluations == null) {
      maxEvaluations = allScenarios.size();
    }
    return Math.max(1, Math.min(maxEvaluations, allScenarios.size()));
  }

  static int initialSamples(List<Scenario> allScenarios, Benchmark benchmark) {
    int budget = evaluationBudget(allScenarios, benchmark);
    var strategy = benchmark.getSpec().getStrategy();
    return strategy.getInitialSamples() == null
            ? Math.min(ScenarioSelectionStrategySpec.DEFAULT_INITIAL_SAMPLES, budget)
            : Math.min(strategy.getInitialSamples(), budget);
  }

  static long seed(Benchmark benchmark) {
    var strategy = benchmark.getSpec().getStrategy();
    return strategy.getSeed() == null ? ScenarioSelectionStrategySpec.DEFAULT_SEED : strategy.getSeed();
  }

  static List<Scenario> selectSpaceFillingScenarios(List<Scenario> allScenarios, long seed, int sampleSize) {
    List<Scenario> candidates = new ArrayList<>(allScenarios);
    java.util.Collections.shuffle(candidates, new Random(seed));

    List<Scenario> selected = new ArrayList<>();
    if (sampleSize <= 0 || candidates.isEmpty()) {
      return selected;
    }

    selected.add(candidates.remove(0));
    Map<String, Map<String, Double>> vectors = encode(allScenarios);
    while (selected.size() < sampleSize && !candidates.isEmpty()) {
      Scenario next = candidates.stream()
              .max(Comparator.comparingDouble(candidate -> distanceToClosestSelected(candidate, selected, vectors)))
              .orElseThrow();
      selected.add(next);
      candidates.remove(next);
    }
    return selected;
  }

  static double objectiveScore(EvaluatedScenario evaluatedScenario, Benchmark benchmark) {
    var objective = benchmark.getSpec().getStrategy().getObjective();
    if (objective == null
            || ((objective.getMaximize() == null || objective.getMaximize().isEmpty())
            && (objective.getMinimize() == null || objective.getMinimize().isEmpty()))) {
      return defaultObjective(evaluatedScenario.getMetrics());
    }

    double score = 0.0;
    if (objective.getMaximize() != null) {
      for (var metric : objective.getMaximize()) {
        score += metricValue(evaluatedScenario.getMetrics(), metric);
      }
    }
    if (objective.getMinimize() != null) {
      for (var metric : objective.getMinimize()) {
        score -= metricValue(evaluatedScenario.getMetrics(), metric);
      }
    }
    return score;
  }

  static double distanceToClosestEvaluated(Scenario candidate, List<String> evaluatedNames,
                                           Map<String, Map<String, Double>> vectors) {
    Map<String, Double> candidateVector = vectors.get(candidate.getMetadata().getName());
    return evaluatedNames.stream()
            .map(vectors::get)
            .filter(java.util.Objects::nonNull)
            .mapToDouble(evaluatedVector -> distance(candidateVector, evaluatedVector))
            .min()
            .orElse(Double.MAX_VALUE);
  }

  static double distance(Map<String, Double> first, Map<String, Double> second) {
    Set<String> keys = new java.util.HashSet<>();
    keys.addAll(first.keySet());
    keys.addAll(second.keySet());

    double sum = 0.0;
    for (String key : keys) {
      double delta = first.getOrDefault(key, 0.0) - second.getOrDefault(key, 0.0);
      sum += delta * delta;
    }
    return Math.sqrt(sum);
  }

  static Map<String, Map<String, Double>> encode(List<Scenario> scenarios) {
    Map<String, Map<String, Double>> rawVectors = new LinkedHashMap<>();
    for (Scenario scenario : scenarios) {
      rawVectors.put(scenario.getMetadata().getName(), rawFeatures(scenario));
    }

    Map<String, Double> min = new LinkedHashMap<>();
    Map<String, Double> max = new LinkedHashMap<>();
    for (Map<String, Double> vector : rawVectors.values()) {
      for (Map.Entry<String, Double> entry : vector.entrySet()) {
        min.merge(entry.getKey(), entry.getValue(), Math::min);
        max.merge(entry.getKey(), entry.getValue(), Math::max);
      }
    }

    Map<String, Map<String, Double>> normalizedVectors = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, Double>> entry : rawVectors.entrySet()) {
      Map<String, Double> normalized = new LinkedHashMap<>();
      for (Map.Entry<String, Double> feature : entry.getValue().entrySet()) {
        double minValue = min.get(feature.getKey());
        double maxValue = max.get(feature.getKey());
        double range = maxValue - minValue;
        normalized.put(feature.getKey(), range == 0 ? 0.0 : (feature.getValue() - minValue) / range);
      }
      normalizedVectors.put(entry.getKey(), normalized);
    }
    return normalizedVectors;
  }

  private static double defaultObjective(JsonObject metrics) {
    double successRate = metrics.containsKey("checkout_success_rate")
            ? metricValue(metrics, "checkout_success_rate")
            : metricValue(metrics, "successRate");
    double latency = metrics.containsKey("iteration_duration_p95")
            ? metricValue(metrics, "iteration_duration_p95")
            : metricValue(metrics, "p95Latency");
    return successRate - latency;
  }

  private static double metricValue(JsonObject metrics, String metric) {
    var value = metrics.getValue(metric);
    if (value == null && "successRate".equals(metric)) {
      value = metrics.getValue("checkout_success_rate");
    }
    if (value == null && "p95Latency".equals(metric)) {
      value = metrics.getValue("iteration_duration_p95");
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String text) {
      try {
        return Double.parseDouble(text);
      } catch (NumberFormatException ignored) {
        return 0.0;
      }
    }
    return 0.0;
  }

  private static double distanceToClosestSelected(Scenario candidate, List<Scenario> selected,
                                                  Map<String, Map<String, Double>> vectors) {
    Map<String, Double> candidateVector = vectors.get(candidate.getMetadata().getName());
    return selected.stream()
            .map(selectedScenario -> vectors.get(selectedScenario.getMetadata().getName()))
            .mapToDouble(selectedVector -> distance(candidateVector, selectedVector))
            .min()
            .orElse(Double.MAX_VALUE);
  }

  private static Map<String, Double> rawFeatures(Scenario scenario) {
    Map<String, Double> features = new LinkedHashMap<>();
    features.put("workload.users", (double) scenario.getSpec().getWorkload().getUsers());

    if (scenario.getSpec().getFault() != null) {
      features.put("fault.percentage", (double) scenario.getSpec().getFault().getPercentage());
    }

    for (Connector connector : scenario.getSpec().getConnectors()) {
      addConnectorFeatures(features, connector);
    }
    return features;
  }

  private static void addConnectorFeatures(Map<String, Double> features, Connector connector) {
    String prefix = "connector." + connector.getName() + ".";
    if (connector.getSource().getEnvs() != null) {
      for (Map.Entry<String, JsonNode> env : connector.getSource().getEnvs().entrySet()) {
        features.put(prefix + "source.env." + env.getKey(), numericValue(env.getValue()));
      }
    }
    if (connector.getDestination().getEnvs() != null) {
      for (Map.Entry<String, JsonNode> env : connector.getDestination().getEnvs().entrySet()) {
        features.put(prefix + "destination.env." + env.getKey(), numericValue(env.getValue()));
      }
    }
  }

  private static double numericValue(JsonNode value) {
    if (value.isNumber()) {
      return value.asDouble();
    }
    String text = value.asText();
    try {
      return Double.parseDouble(text.replace("s", ""));
    } catch (NumberFormatException e) {
      return Math.abs(text.hashCode());
    }
  }
}
