import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { UIChart } from 'primeng/chart';
import { RunNormalizerService } from '../../core/services/run-normalizer.service';
import { ExplorerStateService } from '../../core/services/explorer-state.service';
import { ResultSpaceComponent } from './result-space.component';

describe('ResultSpaceComponent navigation', () => {
  let fixture: ComponentFixture<ResultSpaceComponent>;
  let state: ExplorerStateService;
  const normalizer = new RunNormalizerService();

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResultSpaceComponent],
      providers: [provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(ResultSpaceComponent);
    state = TestBed.inject(ExplorerStateService);
    state.setRun(normalizer.normalize(trace(), { results: results() }));
    expect(state.showSearchPath()).toBe(true);
  });

  it('derives trajectory roles in decision order without Previous or Next categories', () => {
    state.selectDecision(2);
    const datasets = fixture.componentInstance.data().datasets;

    expect(datasetDecisions(datasets, 'Start')).toEqual([1]);
    expect(datasetDecisions(datasets, 'Current Decision')).toEqual([2]);
    expect(datasetDecisions(datasets, 'Best Found')).toEqual([3]);
    expect(datasetDecisions(datasets, 'Previous Decision')).toEqual([]);
    expect(datasetDecisions(datasets, 'Next Decision')).toEqual([]);

    state.selectDecision(1);
    expect(datasetDecisions(fixture.componentInstance.data().datasets, 'Current Decision')).toEqual([1]);
    state.selectDecision(3);
    expect(datasetDecisions(fixture.componentInstance.data().datasets, 'Current Decision')).toEqual([3]);
    expect(datasetDecisions(fixture.componentInstance.data().datasets, 'Best Found')).toEqual([]);
  });

  it('shows the selected decision position for direct navigation', () => {
    state.selectDecision(2);

    expect(fixture.componentInstance.selectedPosition()).toBe(2);
    expect(fixture.componentInstance.hasPreviousDecision()).toBe(true);
    expect(fixture.componentInstance.hasNextDecision()).toBe(true);

    state.selectDecision(1);
    expect(fixture.componentInstance.hasPreviousDecision()).toBe(false);
    state.selectDecision(3);
    expect(fixture.componentInstance.hasNextDecision()).toBe(false);
  });

  it('resolves Focus Selected for the active context and skips missing points', () => {
    const chart = mockChart();
    fixture.componentInstance.chart = chart as unknown as UIChart;
    state.selectDecision(2);

    fixture.componentInstance.focusSelected();
    expect(chart.chart.zoomScale).toHaveBeenCalledTimes(2);

    state.workloadUsers.set(999);
    fixture.componentInstance.focusSelected();
    expect(chart.chart.zoomScale).toHaveBeenCalledTimes(2);
  });

  it('auto-focuses when global selection changes and reset restores the viewport', async () => {
    const focus = vi.spyOn(fixture.componentInstance, 'focusSelected').mockImplementation(() => {});
    fixture.componentInstance.autoFocusSelected.set(true);
    fixture.detectChanges();
    await Promise.resolve();
    focus.mockClear();

    state.selectDecision(2);
    fixture.detectChanges();
    await Promise.resolve();
    await Promise.resolve();
    expect(focus).toHaveBeenCalled();

    const chart = mockChart();
    fixture.componentInstance.chart = chart as unknown as UIChart;
    fixture.componentInstance.resetZoom();
    expect(chart.chart.resetZoom).toHaveBeenCalledWith('active');
  });
});

function datasetDecisions(datasets: Array<{ label?: string; data: unknown[] }>, label: string): number[] {
  return (datasets.find((dataset) => dataset.label === label)?.data ?? [])
    .map((point) => (point as { decision?: number }).decision)
    .filter((decision): decision is number => decision !== undefined);
}

function mockChart() {
  return {
    chart: {
      scales: {
        x: { min: 0, max: 1 },
        y: { min: 0, max: 1 },
      },
      getInitialScaleBounds: () => ({ x: { min: 0, max: 1 }, y: { min: 0, max: 1 } }),
      zoomScale: vi.fn(),
      resetZoom: vi.fn(),
    },
  };
}

function trace(): Record<string, unknown> {
  return {
    schemaVersion: 2,
    benchmark: 'benchmark',
    heuristic: 'knnAdaptive',
    runId: 'run',
    totalConfigurationSpaceSize: 3,
    decisions: [1, 2, 3].map((decision) => ({
      decision,
      phase: decision === 1 ? 'initialSample' : 'adaptiveSelection',
      selectionMode: decision === 1 ? 'INITIAL_BATCH' : 'SEQUENTIAL',
      configuration: { hash: `hash-${decision}`, summary: `config-${decision}`, normalized: { connectors: [configuration(String(decision))] } },
      expectedScenarios: [scenarioContext(decision)],
      aggregatedResult: { score: decision / 10, currentScore: decision / 10, improvedBest: decision > 1, metrics: {} },
    })),
  };
}

function results(): Record<string, unknown>[] {
  return [1, 2, 3].map((decision) => ({
    scenario: `scenario-${decision}`,
    workload_name: 'workload',
    workload_users: 100,
    fault_provider: 'envoy',
    fault_percentage: 25,
    fault_services: ['service'],
    checkout_success_rate: 0.7 + decision / 10,
    iteration_duration_p95: decision,
    connectors: [configuration(String(decision))],
  }));
}

function configuration(name: string): Record<string, unknown> {
  return { name, source: 'source', destination: 'destination', source_env_RETRY: '1' };
}

function scenarioContext(decision: number): Record<string, unknown> {
  return {
    scenario: `scenario-${decision}`,
    workload: { name: 'workload', users: 100 },
    fault: { provider: 'envoy', percentage: 25, services: ['service'] },
  };
}
