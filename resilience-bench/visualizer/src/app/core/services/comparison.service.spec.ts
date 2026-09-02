import { ComparisonService } from './comparison.service';
import { RunEvaluationService } from './run-evaluation.service';
import { RunNormalizerService } from './run-normalizer.service';
import { ObjectiveScorer } from './objective-scorer.service';

describe('ComparisonService', () => {
  const service = new ComparisonService(new RunNormalizerService(), new RunEvaluationService(new ObjectiveScorer()));

  it('loads two or more runs in insertion order and assigns stable identities', () => {
    const model = service.build([input('knnAdaptive', 'a'), input('randomSampling', 'b')]);
    expect(model.errors).toEqual([]);
    expect(model.runs.map((run) => run.label)).toEqual(['knnAdaptive', 'randomSampling']);
    expect(model.runs.map((run) => run.pointStyle)).toEqual(['circle', 'triangle']);
    expect(model.runs[0].color).not.toBe(model.runs[1].color);
  });

  it('rejects different benchmark, contexts, and search-space size', () => {
    const differentBenchmark = input('randomSampling', 'b');
    differentBenchmark.trace = { ...trace('other-benchmark', 'b'), totalConfigurationSpaceSize: 9 };
    differentBenchmark.results = { results: [result(300, 50)] };
    const model = service.build([input('knnAdaptive', 'a'), differentBenchmark]);
    expect(model.errors.join(' ')).toMatch(/benchmark/);
    expect(model.errors.join(' ')).toMatch(/contexts/);
    expect(model.errors.join(' ')).toMatch(/configuration-space/);
  });

  it('accepts different budgets and emits a warning', () => {
    const first = input('knnAdaptive', 'a');
    const second = input('randomSampling', 'b');
    (second.trace as Record<string, unknown>)['maxEvaluations'] = 20;
    (first.trace as Record<string, unknown>)['maxEvaluations'] = 10;
    const model = service.build([first, second]);
    expect(model.errors).toEqual([]);
    expect(model.warnings.join(' ')).toContain('different evaluation budgets');
  });

  it('supports a shared reference and also works without one', () => {
    const withoutReference = service.build([input('a', 'a'), input('b', 'b')]);
    expect(withoutReference.referenceCompatible).toBe(false);
    const reference = { results: [result(100, 25)] };
    const withReference = service.build([input('a', 'a'), input('b', 'b')], reference);
    expect(withReference.referenceResults).toHaveLength(1);
  });

  it('accepts runs with equal structured objectives', () => {
    const first = input('knnAdaptive', 'a');
    const second = input('randomSampling', 'b');
    (first.trace as Record<string, unknown>)['objective'] = structuredObjective();
    (second.trace as Record<string, unknown>)['objective'] = structuredObjective();

    expect(service.build([first, second]).errors).toEqual([]);
  });

  it('rejects runs with different effective weights', () => {
    const first = input('knnAdaptive', 'a');
    const second = input('randomSampling', 'b');
    (first.trace as Record<string, unknown>)['objective'] = structuredObjective(0.5);
    (second.trace as Record<string, unknown>)['objective'] = structuredObjective(0.6);

    expect(service.build([first, second]).errors.join(' ')).toContain('different objective scoring semantics');
  });

  it('rejects runs with different normalization parameters', () => {
    const first = input('knnAdaptive', 'a');
    const second = input('randomSampling', 'b');
    (first.trace as Record<string, unknown>)['objective'] = structuredObjective(0.5, 22450);
    (second.trace as Record<string, unknown>)['objective'] = structuredObjective(0.5, 30000);

    expect(service.build([first, second]).errors.join(' ')).toContain('different objective scoring semantics');
  });
});

function input(strategy: string, id: string) {
  return { label: strategy, trace: trace('benchmark', id, strategy), results: { results: [result(100, 25)] } };
}

function trace(benchmark: string, id: string, strategy = 'knnAdaptive'): Record<string, unknown> {
  return {
    schemaVersion: 2,
    benchmark,
    heuristic: strategy,
    runId: id,
    totalConfigurationSpaceSize: 3,
    decisions: [{
      decision: 1,
      configuration: { hash: 'hash-a', summary: 'config-a', normalized: { connectors: [] } },
      expectedScenarios: [scenarioContext(100, 25)],
      aggregatedResult: { score: 0.8, metrics: {} },
    }],
  };
}

function result(users: number, fault: number): Record<string, unknown> {
  return {
    scenario: `scenario-${users}-${fault}`,
    workload_name: 'workload',
    workload_users: users,
    fault_provider: 'envoy',
    fault_percentage: fault,
    fault_services: ['service'],
    checkout_success_rate: 0.9,
    iteration_duration_p95: 0.1,
    connectors: [],
  };
}

function scenarioContext(users: number, fault: number): Record<string, unknown> {
  return { scenario: `scenario-${users}-${fault}`, workload: { name: 'workload', users }, fault: { provider: 'envoy', percentage: fault, services: ['service'] } };
}

function structuredObjective(successWeight = 0.5, scale = 22450): Record<string, unknown> {
  return {
    format: 'structured',
    metrics: [
      { name: 'success', direction: 'maximize', effectiveWeight: successWeight, normalization: { type: 'minMax', min: 0, max: 1 } },
      { name: 'latency', direction: 'minimize', effectiveWeight: 1 - successWeight, normalization: { type: 'reciprocal', scale } },
    ],
  };
}
