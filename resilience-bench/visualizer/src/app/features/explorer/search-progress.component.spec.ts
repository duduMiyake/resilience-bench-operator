import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { RunNormalizerService } from '../../core/services/run-normalizer.service';
import { ExplorerStateService } from '../../core/services/explorer-state.service';
import { SearchProgressComponent } from './search-progress.component';

describe('SearchProgressComponent selection', () => {
  let fixture: ComponentFixture<SearchProgressComponent>;
  let state: ExplorerStateService;
  const normalizer = new RunNormalizerService();

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SearchProgressComponent],
      providers: [provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(SearchProgressComponent);
    state = TestBed.inject(ExplorerStateService);
    state.setRun(normalizer.normalize(trace()));
  });

  it('highlights the same global selected decision used by Result Space', () => {
    state.selectDecision(2);
    const selected = fixture.componentInstance.data().datasets.find((dataset) => dataset.label === 'Selected Decision');

    expect(selected?.data).toEqual([null, 0.2, null]);
    const labels = fixture.componentInstance.data().datasets.map((dataset) => dataset.label);
    expect(labels).toContain('Observed Score');
    expect(labels).not.toContain('Observed Score - Initial Sample');
    expect(labels).not.toContain('Observed Score - Adaptive Search');
    expect(fixture.componentInstance.initialRange()).toBe('#1');
    expect(fixture.componentInstance.adaptiveRange()).toBe('#2-3');
    expect(fixture.componentInstance.data().datasets.find((dataset) => dataset.label === 'Best Score So Far')?.data).toEqual([0.1, 0.2, 0.3]);
    state.selectDecision(3);
    expect(fixture.componentInstance.data().datasets.find((dataset) => dataset.label === 'Selected Decision')?.data).toEqual([null, null, 0.3]);
  });
});

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
      configuration: { hash: `hash-${decision}`, summary: `config-${decision}`, normalized: { connectors: [] } },
      expectedScenarios: [],
      aggregatedResult: { score: decision / 10, currentScore: decision / 10, bestScoreSoFar: decision / 10, metrics: {} },
    })),
  };
}
