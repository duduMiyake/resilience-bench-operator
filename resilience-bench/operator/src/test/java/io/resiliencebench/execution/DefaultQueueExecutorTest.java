package io.resiliencebench.execution;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.resiliencebench.execution.io.FileProvider;
import io.resiliencebench.execution.io.FileProviderFactory;
import io.resiliencebench.execution.resultcache.HeuristicTraceWriter;
import io.resiliencebench.execution.resultcache.ScenarioResultCache;
import io.resiliencebench.resources.benchmark.Benchmark;
import io.resiliencebench.resources.benchmark.BenchmarkSpec;
import io.resiliencebench.resources.queue.ExecutionQueue;
import io.resiliencebench.resources.queue.ExecutionQueueItem;
import io.resiliencebench.resources.queue.ExecutionQueueSpec;
import io.resiliencebench.resources.scenario.Scenario;
import io.resiliencebench.resources.selection.AdaptiveConfigurationSelectionStrategy;
import io.resiliencebench.resources.selection.ConfigurationResultAggregator;
import io.resiliencebench.resources.selection.ScenarioSelectionStrategySelector;
import io.resiliencebench.resources.workload.Workload;
import io.resiliencebench.support.CustomResourceRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Optional;

import static io.resiliencebench.resources.queue.ExecutionQueueItem.Status.FINISHED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultQueueExecutorTest {

  @Test
  void should_complete_trace_only_after_queue_is_done_and_no_adaptive_candidate_exists() {
    @SuppressWarnings("unchecked")
    var scenarioRepository = (CustomResourceRepository<Scenario>) mock(CustomResourceRepository.class);
    @SuppressWarnings("unchecked")
    var executionRepository = (CustomResourceRepository<ExecutionQueue>) mock(CustomResourceRepository.class);
    @SuppressWarnings("unchecked")
    var benchmarkRepository = (CustomResourceRepository<Benchmark>) mock(CustomResourceRepository.class);
    @SuppressWarnings("unchecked")
    var workloadRepository = (CustomResourceRepository<Workload>) mock(CustomResourceRepository.class);
    var scenarioExecutor = mock(ScenarioExecutor.class);
    var strategySelector = mock(ScenarioSelectionStrategySelector.class);
    var fileProviderFactory = mock(FileProviderFactory.class);
    var resultCache = mock(ScenarioResultCache.class);
    var traceWriter = mock(HeuristicTraceWriter.class);
    var fileProvider = mock(FileProvider.class);
    when(fileProviderFactory.create()).thenReturn(fileProvider);

    var item = new ExecutionQueueItem("scenario", "/results/items/scenario.json");
    item.setStatus(FINISHED);
    var queueMetadata = new ObjectMeta();
    queueMetadata.setName("benchmark");
    queueMetadata.setNamespace("default");
    var queue = new ExecutionQueue(new ExecutionQueueSpec(
            "/results/runs/heuristics/benchmark/knnAdaptive/run-1/results.json",
            new ArrayList<>(java.util.List.of(item)),
            "benchmark"), queueMetadata);
    var benchmark = mock(Benchmark.class);
    var benchmarkSpec = mock(BenchmarkSpec.class);
    var workload = mock(Workload.class);
    var adaptiveStrategy = mock(AdaptiveConfigurationSelectionStrategy.class);

    when(executionRepository.find(queue.getMetadata())).thenReturn(Optional.of(queue));
    when(benchmarkRepository.find("default", "benchmark")).thenReturn(Optional.of(benchmark));
    when(benchmark.getSpec()).thenReturn(benchmarkSpec);
    when(benchmarkSpec.getWorkload()).thenReturn("workload");
    when(workloadRepository.find("default", "workload")).thenReturn(Optional.of(workload));
    when(scenarioRepository.list("default")).thenReturn(java.util.List.of());
    when(strategySelector.selectAdaptive(benchmark)).thenReturn(Optional.of(adaptiveStrategy));
    when(adaptiveStrategy.selectNextDecision(any(), any(), any(), any(), any()))
            .thenReturn(Optional.empty());

    var executor = new DefaultQueueExecutor(
            scenarioRepository, executionRepository, benchmarkRepository, workloadRepository,
            scenarioExecutor, strategySelector, fileProviderFactory, resultCache, traceWriter,
            new ConfigurationResultAggregator());

    executor.execute(queue);

    verify(traceWriter).recordRunCompleted(benchmark, queue);
  }
}
