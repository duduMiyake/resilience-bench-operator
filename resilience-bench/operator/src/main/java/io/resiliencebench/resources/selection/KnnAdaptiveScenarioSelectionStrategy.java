package io.resiliencebench.resources.selection;

import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.workload.Workload;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.distance;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.encode;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.evaluationBudget;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.initialSamples;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.objectiveScore;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.seed;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.selectSpaceFillingScenarios;

@Component
public class KnnAdaptiveScenarioSelectionStrategy implements AdaptiveScenarioSelectionStrategy {

  @Override
  public List<Scenario> selectScenarios(List<Scenario> allScenarios, Benchmark benchmark, Workload workload) {
    if (allScenarios.isEmpty()) {
      return List.of();
    }

    return List.copyOf(selectSpaceFillingScenarios(allScenarios, seed(benchmark), initialSamples(allScenarios, benchmark)));
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

    if (evaluatedScenarios.isEmpty()) {
      Set<String> queued = new HashSet<>(alreadyQueuedScenarioNames);
      return selectSpaceFillingScenarios(allScenarios, seed(benchmark), allScenarios.size()).stream()
              .filter(scenario -> !queued.contains(scenario.getMetadata().getName()))
              .findFirst();
    }

    Map<String, Map<String, Double>> vectors = encode(allScenarios);
    return candidates.stream()
            .max(Comparator.comparingDouble(candidate ->
                    selectionScore(candidate, evaluatedScenarios, vectors, benchmark)));
  }

  private static double selectionScore(Scenario candidate,
                                       List<EvaluatedScenario> evaluatedScenarios,
                                       Map<String, Map<String, Double>> vectors,
                                       Benchmark benchmark) {
    var strategy = benchmark.getSpec().getStrategy();
    int neighbors = strategy.getNeighbors() == null
            ? ScenarioSelectionStrategySpec.DEFAULT_NEIGHBORS
            : strategy.getNeighbors();
    double explorationWeight = strategy.getExplorationWeight() == null
            ? ScenarioSelectionStrategySpec.DEFAULT_EXPLORATION_WEIGHT
            : strategy.getExplorationWeight();

    Map<String, Double> candidateVector = vectors.get(candidate.getMetadata().getName());
    List<Neighbor> nearestNeighbors = evaluatedScenarios.stream()
            .filter(evaluatedScenario -> vectors.containsKey(evaluatedScenario.getScenarioName()))
            .map(evaluatedScenario -> new Neighbor(
                    evaluatedScenario,
                    distance(candidateVector, vectors.get(evaluatedScenario.getScenarioName()))))
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
      weightedScore += weight * objectiveScore(neighbor.evaluatedScenario(), benchmark);
      totalWeight += weight;
    }

    double predictedScore = weightedScore / totalWeight;
    return predictedScore + explorationWeight * uncertainty;
  }

  private record Neighbor(EvaluatedScenario evaluatedScenario, double distance) {
  }
}
