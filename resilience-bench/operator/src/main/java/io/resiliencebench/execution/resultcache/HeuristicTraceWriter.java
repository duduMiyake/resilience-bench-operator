package io.resiliencebench.execution.resultcache;

import io.resiliencebench.execution.io.FileProvider;
import io.resiliencebench.execution.io.FileProviderFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.selection.EvaluatedScenario;
import io.resiliencebench.support.CustomResourceRepository;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.distance;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.encode;
import static io.resiliencebench.resources.selection.ScenarioSelectionSupport.objectiveScore;
import static io.resiliencebench.support.Annotations.OWNED_BY;

@Service
public class HeuristicTraceWriter {

  private final FileProvider fileProvider;
  private final ScenarioCacheKeyFactory cacheKeyFactory;
  private final ScenarioResultCache scenarioResultCache;
  private final CustomResourceRepository<Scenario> scenarioRepository;

  public HeuristicTraceWriter(FileProviderFactory fileProviderFactory,
                              ScenarioCacheKeyFactory cacheKeyFactory,
                              ScenarioResultCache scenarioResultCache,
                              CustomResourceRepository<Scenario> scenarioRepository) {
    this.fileProvider = fileProviderFactory.create();
    this.cacheKeyFactory = cacheKeyFactory;
    this.scenarioResultCache = scenarioResultCache;
    this.scenarioRepository = scenarioRepository;
  }

  public void appendStep(Benchmark benchmark, ExecutionQueue queue, Scenario scenario, String phase,
                         String source, JsonObject result) {
    if (!scenarioResultCache.isReadWriteEnabled(benchmark)) {
      return;
    }
    var traceFile = ResultStoragePathFactory.traceFile(benchmark, runIdFromResultFile(queue.getSpec().getResultFile()));
    var trace = fileProvider.getFileAsString(traceFile)
            .map(JsonObject::new)
            .orElseGet(() -> createTrace(benchmark, queue));
    var steps = trace.getJsonArray("steps", new JsonArray());
    var step = new JsonObject()
            .put("step", steps.size() + 1)
            .put("phase", phase)
            .put("scenario", scenario.getMetadata().getName())
            .put("scenarioHash", cacheKeyFactory.hash(scenario))
            .put("source", source)
            .put("selectedAt", LocalDateTime.now(ZoneOffset.UTC).toString())
            .put("resultScore", scenarioResultCache.resultScore(result));
    selectionDetails(benchmark, queue, scenario, phase).ifPresent(details -> step.put("selection", details));
    steps.add(step);
    trace.put("steps", steps);
    fileProvider.writeToFile(traceFile, trace.encode());
  }

  private java.util.Optional<JsonObject> selectionDetails(Benchmark benchmark, ExecutionQueue queue,
                                                          Scenario scenario, String phase) {
    var strategy = benchmark.getSpec().getStrategy();
    if (strategy == null
            || !ScenarioSelectionStrategySpec.KNN_ADAPTIVE.equalsIgnoreCase(strategy.getType())
            || !"adaptiveSelection".equals(phase)) {
      return java.util.Optional.empty();
    }

    var existingResults = fileProvider.getFileAsString(queue.getSpec().getResultFile());
    if (existingResults.isEmpty()) {
      return java.util.Optional.empty();
    }
    var evaluatedScenarios = new JsonObject(existingResults.get()).getJsonArray("results", new JsonArray()).stream()
            .filter(JsonObject.class::isInstance)
            .map(JsonObject.class::cast)
            .filter(result -> result.getString("scenario") != null)
            .map(result -> new EvaluatedScenario(result.getString("scenario"), result))
            .toList();
    if (evaluatedScenarios.isEmpty()) {
      return java.util.Optional.empty();
    }

    var allScenarios = scenarioRepository.list(queue.getMetadata().getNamespace()).stream()
            .filter(item -> item.getMetadata().getAnnotations() != null)
            .filter(item -> benchmark.getMetadata().getName().equals(item.getMetadata().getAnnotations().get(OWNED_BY)))
            .toList();
    var vectors = encode(allScenarios);
    var candidateVector = vectors.get(scenario.getMetadata().getName());
    if (candidateVector == null) {
      return java.util.Optional.empty();
    }

    int neighbors = strategy.getNeighbors() == null
            ? ScenarioSelectionStrategySpec.DEFAULT_NEIGHBORS
            : strategy.getNeighbors();
    double explorationWeight = strategy.getExplorationWeight() == null
            ? ScenarioSelectionStrategySpec.DEFAULT_EXPLORATION_WEIGHT
            : strategy.getExplorationWeight();

    List<NeighborTrace> nearestNeighbors = evaluatedScenarios.stream()
            .filter(evaluated -> vectors.containsKey(evaluated.getScenarioName()))
            .map(evaluated -> new NeighborTrace(
                    evaluated,
                    distance(candidateVector, vectors.get(evaluated.getScenarioName())),
                    objectiveScore(evaluated, benchmark)))
            .sorted(Comparator.comparingDouble(NeighborTrace::distance))
            .limit(neighbors)
            .toList();
    if (nearestNeighbors.isEmpty()) {
      return java.util.Optional.empty();
    }

    double weightedScore = 0.0;
    double totalWeight = 0.0;
    for (NeighborTrace neighbor : nearestNeighbors) {
      double weight = 1.0 / (neighbor.distance() + 0.000001);
      weightedScore += weight * neighbor.realScore();
      totalWeight += weight;
    }
    double predictedScore = weightedScore / totalWeight;
    double uncertainty = nearestNeighbors.get(0).distance();
    double explorationBonus = explorationWeight * uncertainty;

    var neighborsJson = new JsonArray(nearestNeighbors.stream()
            .map(neighbor -> new JsonObject()
                    .put("scenario", neighbor.evaluatedScenario().getScenarioName())
                    .put("distance", neighbor.distance())
                    .put("realScore", neighbor.realScore()))
            .toList());
    return java.util.Optional.of(new JsonObject()
            .put("predictedScore", predictedScore)
            .put("explorationBonus", explorationBonus)
            .put("selectionScore", predictedScore + explorationBonus)
            .put("uncertainty", uncertainty)
            .put("nearestNeighbors", neighborsJson));
  }

  private static JsonObject createTrace(Benchmark benchmark, ExecutionQueue queue) {
    return new JsonObject()
            .put("benchmark", benchmark.getMetadata().getName())
            .put("strategy", ResultStoragePathFactory.strategyType(benchmark))
            .put("runId", runIdFromResultFile(queue.getSpec().getResultFile()))
            .put("resultFile", queue.getSpec().getResultFile())
            .put("startedAt", LocalDateTime.now(ZoneOffset.UTC).toString())
            .put("steps", new JsonArray());
  }

  private static String runIdFromResultFile(String resultFile) {
    var normalized = resultFile.replace("\\", "/");
    if (normalized.endsWith("/results.json")) {
      var parent = normalized.substring(0, normalized.length() - "/results.json".length());
      return parent.substring(parent.lastIndexOf('/') + 1);
    }
    var fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
    return fileName.replace("-results.json", "");
  }

  private record NeighborTrace(EvaluatedScenario evaluatedScenario, double distance, double realScore) {
  }
}
