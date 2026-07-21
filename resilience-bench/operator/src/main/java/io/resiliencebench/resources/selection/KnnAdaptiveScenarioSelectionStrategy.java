package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.selection.configuration.ResilienceConfigurationKey;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import io.resiliencebench.resources.workload.Workload;
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
  public Optional<ResilienceConfigurationKey> selectNextConfiguration(
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
              .findFirst();
    }

    Map<ResilienceConfigurationKey, Map<String, Double>> vectors = encodeConfigurations(allConfigurations);
    return candidates.stream()
            .max(Comparator.comparingDouble(candidate ->
                    selectionScore(candidate, evaluatedConfigurations, vectors, benchmark)));
  }

  private static double selectionScore(ResilienceConfigurationKey candidate,
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
                    distance(candidateVector, vectors.get(evaluatedConfiguration.getConfigurationKey()))))
            .sorted(Comparator.comparingDouble(Neighbor::distance))
            .limit(neighbors)
            .toList();

    if (nearestNeighbors.isEmpty()) {
      return 0.0;
    }

    double weightedScore = 0.0;
    double totalWeight = 0.0;
    double uncertainty = nearestNeighbors.get(0).distance();
    for (Neighbor neighbor : nearestNeighbors) {
      double weight = 1.0 / (neighbor.distance() + 0.000001);
      weightedScore += weight * objectiveScore(neighbor.evaluatedConfiguration(), benchmark);
      totalWeight += weight;
    }

    double predictedScore = weightedScore / totalWeight;
    return predictedScore + explorationWeight * uncertainty;
  }

  private record Neighbor(EvaluatedConfiguration evaluatedConfiguration, double distance) {
  }
}