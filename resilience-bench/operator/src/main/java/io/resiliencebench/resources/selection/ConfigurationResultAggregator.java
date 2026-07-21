package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.selection.configuration.ResilienceConfigurationKey;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import io.vertx.core.json.JsonObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ConfigurationResultAggregator {

  public List<EvaluatedConfiguration> completeEvaluations(ScenarioConfigurationIndex configurationIndex,
                                                          List<EvaluatedScenario> evaluatedScenarios) {
    Map<String, EvaluatedScenario> resultByScenarioName = new LinkedHashMap<>();
    for (EvaluatedScenario evaluatedScenario : evaluatedScenarios) {
      resultByScenarioName.put(evaluatedScenario.getScenarioName(), evaluatedScenario);
    }

    List<EvaluatedConfiguration> evaluations = new ArrayList<>();
    for (ResilienceConfigurationKey key : configurationIndex.keys()) {
      aggregate(configurationIndex, key, resultByScenarioName)
              .filter(EvaluatedConfiguration::isComplete)
              .ifPresent(evaluations::add);
    }
    return evaluations;
  }

  public Optional<EvaluatedConfiguration> aggregate(ScenarioConfigurationIndex configurationIndex,
                                                   ResilienceConfigurationKey key,
                                                   Map<String, EvaluatedScenario> resultByScenarioName) {
    var scenarios = configurationIndex.scenariosFor(key);
    if (scenarios.isEmpty()) {
      return Optional.empty();
    }

    var results = scenarios.stream()
            .map(scenario -> resultByScenarioName.get(scenario.getMetadata().getName()))
            .filter(java.util.Objects::nonNull)
            .toList();
    if (results.isEmpty()) {
      return Optional.of(new EvaluatedConfiguration(key, new JsonObject()
              .put("expectedContexts", scenarios.size())
              .put("completedContexts", 0)
              .put("complete", false), scenarios.size(), 0));
    }

    Map<String, DoubleSummary> summaries = new LinkedHashMap<>();
    for (EvaluatedScenario result : results) {
      for (String field : result.getMetrics().fieldNames()) {
        var value = result.getMetrics().getValue(field);
        if (value instanceof Number number) {
          summaries.computeIfAbsent(field, ignored -> new DoubleSummary()).accept(number.doubleValue());
        }
      }
    }

    var metrics = new JsonObject()
            .put("expectedContexts", scenarios.size())
            .put("completedContexts", results.size())
            .put("complete", results.size() == scenarios.size());
    for (Map.Entry<String, DoubleSummary> entry : summaries.entrySet()) {
      var summary = entry.getValue();
      metrics.put(entry.getKey(), summary.mean());
      metrics.put(entry.getKey() + "_mean", summary.mean());
      metrics.put(entry.getKey() + "_min", summary.min());
      metrics.put(entry.getKey() + "_max", summary.max());
    }

    return Optional.of(new EvaluatedConfiguration(key, metrics, scenarios.size(), results.size()));
  }

  private static class DoubleSummary {
    private int count;
    private double sum;
    private double min = Double.MAX_VALUE;
    private double max = -Double.MAX_VALUE;

    void accept(double value) {
      count++;
      sum += value;
      min = Math.min(min, value);
      max = Math.max(max, value);
    }

    double mean() {
      return count == 0 ? 0.0 : sum / count;
    }

    double min() {
      return count == 0 ? 0.0 : min;
    }

    double max() {
      return count == 0 ? 0.0 : max;
    }
  }
}
