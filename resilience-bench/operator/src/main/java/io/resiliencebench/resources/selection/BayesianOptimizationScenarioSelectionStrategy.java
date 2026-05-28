package io.resiliencebench.resources.selection;

import com.fasterxml.jackson.databind.JsonNode;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.scenario.Connector;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.workload.Workload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

@Component
public class BayesianOptimizationScenarioSelectionStrategy implements AdaptiveScenarioSelectionStrategy {

  @Override
  public List<Scenario> selectScenarios(List<Scenario> allScenarios, Benchmark benchmark, Workload workload) {
    if (allScenarios.isEmpty()) {
      return List.of();
    }

    ScenarioSelectionStrategySpec strategy = benchmark.getSpec().getStrategy();
    long seed = strategy.getSeed() == null ? ScenarioSelectionStrategySpec.DEFAULT_SEED : strategy.getSeed();
    Integer maxEvaluations = strategy.getMaxEvaluations() == null
            ? strategy.getMaxScenarios()
            : strategy.getMaxEvaluations();
    if (maxEvaluations == null) {
      maxEvaluations = allScenarios.size();
    }

    int budget = Math.max(1, Math.min(maxEvaluations, allScenarios.size()));
    int initialSamples = strategy.getInitialSamples() == null
            ? Math.min(ScenarioSelectionStrategySpec.DEFAULT_INITIAL_SAMPLES, budget)
            : Math.min(strategy.getInitialSamples(), budget);

    List<Scenario> selected = selectSpaceFillingScenarios(allScenarios, seed, initialSamples);

    return List.copyOf(selected);
  }

  @Override
  public Optional<Scenario> selectNextScenario(List<Scenario> allScenarios,
                                               Set<String> alreadyQueuedScenarioNames,
                                               List<EvaluatedScenario> evaluatedScenarios,
                                               Benchmark benchmark,
                                               Workload workload) {
    if (allScenarios.isEmpty()) {
      return Optional.empty();
    }

    int budget = evaluationBudget(allScenarios, benchmark);
    if (alreadyQueuedScenarioNames.size() >= budget) {
      return Optional.empty();
    }

    List<Scenario> candidates = allScenarios.stream()
            .filter(scenario -> !alreadyQueuedScenarioNames.contains(scenario.getMetadata().getName()))
            .toList();
    if (candidates.isEmpty()) {
      return Optional.empty();
    }

    Map<String, Map<String, Double>> vectors = encode(allScenarios);
    if (evaluatedScenarios.isEmpty()) {
      long seed = benchmark.getSpec().getStrategy().getSeed() == null
              ? ScenarioSelectionStrategySpec.DEFAULT_SEED
              : benchmark.getSpec().getStrategy().getSeed();
      Set<String> queued = new HashSet<>(alreadyQueuedScenarioNames);
      return selectSpaceFillingScenarios(allScenarios, seed, allScenarios.size()).stream()
              .filter(scenario -> !queued.contains(scenario.getMetadata().getName()))
              .findFirst();
    }

    List<String> evaluatedNames = evaluatedScenarios.stream().map(EvaluatedScenario::getScenarioName).toList();
    return candidates.stream()
            .max(Comparator.comparingDouble(candidate ->
                    acquisitionScore(candidate, evaluatedScenarios, evaluatedNames, vectors, benchmark)));
  }

  private static int evaluationBudget(List<Scenario> allScenarios, Benchmark benchmark) {
    ScenarioSelectionStrategySpec strategy = benchmark.getSpec().getStrategy();
    Integer maxEvaluations = strategy.getMaxEvaluations() == null
            ? strategy.getMaxScenarios()
            : strategy.getMaxEvaluations();
    if (maxEvaluations == null) {
      maxEvaluations = allScenarios.size();
    }
    return Math.max(1, Math.min(maxEvaluations, allScenarios.size()));
  }

  private static List<Scenario> selectSpaceFillingScenarios(List<Scenario> allScenarios, long seed, int sampleSize) {
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

  private static double acquisitionScore(Scenario candidate,
                                         List<EvaluatedScenario> evaluatedScenarios,
                                         List<String> evaluatedNames,
                                         Map<String, Map<String, Double>> vectors,
                                         Benchmark benchmark) {
    Map<String, Double> candidateVector = vectors.get(candidate.getMetadata().getName());
    double bestObserved = evaluatedScenarios.stream()
            .mapToDouble(evaluatedScenario -> objectiveScore(evaluatedScenario, benchmark))
            .max()
            .orElse(0.0);

    double weightedScore = 0.0;
    double totalWeight = 0.0;
    double minDistance = Double.MAX_VALUE;
    for (EvaluatedScenario evaluatedScenario : evaluatedScenarios) {
      Map<String, Double> evaluatedVector = vectors.get(evaluatedScenario.getScenarioName());
      if (evaluatedVector == null) {
        continue;
      }
      double distance = distance(candidateVector, evaluatedVector);
      minDistance = Math.min(minDistance, distance);
      double weight = 1.0 / (distance + 0.000001);
      weightedScore += weight * objectiveScore(evaluatedScenario, benchmark);
      totalWeight += weight;
    }

    double predictedScore = totalWeight == 0 ? bestObserved : weightedScore / totalWeight;
    double uncertainty = minDistance == Double.MAX_VALUE
            ? distanceToClosestEvaluated(candidate, evaluatedNames, vectors)
            : minDistance;
    double expectedImprovement = Math.max(0.0, predictedScore - bestObserved);
    return expectedImprovement + uncertainty;
  }

  private static double distanceToClosestEvaluated(Scenario candidate, List<String> evaluatedNames,
                                                   Map<String, Map<String, Double>> vectors) {
    Map<String, Double> candidateVector = vectors.get(candidate.getMetadata().getName());
    return evaluatedNames.stream()
            .map(vectors::get)
            .filter(java.util.Objects::nonNull)
            .mapToDouble(evaluatedVector -> distance(candidateVector, evaluatedVector))
            .min()
            .orElse(Double.MAX_VALUE);
  }

  private static double objectiveScore(EvaluatedScenario evaluatedScenario, Benchmark benchmark) {
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

  private static double defaultObjective(io.vertx.core.json.JsonObject metrics) {
    double successRate = metrics.containsKey("checkout_success_rate")
            ? metricValue(metrics, "checkout_success_rate")
            : metricValue(metrics, "successRate");
    double latency = metrics.containsKey("iteration_duration_p95")
            ? metricValue(metrics, "iteration_duration_p95")
            : metricValue(metrics, "p95Latency");
    return successRate - latency;
  }

  private static double metricValue(io.vertx.core.json.JsonObject metrics, String metric) {
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

  private static double distance(Map<String, Double> first, Map<String, Double> second) {
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

  private static Map<String, Map<String, Double>> encode(List<Scenario> scenarios) {
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
