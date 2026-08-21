package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.NormalizationSpec;
import io.resiliencebench.resources.benchmark.ObjectiveMetricSpec;
import io.resiliencebench.resources.benchmark.ObjectiveSpec;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ObjectiveScorer {

  private static final String MAXIMIZE = "maximize";
  private static final String MINIMIZE = "minimize";
  private static final String MIN_MAX = "minMax";
  private static final String RECIPROCAL = "reciprocal";

  private ObjectiveScorer() {
  }

  public static boolean isLegacy(Benchmark benchmark) {
    ObjectiveSpec objective = objective(benchmark);
    return objective != null && objective.getMetrics() == null
            && (!objective.getMaximize().isEmpty() || !objective.getMinimize().isEmpty());
  }

  public static void validate(Benchmark benchmark) {
    ObjectiveSpec objective = objective(benchmark);
    if (objective == null) {
      return;
    }
    if (objective.getMetrics() != null) {
      if (!objective.getMaximize().isEmpty() || !objective.getMinimize().isEmpty()) {
        throw new IllegalArgumentException("objective.metrics cannot be mixed with objective.maximize or objective.minimize");
      }
      if (objective.getMetrics().isEmpty()) {
        throw new IllegalArgumentException("objective.metrics must contain at least one metric");
      }
      objective.getMetrics().forEach(ObjectiveScorer::validateMetric);
      return;
    }
    objective.getMaximize().forEach(name -> validateName(name, "objective.maximize"));
    objective.getMinimize().forEach(name -> validateName(name, "objective.minimize"));
  }

  public static double score(JsonObject metrics, Benchmark benchmark) {
    validate(benchmark);
    ObjectiveSpec objective = objective(benchmark);
    if (objective == null || objective.getMetrics() == null || objective.getMetrics().isEmpty()) {
      return legacyScore(metrics, benchmark, objective);
    }

    double totalWeight = objective.getMetrics().stream()
            .mapToDouble(metric -> metric.getWeight() == null ? 1.0 : metric.getWeight())
            .sum();
    double score = 0.0;
    for (ObjectiveMetricSpec metric : objective.getMetrics()) {
      double value = numericMetric(metrics, metric.getName(), benchmark);
      double normalized = normalize(value, metric);
      double weight = metric.getWeight() == null ? 1.0 : metric.getWeight();
      score += (weight / totalWeight) * normalized;
    }
    return Math.max(0.0, Math.min(1.0, score));
  }

  public static JsonObject traceMetadata(Benchmark benchmark) {
    ObjectiveSpec objective = objective(benchmark);
    if (objective == null || objective.getMetrics() == null || objective.getMetrics().isEmpty()) {
      return new JsonObject().put("format", isLegacy(benchmark) ? "legacyRawSignedSum" : "default");
    }
    double totalWeight = objective.getMetrics().stream()
            .mapToDouble(metric -> metric.getWeight() == null ? 1.0 : metric.getWeight())
            .sum();
    JsonArray metrics = new JsonArray();
    for (ObjectiveMetricSpec metric : objective.getMetrics()) {
      NormalizationSpec normalization = metric.getNormalization();
      JsonObject normalizationJson = new JsonObject().put("type", normalization.getType());
      if (normalization.getMin() != null) normalizationJson.put("min", normalization.getMin());
      if (normalization.getMax() != null) normalizationJson.put("max", normalization.getMax());
      if (normalization.getScale() != null) normalizationJson.put("scale", normalization.getScale());
      metrics.add(new JsonObject()
              .put("name", metric.getName())
              .put("direction", metric.getDirection())
              .put("effectiveWeight", (metric.getWeight() == null ? 1.0 : metric.getWeight()) / totalWeight)
              .put("normalization", normalizationJson));
    }
    return new JsonObject().put("format", "structured").put("metrics", metrics);
  }

  private static double legacyScore(JsonObject metrics, Benchmark benchmark, ObjectiveSpec objective) {
    if (objective == null || (objective.getMaximize().isEmpty() && objective.getMinimize().isEmpty())) {
      return numericMetric(metrics, metrics.containsKey("checkout_success_rate")
              ? "checkout_success_rate" : "successRate", benchmark)
              - numericMetric(metrics, metrics.containsKey("iteration_duration_p95")
              ? "iteration_duration_p95" : "p95Latency", benchmark);
    }
    double score = 0.0;
    for (String metric : objective.getMaximize()) score += numericLegacyMetric(metrics, metric, benchmark);
    for (String metric : objective.getMinimize()) score -= numericLegacyMetric(metrics, metric, benchmark);
    return score;
  }

  private static double numericLegacyMetric(JsonObject metrics, String name, Benchmark benchmark) {
    return numericMetric(metrics, name, benchmark);
  }

  private static double numericMetric(JsonObject metrics, String name, Benchmark benchmark) {
    Object value = metrics.getValue(name);
    if (value == null) {
      String benchmarkName = benchmark.getMetadata() == null ? "unknown" : benchmark.getMetadata().getName();
      throw new IllegalArgumentException("Configured objective metric '" + name
              + "' was not found in aggregated result metrics for benchmark '" + benchmarkName
              + "'. Available metrics: " + new ArrayList<>(metrics.fieldNames()));
    }
    double number;
    if (value instanceof Number numeric) {
      number = numeric.doubleValue();
    } else {
      try {
        number = Double.parseDouble(String.valueOf(value));
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException("Configured objective metric '" + name + "' is not numeric", exception);
      }
    }
    if (!Double.isFinite(number)) {
      throw new IllegalArgumentException("Configured objective metric '" + name + "' is not finite");
    }
    return number;
  }

  private static double normalize(double value, ObjectiveMetricSpec metric) {
    NormalizationSpec normalization = metric.getNormalization();
    if (MIN_MAX.equalsIgnoreCase(normalization.getType())) {
      double normalized = (MAXIMIZE.equalsIgnoreCase(metric.getDirection())
              ? value - normalization.getMin() : normalization.getMax() - value)
              / (normalization.getMax() - normalization.getMin());
      return Math.max(0.0, Math.min(1.0, normalized));
    }
    if (value < 0) {
      throw new IllegalArgumentException("Reciprocal objective metric '" + metric.getName()
              + "' must be non-negative");
    }
    return normalization.getScale() / (normalization.getScale() + value);
  }

  private static void validateMetric(ObjectiveMetricSpec metric) {
    validateName(metric.getName(), "objective.metrics.name");
    String direction = metric.getDirection() == null ? "" : metric.getDirection().toLowerCase(Locale.ROOT);
    if (!MAXIMIZE.equals(direction) && !MINIMIZE.equals(direction)) {
      throw new IllegalArgumentException("Unsupported objective direction for '" + metric.getName() + "': " + metric.getDirection());
    }
    if (metric.getWeight() != null && (!Double.isFinite(metric.getWeight()) || metric.getWeight() <= 0)) {
      throw new IllegalArgumentException("Objective weight for '" + metric.getName() + "' must be greater than 0");
    }
    NormalizationSpec normalization = metric.getNormalization();
    if (normalization == null || normalization.getType() == null) {
      throw new IllegalArgumentException("Objective normalization is required for '" + metric.getName() + "'");
    }
    if (MIN_MAX.equalsIgnoreCase(normalization.getType())) {
      if (normalization.getMin() == null || normalization.getMax() == null
              || !Double.isFinite(normalization.getMin()) || !Double.isFinite(normalization.getMax())
              || normalization.getMax() <= normalization.getMin()) {
        throw new IllegalArgumentException("Objective minMax normalization for '" + metric.getName() + "' requires max > min");
      }
    } else if (RECIPROCAL.equalsIgnoreCase(normalization.getType())) {
      if (!MINIMIZE.equals(direction)) {
        throw new IllegalArgumentException("reciprocal normalization is supported only for minimized metrics: " + metric.getName());
      }
      if (normalization.getScale() == null || !Double.isFinite(normalization.getScale()) || normalization.getScale() <= 0) {
        throw new IllegalArgumentException("Objective reciprocal scale for '" + metric.getName() + "' must be greater than 0");
      }
    } else {
      throw new IllegalArgumentException("Unsupported objective normalization for '" + metric.getName() + "': " + normalization.getType());
    }
  }

  private static void validateName(String name, String field) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException(field + " metric name must not be blank");
  }

  private static ObjectiveSpec objective(Benchmark benchmark) {
    return benchmark.getSpec().getStrategy() == null ? null : benchmark.getSpec().getStrategy().getObjective();
  }
}
