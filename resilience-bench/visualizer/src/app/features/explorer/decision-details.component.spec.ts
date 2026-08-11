import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ExplorerStateService } from '../../core/services/explorer-state.service';
import { RunNormalizerService } from '../../core/services/run-normalizer.service';
import { DecisionDetailsComponent } from './decision-details.component';

describe('DecisionDetailsComponent', () => {
  let fixture: ComponentFixture<DecisionDetailsComponent>;
  let state: ExplorerStateService;
  const normalizer = new RunNormalizerService();

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DecisionDetailsComponent],
      providers: [provideNoopAnimations()],
    }).compileComponents();

    state = TestBed.inject(ExplorerStateService);
    state.setRun(normalizer.normalize(traceWithKnnMetadata()));
    fixture = TestBed.createComponent(DecisionDetailsComponent);
  });

  it('explains Initial Sample without claiming KNN metadata is missing', () => {
    state.selectDecision(1);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('This configuration belongs to the initial sample.');
    expect(text).toContain('selected before the adaptive KNN search begins');
    expect(text).not.toContain('does not include enough KNN metadata');
    expect(fixture.nativeElement.querySelector('.formula-row')).toBeNull();
  });

  it('renders deterministic adaptive KNN explanation and formula values', () => {
    state.selectDecision(2);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('The heuristic predicted a score of 0.933');
    expect(text).toContain('Exploration Bonus');
    expect(text).toContain('Selection Score');
    expect(text).toContain('1.089');
    expect(text).toContain('observed score was 0.968');
    expect(text).toContain('best known score remained 0.972');
  });
});

function traceWithKnnMetadata(): Record<string, unknown> {
  return {
    schemaVersion: 2,
    benchmark: 'hipstershop',
    heuristic: 'knnAdaptive',
    runId: 'run-1',
    decisions: [
      {
        decision: 1,
        phase: 'initialSample',
        selectionMode: 'INITIAL_BATCH',
        configuration: { hash: 'initial', summary: 'initial config', normalized: { connectors: [] } },
        selection: { heuristic: 'knnAdaptive', metadata: {} },
        aggregatedResult: { currentScore: 0.9523, bestScoreSoFar: 0.9523, improvedBest: true, metrics: {} },
      },
      {
        decision: 2,
        phase: 'adaptiveSelection',
        selectionMode: 'SEQUENTIAL',
        configuration: { hash: 'adaptive', summary: 'adaptive config', normalized: { connectors: [] } },
        selection: {
          heuristic: 'knnAdaptive',
          metadata: {
            predictedScore: 0.933,
            uncertainty: 1.563,
            explorationBonus: 0.156,
            selectionScore: 1.089,
          },
        },
        aggregatedResult: { currentScore: 0.968, bestScoreSoFar: 0.972, improvedBest: false, metrics: {} },
      },
    ],
  };
}
