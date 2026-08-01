package io.resiliencebench;

import java.util.List;

import io.resiliencebench.execution.QueueExecutor;
import io.resiliencebench.execution.resultcache.HeuristicTraceWriter;
import io.resiliencebench.execution.resultcache.ResultStoragePathFactory;
import io.resiliencebench.resources.selection.ConfigurationSelectionDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.resiliencebench.resources.ExecutionQueueFactory;
import io.resiliencebench.resources.ScenarioFactory;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkStatus;
import io.resiliencebench.resources.benchmark.ScenarioSelectionStrategySpec;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.selection.ScenarioSelectionStrategySelector;
import io.resiliencebench.resources.selection.configuration.ScenarioConfigurationIndex;
import io.resiliencebench.resources.workload.Workload;
import io.resiliencebench.support.CustomResourceRepository;

@ControllerConfiguration
public class BenchmarkController implements Reconciler<Benchmark> {

  private static final Logger logger = LoggerFactory.getLogger(BenchmarkController.class);

  private final CustomResourceRepository<Scenario> scenarioRepository;

  private final CustomResourceRepository<Workload> workloadRepository;
  private final CustomResourceRepository<ExecutionQueue> queueRepository;

  private final QueueExecutor queueExecutor;
  private final ScenarioSelectionStrategySelector scenarioSelectionStrategySelector;
  private final HeuristicTraceWriter heuristicTraceWriter;

  public BenchmarkController(QueueExecutor queueExecutor,
                             CustomResourceRepository<Scenario> scenarioRepository,
                             CustomResourceRepository<Workload> workloadRepository,
                             CustomResourceRepository<ExecutionQueue> queueRepository,
                             ScenarioSelectionStrategySelector scenarioSelectionStrategySelector,
                             HeuristicTraceWriter heuristicTraceWriter) {
    this.queueExecutor = queueExecutor;
    this.scenarioRepository = scenarioRepository;
    this.workloadRepository = workloadRepository;
    this.queueRepository = queueRepository;
    this.scenarioSelectionStrategySelector = scenarioSelectionStrategySelector;
    this.heuristicTraceWriter = heuristicTraceWriter;
  }

  // Considering only creation and update events. if something changes in benchmark, we need to re-run the scenarios
  @Override
  public UpdateControl<Benchmark> reconcile(Benchmark benchmark, Context<Benchmark> context) {
    var existingQueue = queueRepository.find(benchmark.getMetadata().getNamespace(), benchmark.getMetadata().getName());
    if (existingQueue.isPresent()) {
      logger.info("Queue already exists for benchmark {}. Reusing existing queue.", benchmark.getMetadata().getName());
      queueExecutor.execute(existingQueue.get());
      return UpdateControl.noUpdate();
    }

    var workload = workloadRepository.find(benchmark.getMetadata().getNamespace(), benchmark.getSpec().getWorkload());
    if (workload.isEmpty()) {
      logger.error("Workload not found: {}", benchmark.getSpec().getWorkload());
      return UpdateControl.noUpdate();
    }

    SelectionResult selectionResult;
    try {
      selectionResult = createScenariosForQueue(benchmark, workload.get());
    } catch (IllegalArgumentException e) {
      logger.error("Invalid scenario selection strategy for benchmark {}. {}",
              benchmark.getMetadata().getName(),
              e.getMessage());
      benchmark.setStatus(new BenchmarkStatus(0, e.getMessage()));
      return UpdateControl.updateStatus(benchmark);
    }

    if (selectionResult.scenarios().isEmpty()) {
      logger.error("No scenarios generated for benchmark {}", benchmark.getMetadata().getName());
      return UpdateControl.noUpdate();
    }

    var executionQueue = prepareToRunScenarios(benchmark, selectionResult.scenarios());
    recordInitialSelections(benchmark, executionQueue, selectionResult);

    logger.info("Benchmark reconciled {}. {} scenarios created",
            benchmark.getMetadata().getName(),
            selectionResult.scenarios().size()
    );
    benchmark.setStatus(new BenchmarkStatus(selectionResult.scenarios().size()));
    queueExecutor.execute(executionQueue);
    return UpdateControl.updateStatus(benchmark);
  }

  private SelectionResult createScenariosForQueue(Benchmark benchmark, Workload workload) {
    scenarioRepository.deleteAll(benchmark.getMetadata().getNamespace()); // TODO we don't support (yet) multiple reconciles loops

    var allScenarios = ScenarioFactory.create(benchmark, workload);
    var allConfigurations = ScenarioConfigurationIndex.from(allScenarios);
    var selectedStrategy = scenarioSelectionStrategySelector.select(benchmark);
    var scenariosList = selectedStrategy.selectScenarios(allScenarios, benchmark, workload);
    var selectedConfigurations = ScenarioConfigurationIndex.from(scenariosList);
    logStrategySelection(benchmark, allScenarios.size(), allConfigurations.totalConfigurations(),
            scenariosList.size(), selectedConfigurations.totalConfigurations());

    if (scenarioSelectionStrategySelector.selectAdaptive(benchmark).isPresent()) {
      allScenarios.forEach(scenarioRepository::create);
    } else {
      scenariosList.forEach(scenarioRepository::create);
    }
    return new SelectionResult(scenariosList, allConfigurations, selectedConfigurations);
  }

  private void recordInitialSelections(Benchmark benchmark, ExecutionQueue queue, SelectionResult selectionResult) {
    var phase = initialPhase(benchmark);
    var heuristic = ResultStoragePathFactory.strategyType(benchmark);
    var decisions = selectionResult.selectedConfigurations().keys().stream()
            .map(configuration -> ConfigurationSelectionDecision.of(configuration, heuristic))
            .toList();
    var remainingConfigurations = Math.max(0,
            selectionResult.allConfigurations().totalConfigurations() - selectionResult.selectedConfigurations().totalConfigurations());
    heuristicTraceWriter.recordSelectedConfigurations(benchmark, queue, selectionResult.allConfigurations(), decisions,
            phase, 0, remainingConfigurations);
  }

  private void logStrategySelection(Benchmark benchmark, int totalScenarios, int totalConfigurations,
                                    int selectedScenarios, int selectedConfigurations) {
    var strategy = benchmark.getSpec().getStrategy();
    var strategyType = strategy == null || strategy.getType() == null || strategy.getType().isBlank()
            ? ScenarioSelectionStrategySpec.EXHAUSTIVE
            : strategy.getType();
    var seed = strategy == null || strategy.getSeed() == null ? ScenarioSelectionStrategySpec.DEFAULT_SEED : strategy.getSeed();
    var sampleRate = strategy == null || strategy.getSampleRate() == null ? null : strategy.getSampleRate();
    var maxScenarios = strategy == null || strategy.getMaxScenarios() == null ? null : strategy.getMaxScenarios();
    var maxConfigurations = strategy == null || strategy.getMaxConfigurations() == null ? null : strategy.getMaxConfigurations();
    var initialSamples = strategy == null || strategy.getInitialSamples() == null ? null : strategy.getInitialSamples();
    var maxEvaluations = strategy == null || strategy.getMaxEvaluations() == null ? null : strategy.getMaxEvaluations();

    logger.info("Scenario selection strategy={} totalPossibleScenarios={} totalConfigurations={} selectedScenarios={} selectedConfigurations={} seed={} sampleRate={} maxScenarios={} maxConfigurations={} initialSamples={} maxEvaluations={}",
            strategyType,
            totalScenarios,
            totalConfigurations,
            selectedScenarios,
            selectedConfigurations,
            seed,
            sampleRate,
            maxScenarios,
            maxConfigurations,
            initialSamples,
            maxEvaluations);
  }

  private ExecutionQueue prepareToRunScenarios(Benchmark benchmark, List<Scenario> scenariosList) {
    queueRepository.deleteAll(benchmark.getMetadata().getNamespace());
    var queueCreated = ExecutionQueueFactory.create(benchmark, scenariosList);
    return queueRepository.create(queueCreated);
  }

  private static String initialPhase(Benchmark benchmark) {
    var type = ResultStoragePathFactory.strategyType(benchmark);
    if (ScenarioSelectionStrategySpec.KNN_ADAPTIVE.equalsIgnoreCase(type)) {
      return "initialSample";
    }
    return type;
  }

  private record SelectionResult(List<Scenario> scenarios,
                                 ScenarioConfigurationIndex allConfigurations,
                                 ScenarioConfigurationIndex selectedConfigurations) {
  }
}
