import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ComparisonModel } from '../../core/models/comparison.models';
import { ComparisonService } from '../../core/services/comparison.service';
import { RunEvaluationService } from '../../core/services/run-evaluation.service';
import { RunNormalizerService } from '../../core/services/run-normalizer.service';
import { ComparisonComponent } from './comparison.component';

describe('ComparisonComponent', () => {
  let fixture: ComponentFixture<ComparisonComponent>;
  let model: ComparisonModel;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ComparisonComponent], providers: [provideNoopAnimations()] }).compileComponents();
    const service = new ComparisonService(new RunNormalizerService(), new RunEvaluationService());
    model = service.build([input('knnAdaptive', 'a'), input('randomSampling', 'b')]);
    fixture = TestBed.createComponent(ComparisonComponent);
    fixture.componentRef.setInput('model', model);
    fixture.detectChanges();
  });

  it('shows points from every run and one shared reference dataset', () => {
    const datasets = fixture.componentInstance.resultSpaceData().datasets;
    expect(datasets.map((dataset) => dataset.label)).toEqual(['Exhaustive Reference', 'knnAdaptive', 'randomSampling', 'knnAdaptive trajectory']);
    expect(datasets[1].data).toHaveLength(2);
    expect(datasets[2].data).toHaveLength(2);
  });

  it('changes only the displayed trajectory when Focus Run changes', () => {
    fixture.componentInstance.focusedRunId.set(model.runs[1].id);
    const datasets = fixture.componentInstance.resultSpaceData().datasets;
    expect(datasets[1].data).toHaveLength(2);
    expect(datasets.at(-1)?.label).toBe('randomSampling trajectory');
  });

  it('builds one best-so-far convergence series per run with each own length', () => {
    model = {
      ...model,
      runs: model.runs.map((run, index) => index === 0
        ? { ...run, run: { ...run.run, decisions: run.run.decisions.slice(0, 1) } }
        : run),
    };
    fixture.componentRef.setInput('model', model);
    fixture.detectChanges();
    const datasets = fixture.componentInstance.convergenceData().datasets;
    expect(datasets.filter((dataset) => dataset.label !== 'Exhaustive Reference Best')).toHaveLength(2);
    expect(datasets.find((dataset) => dataset.label === 'knnAdaptive')?.data).toHaveLength(1);
    expect(datasets.find((dataset) => dataset.label === 'randomSampling')?.data).toHaveLength(2);
  });
});

function input(strategy: string, id: string) {
  return { label: '', trace: trace(strategy, id), results: { results: [result(100, 25), result(300, 50)] } };
}

function trace(strategy: string, id: string): Record<string, unknown> {
  return {
    schemaVersion: 2,
    benchmark: 'benchmark',
    heuristic: strategy,
    runId: id,
    totalConfigurationSpaceSize: 3,
    decisions: [1, 2].map((decision) => ({
      decision,
      configuration: { hash: `hash-${decision}`, summary: `config-${decision}`, normalized: { connectors: [] } },
      expectedScenarios: [scenarioContext(100, 25), scenarioContext(300, 50)],
      executions: [scenarioContext(100, 25), scenarioContext(300, 50)],
      aggregatedResult: { score: 0.7 + decision / 10, metrics: {} },
    })),
  };
}

function result(users: number, fault: number): Record<string, unknown> {
  return { scenario: `scenario-${users}-${fault}`, workload_name: 'workload', workload_users: users, fault_provider: 'envoy', fault_percentage: fault, fault_services: ['service'], checkout_success_rate: 0.9, iteration_duration_p95: 0.1, connectors: [] };
}

function scenarioContext(users: number, fault: number): Record<string, unknown> {
  return { scenario: `scenario-${users}-${fault}`, workload: { name: 'workload', users }, fault: { provider: 'envoy', percentage: fault, services: ['service'] } };
}
