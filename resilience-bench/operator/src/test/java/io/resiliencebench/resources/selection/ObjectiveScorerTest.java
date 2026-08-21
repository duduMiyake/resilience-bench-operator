package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.benchmark.NormalizationSpec;
import io.resiliencebench.resources.benchmark.ObjectiveMetricSpec;
import io.resiliencebench.resources.benchmark.ObjectiveSpec;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectiveScorerTest {

  @Test
  void min_max_normalization_clamps_and_respects_direction() {
    var maximize = metric("value", "maximize", new NormalizationSpec("minMax", 10.0, 20.0, null));
    var minimize = metric("value", "minimize", new NormalizationSpec("minMax", 10.0, 20.0, null));

    assertEquals(0.0, score(List.of(maximize), new JsonObject().put("value", 10)));
    assertEquals(1.0, score(List.of(maximize), new JsonObject().put("value", 20)));
    assertEquals(1.0, score(List.of(minimize), new JsonObject().put("value", 10)));
    assertEquals(0.0, score(List.of(minimize), new JsonObject().put("value", 20)));
    assertEquals(0.0, score(List.of(maximize), new JsonObject().put("value", 0)));
    assertEquals(1.0, score(List.of(maximize), new JsonObject().put("value", 30)));
  }

  @Test
  void reciprocal_normalization_uses_fixed_scale() {
    var metric = metric("latency", "minimize", new NormalizationSpec("reciprocal", null, null, 22450.0));

    assertEquals(1.0, score(List.of(metric), new JsonObject().put("latency", 0)));
    assertEquals(0.5, score(List.of(metric), new JsonObject().put("latency", 22450)));
    org.junit.jupiter.api.Assertions.assertTrue(score(List.of(metric), new JsonObject().put("latency", 30000)) < 0.5);
  }

  @Test
  void weights_are_normalized_and_omitted_weights_are_equal() {
    var first = metric("first", "maximize", new NormalizationSpec("minMax", 0.0, 1.0, null));
    var second = metric("second", "maximize", new NormalizationSpec("minMax", 0.0, 1.0, null));

    assertEquals(0.5, score(List.of(first, second), new JsonObject().put("first", 1).put("second", 0)));
    first = new ObjectiveMetricSpec("first", "maximize", 7.0,
            new NormalizationSpec("minMax", 0.0, 1.0, null));
    second = new ObjectiveMetricSpec("second", "maximize", 3.0,
            new NormalizationSpec("minMax", 0.0, 1.0, null));
    assertEquals(0.7, score(List.of(first, second), new JsonObject().put("first", 1).put("second", 0)));
  }

  @Test
  void hipstershop_objective_produces_normalized_combined_score() {
    var success = new ObjectiveMetricSpec("checkout_success_rate", "maximize", 0.5,
            new NormalizationSpec("minMax", 0.0, 1.0, null));
    var latency = new ObjectiveMetricSpec("iteration_duration_p(95)", "minimize", 0.5,
            new NormalizationSpec("reciprocal", null, null, 22450.0));

    double expected = 0.5 * 0.972 + 0.5 * 22450.0 / (22450.0 + 22582.0);
    assertEquals(expected, score(List.of(success, latency), new JsonObject()
            .put("checkout_success_rate", 0.972)
            .put("iteration_duration_p(95)", 22582)), 0.0000001);
  }

  @Test
  void missing_metric_fails_without_aliasing_p95_names() {
    var metric = metric("iteration_duration_(95)", "minimize",
            new NormalizationSpec("reciprocal", null, null, 22450.0));

    var exception = assertThrows(IllegalArgumentException.class,
            () -> score(List.of(metric), new JsonObject().put("iteration_duration_p(95)", 100)));

    org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("iteration_duration_(95)"));
  }

  @Test
  void legacy_score_remains_raw_and_mixed_formats_are_rejected() {
    var legacy = benchmark(new ObjectiveSpec(List.of("successRate"), List.of("p95Latency")));
    assertEquals(-98.1, ObjectiveScorer.score(new JsonObject().put("successRate", 0.9).put("p95Latency", 99), legacy));

    var mixed = new ObjectiveSpec(List.of("successRate"), List.of());
    mixed.setMetrics(List.of(metric("successRate", "maximize", null,
            new NormalizationSpec("minMax", 0.0, 1.0, null))));
    assertThrows(IllegalArgumentException.class,
            () -> ObjectiveScorer.validate(benchmark(mixed)));
  }

  @Test
  void invalid_weights_and_normalization_are_rejected() {
    var invalidWeight = metric("value", "maximize", 0.0,
            new NormalizationSpec("minMax", 0.0, 1.0, null));
    assertThrows(IllegalArgumentException.class, () -> ObjectiveScorer.validate(
            benchmark(ObjectiveSpec.structured(List.of(invalidWeight)))));

    var invalidReciprocal = metric("value", "maximize", null,
            new NormalizationSpec("reciprocal", null, null, 1.0));
    assertThrows(IllegalArgumentException.class, () -> ObjectiveScorer.validate(
            benchmark(ObjectiveSpec.structured(List.of(invalidReciprocal)))));
  }

  private static ObjectiveMetricSpec metric(String name, String direction, NormalizationSpec normalization) {
    return metric(name, direction, null, normalization);
  }

  private static ObjectiveMetricSpec metric(String name, String direction, Double weight,
                                            NormalizationSpec normalization) {
    return new ObjectiveMetricSpec(name, direction, weight, normalization);
  }

  private static double score(List<ObjectiveMetricSpec> metrics, JsonObject values) {
    return ObjectiveScorer.score(values, benchmark(ObjectiveSpec.structured(metrics)));
  }

  private static Benchmark benchmark(ObjectiveSpec objective) {
    var benchmark = new Benchmark();
    benchmark.setSpec(new BenchmarkSpec("workload",
            new ScenarioSelectionStrategySpec("knnAdaptive", null, null, null,
                    1, 1, objective), List.of()));
    return benchmark;
  }
}
