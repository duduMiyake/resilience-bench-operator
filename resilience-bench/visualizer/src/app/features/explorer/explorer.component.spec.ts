import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { RunNormalizerService } from '../../core/services/run-normalizer.service';
import { ExplorerComponent } from './explorer.component';

describe('ExplorerComponent Search Outcome', () => {
  let fixture: ComponentFixture<ExplorerComponent>;
  const normalizer = new RunNormalizerService();

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExplorerComponent],
      providers: [provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(ExplorerComponent);
  });

  it('formats outcome values with the requested precision and context', () => {
    fixture.componentRef.setInput('run', normalizer.normalize(trace([0.97504], 1369)));
    const component = fixture.componentInstance;

    expect(component.formatOutcomeMetric(0.97504)).toBe('0.9750');
    expect(component.formatRelativeGap(0.1366)).toBe('0.14%');
    expect(component.formatOutcomePercent(0.015)).toBe('1.5%');
  });

  it('formats operational context metrics and directions for users', () => {
    const component = fixture.componentInstance;

    expect(component.formatContextSuccessRate(0.975)).toBe('97.50%');
    expect(component.formatContextDuration(20613.6881)).toBe('20.61 s');
    expect(component.formatSuccessGap(0.975, 0.9763)).toBe('0.13 pp below reference');
    expect(component.formatSuccessGap(0.98, 0.9763)).toBe('0.37 pp above reference');
    expect(component.formatSuccessGap(0.9763, 0.9763)).toBe('Matches reference');
    expect(component.formatLatencyGap(20613.6881, 19612.1702)).toBe('1.00 s slower');
    expect(component.formatLatencyGap(19000, 19612.1702)).toBe('0.61 s faster');
    expect(component.formatLatencyGap(19612.1702, 19612.1702)).toBe('Matches reference');
    expect(component.formatContextDuration(undefined)).toBe('—');
    expect(component.formatSuccessGap(undefined, 0.9)).toBe('—');
  });
  it('renders the recorded weights and direction-aware normalization formulas', () => {
    const run = normalizer.normalize(trace([0.75], 20));
    run.objective = { format: 'structured', metrics: [
      { name: 'checkout_success_rate', direction: 'maximize', effectiveWeight: 0.5, normalization: { type: 'minMax', min: 0, max: 1 } },
      { name: 'iteration_duration_p(95)', direction: 'minimize', effectiveWeight: 0.5, normalization: { type: 'reciprocal', scale: 22450 } },
    ] };
    fixture.componentRef.setInput('run', run);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.score-formula')?.textContent).toContain('Score =');
    expect(element.querySelectorAll('.formula-term')).toHaveLength(2);
    expect(element.querySelector('.score-definitions')?.textContent).toContain('22450 / (22450 + m2)');
    const component = fixture.componentInstance;
    expect(component.normalizationLabel(run.objective.metrics[0])).toBe('clamp((m1 − 0) / (1 − 0), 0, 1)');
    expect(component.normalizationLabel({ ...run.objective.metrics[0], direction: 'minimize' })).toBe('clamp((1 − m1) / (1 − 0), 0, 1)');
  });

  it('summarizes a compatible comparison and exact matches', () => {
    fixture.componentRef.setInput('run', normalizer.normalize(trace([0.975], 20), undefined, reference(0.975, 0.9763)));
    expect(fixture.componentInstance.outcomeSummary()).toContain('0.13% below the exhaustive reference best (0.9763)');

    fixture.componentRef.setInput('run', normalizer.normalize(trace([0.9763], 20), undefined, reference(0.9763, 0.9763)));
    expect(fixture.componentInstance.outcomeSummary()).toContain('matches the exhaustive reference best (0.9763)');
  });

  it('uses the short summary and hides reference groups when comparison is unavailable', () => {
    fixture.componentRef.setInput('run', normalizer.normalize(trace([0.975], 20)));
    expect(fixture.componentInstance.outcomeSummary()).toBe('Best heuristic result: 0.9750, found at Decision #1.');

    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('The trace does not describe a structured objective');
    expect(text).not.toContain('theoretical maximum score is 1');
    expect(text).not.toContain('Reference Comparison');
    expect(text).not.toContain('Reference Best');
  });

  it('renders readable quality milestones, including not reached', () => {
    fixture.componentRef.setInput('run', normalizer.normalize(trace([0.9], 20), undefined, reference(0.9, 1)));
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('90% of reference');
    expect(text).toContain('Reached at Decision #1');
    expect(text).toContain('95% of reference');
    expect(text).toContain('Not reached');
  });
});

function trace(scores: number[], total: number): Record<string, unknown> {
  return {
    schemaVersion: 2,
    benchmark: 'benchmark',
    heuristic: 'knnAdaptive',
    runId: 'run',
    totalConfigurationSpaceSize: total,
    decisions: scores.map((score, index) => ({
      decision: index + 1,
      configuration: { hash: `hash-${index}`, summary: 'configuration', normalized: { connectors: [configuration('a')] } },
      expectedScenarios: [scenarioContext()],
      aggregatedResult: { score, metrics: {} },
    })),
  };
}

function reference(sharedScore: number, bestScore: number): { results: Record<string, unknown>[] } {
  return { results: [
    { ...result(sharedScore, 'a'), connectors: [configuration('a')] },
    { ...result(bestScore, 'b'), connectors: [configuration('b')] },
  ] };
}

function result(score: number, config: string): Record<string, unknown> {
  return {
    scenario: `scenario-${config}`, workload_name: 'workload', workload_users: 100,
    fault_provider: 'envoy', fault_percentage: 25, fault_services: ['service'],
    checkout_success_rate: score, iteration_duration_p95: 0, connectors: [configuration(config)],
  };
}

function configuration(name: string): Record<string, unknown> {
  return { name, source: 'source', destination: 'destination', source_env_RETRY: '1' };
}

function scenarioContext(): Record<string, unknown> {
  return {
    scenario: 'scenario',
    workload: { name: 'workload', users: 100 },
    fault: { provider: 'envoy', percentage: 25, services: ['service'] },
  };
}
