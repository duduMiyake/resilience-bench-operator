package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.selection.configuration.ResilienceConfigurationKey;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import io.resiliencebench.resources.workload.Workload;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.configurationBudget;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.distance;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.encodeConfigurations;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.initialConfigurations;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.objectiveScore;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.seed;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.selectSpaceFillingConfigurations;

@Component
public class KnnAdaptiveScenarioSelectionStrategy implements AdaptiveConfigurationSelectionStrategy {

  @Override
  public List<ResilienceConfigurationKey> selectConfigurations(ScenarioConfigurationIndex configurationIndex,
                                                               Benchmark benchmark,
                                                               Workload workload) {
    if (configurationIndex.totalConfigurations() == 0) {
      return List.of();
    }

    return List.copyOf(selectSpaceFillingConfigurations(
            configurationIndex,
            seed(benchmark),
            initialConfigurations(configurationIndex, benchmark)));
  }

  @Override
  public Optional<ConfigurationSelectionDecision> selectNextDecision(
          ScenarioConfigurationIndex configurationIndex,
          Set<ResilienceConfigurationKey> alreadyQueuedConfigurations,
          List<EvaluatedConfiguration> evaluatedConfigurations,
          Benchmark benchmark,
          Workload workload) {
    var allConfigurations = configurationIndex.keys();
    if (allConfigurations.isEmpty()) {
      return Optional.empty();
    }

    int budget = configurationBudget(configurationIndex, benchmark);
    if (alreadyQueuedConfigurations.size() >= budget) {
      return Optional.empty();
    }

    List<ResilienceConfigurationKey> candidates = allConfigurations.stream()
            .filter(configuration -> !alreadyQueuedConfigurations.contains(configuration))
            .toList();
    if (candidates.isEmpty()) {
      return Optional.empty();
    }

    if (evaluatedConfigurations.isEmpty()) {
      Set<ResilienceConfigurationKey> queued = new HashSet<>(alreadyQueuedConfigurations);
      return selectSpaceFillingConfigurations(configurationIndex, seed(benchmark), allConfigurations.size()).stream()
              .filter(configuration -> !queued.contains(configuration))
              .findFirst()
              .map(configuration -> ConfigurationSelectionDecision.of(
                      configuration,
                      ScenarioSelectionStrategySpec.KNN_ADAPTIVE,
                      new JsonObject().put("selectionMode", "spaceFilling")));
    }

    Map<ResilienceConfigurationKey, Map<String, Double>> vectors = encodeConfigurations(allConfigurations);
    return candidates.stream()
            .map(candidate -> evaluateCandidate(candidate, evaluatedConfigurations, vectors, benchmark))
            .max(Comparator.comparingDouble(SelectionEvaluation::selectionScore))
            .map(evaluation -> ConfigurationSelectionDecision.of(
                    evaluation.configuration(),
                    ScenarioSelectionStrategySpec.KNN_ADAPTIVE,
                    evaluation.toMetadata()));
  }

  private static SelectionEvaluation evaluateCandidate(ResilienceConfigurationKey candidate,
                                                       List<EvaluatedConfiguration> evaluatedConfigurations,
                                                       Map<ResilienceConfigurationKey, Map<String, Double>> vectors,
                                                       Benchmark benchmark) {
    var strategy = benchmark.getSpec().getStrategy();
    int neighbors = strategy.getNeighbors() == null
            ? ScenarioSelectionStrategySpec.DEFAULT_NEIGHBORS
            : strategy.getNeighbors();
    double explorationWeight = strategy.getExplorationWeight() == null
            ? ScenarioSelectionStrategySpec.DEFAULT_EXPLORATION_WEIGHT
            : strategy.getExplorationWeight();

    Map<String, Double> candidateVector = vectors.get(candidate);
    List<Neighbor> nearestNeighbors = evaluatedConfigurations.stream()
            .filter(EvaluatedConfiguration::isComplete)
            .filter(evaluatedConfiguration -> vectors.containsKey(evaluatedConfiguration.getConfigurationKey()))
            .map(evaluatedConfiguration -> new Neighbor(
                    evaluatedConfiguration,
                    distance(candidateVector, vectors.get(evaluatedConfiguration.getConfigurationKey())),
                    objectiveScore(evaluatedConfiguration, benchmark)))
            .sorted(Comparator.comparingDouble(Neighbor::distance))
            .limit(neighbors)
            .toList();

    if (nearestNeighbors.isEmpty()) {
      return new SelectionEvaluation(candidate, 0.0, 0.0, 0.0, 0.0, nearestNeighbors);
    }

    double weightedScore = 0.0;
    double totalWeight = 0.0;
    double uncertainty = nearestNeighbors.get(0).distance();
    for (Neighbor neighbor : nearestNeighbors) {
      double weight = 1.0 / (neighbor.distance() + 0.000001);
      weightedScore += weight * neighbor.realScore();
      totalWeight += weight;
    }

    double predictedScore = weightedScore / totalWeight;
    double explorationBonus = explorationWeight * uncertainty;
    return new SelectionEvaluation(candidate, predictedScore, uncertainty, explorationBonus,
            predictedScore + explorationBonus, nearestNeighbors);
  }

  private record SelectionEvaluation(ResilienceConfigurationKey configuration,
                                     double predictedScore,
                                     double uncertainty,
                                     double explorationBonus,
                                     double selectionScore,
                                     List<Neighbor> nearestNeighbors) {

    JsonObject toMetadata() {
      return new JsonObject()
              .put("predictedScore", predictedScore)
              .put("uncertainty", uncertainty)
              .put("explorationBonus", explorationBonus)
              .put("selectionScore", selectionScore)
              .put("nearestNeighbors", new JsonArray(nearestNeighbors.stream()
                      .map(Neighbor::toJson)
                      .toList()));
    }
  }

  private record Neighbor(EvaluatedConfiguration evaluatedConfiguration, double distance, double realScore) {

    JsonObject toJson() {
      var configuration = evaluatedConfiguration.getConfigurationKey();
      return new JsonObject()
              .put("configurationHash", configuration.hash())
              .put("configurationSummary", configuration.summary())
              .put("distance", distance)
              .put("realScore", realScore)
              .put("expectedContexts", evaluatedConfiguration.getExpectedContexts())
              .put("completedContexts", evaluatedConfiguration.getCompletedContexts());
    }
  }
}
