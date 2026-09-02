import { RunEvaluationService } from './run-evaluation.service';
import { RunNormalizerService } from './run-normalizer.service';
import { ObjectiveScorer } from './objective-scorer.service';

describe('RunEvaluationService', () => {
  const normalizer = new RunNormalizerService();
  const service = new RunEvaluationService(new ObjectiveScorer());

  it('uses trace scores with maximize semantics and calculates search outcome metrics', () => {
    const trace = heuristicTrace([0.6, 0.8, 0.7], 10);
    const decisions = trace['decisions'] as Array<Record<string, unknown>>;
    (decisions[0]['aggregatedResult'] as Record<string, unknown>)['currentScore'] = 99;

    const evaluation = service.evaluate(normalizer.normalize(trace));

    expect(evaluation.objectiveDirection).toBe('maximize');
    expect(evaluation.bestObservedScore).toBe(0.8);
    expect(evaluation.bestFoundAtDecision).toBe(2);
    expect(evaluation.evaluatedConfigurations).toBe(3);
    expect(evaluation.exploredRatio).toBeCloseTo(0.3);
    expect(evaluation.searchSpaceReduction).toBeCloseTo(0.7);
  });

  it('scores the exhaustive reference with the structured objective from the trace', () => {
    const trace = heuristicTrace([0.7352661600], 1, [configuration('a')]);
    trace['objective'] = structuredObjective();
    const referenceResult = result('reference', 'a', 100, 25, 0.972, 22582.18567095);
    referenceResult['iteration_duration_p(95)'] = 22582.18567095;
    const reference = { results: [referenceResult] };

    const evaluation = service.evaluate(normalizer.normalize(trace, undefined, reference));

    expect(evaluation.referenceCompatible).toBe(true);
    expect(evaluation.referenceBestScore).toBeCloseTo(0.7352661600, 10);
    expect(evaluation.absoluteGap).toBeCloseTo(0, 10);
  });

  it('rejects a structured reference missing an explicitly named metric', () => {
    const trace = heuristicTrace([0.7352661600], 1, [configuration('a')]);
    trace['objective'] = structuredObjective();
    const referenceResult = result('reference', 'a', 100, 25, 0.972, 22582.18567095);
    delete referenceResult['iteration_duration_p95'];

    const evaluation = service.evaluate(normalizer.normalize(trace, undefined, { results: [referenceResult] }));

    expect(evaluation.referenceCompatible).toBe(false);
    expect(evaluation.referenceCompatibilityReason).toContain("'iteration_duration_p(95)'");
  });

  it('does not use the legacy raw subtraction for structured traces', () => {
    const trace = heuristicTrace([0.7352661600], 1, [configuration('a')]);
    trace['objective'] = structuredObjective();
    const referenceResult = result('reference', 'a', 100, 25, 0.972, 22582.18567095);
    referenceResult['iteration_duration_p(95)'] = 22582.18567095;
    const reference = { results: [referenceResult] };

    const evaluation = service.evaluate(normalizer.normalize(trace, undefined, reference));

    expect(evaluation.referenceBestScore).not.toBeCloseTo(0.972 - 22582.18567095, 5);
  });

  it('groups exhaustive scenarios by connector configuration and reproduces the Operator aggregate', () => {
    const trace = heuristicTrace([0.8], 2, [configuration('a')]);
    const runResults = {
      results: [result('heuristic-a-1', 'a', 100, 25, 0.85, 0.2)],
    };
    const reference = {
      results: [
        result('reference-a-1', 'a', 100, 25, 0.9, 0.1),
        result('reference-a-2', 'a', 300, 50, 0.9, 0.1),
        result('reference-b-1', 'b', 100, 25, 0.95, 0.1),
        result('reference-b-2', 'b', 300, 50, 0.95, 0.1),
      ],
    };
    const expected = (trace['decisions'] as Array<Record<string, unknown>>)[0]['expectedScenarios'] as unknown[];
    expected.push(scenarioContext(300, 50));

    const evaluation = service.evaluate(normalizer.normalize(trace, runResults, reference));

    expect(evaluation.referenceCompatible).toBe(true);
    expect(evaluation.referenceBestScore).toBeCloseTo(0.85);
    expect(evaluation.absoluteGap).toBeCloseTo(0.05);
    expect(evaluation.relativeGap).toBeCloseTo(0.05 / 0.85 * 100);
  });

  it('finds the first decision reaching each quality threshold and represents not reached explicitly', () => {
    const trace = heuristicTrace([0.7, 0.9, 0.93, 0.96], 4, [
      configuration('a'), configuration('b'), configuration('c'), configuration('d'),
    ]);
    const reference = { results: ['a', 'b', 'c', 'd', 'best'].map((config) =>
      result(`reference-${config}`, config, 100, 25, config === 'best' ? 1 : Number.NaN, 0),
    ) };
    reference.results.forEach((item, index) => {
      if (index < 4) item['checkout_success_rate'] = [0.7, 0.9, 0.93, 0.96][index];
    });

    const evaluation = service.evaluate(normalizer.normalize(trace, undefined, reference));

    expect(evaluation.qualityThresholds).toEqual([
      { threshold: 0.9, reachedAtDecision: 2 },
      { threshold: 0.95, reachedAtDecision: 4 },
      { threshold: 0.99, reachedAtDecision: undefined },
    ]);
  });

  it('invalidates aggregate comparison for incomplete coverage but keeps common per-context metrics', () => {
    const trace = heuristicTrace([0.8], 1, [configuration('a')]);
    const expected = (trace['decisions'] as Array<Record<string, unknown>>)[0]['expectedScenarios'] as unknown[];
    expected.push(scenarioContext(300, 50));
    const runResults = { results: [
      result('heuristic-1', 'a', 100, 25, 0.8, 0.3),
      result('heuristic-2', 'a', 300, 50, 0.9, 0.2),
    ] };
    const reference = { results: [result('reference-1', 'a', 100, 25, 0.95, 0.1)] };

    const evaluation = service.evaluate(normalizer.normalize(trace, runResults, reference));

    expect(evaluation.referenceCompatible).toBe(false);
    expect(evaluation.referenceBestScore).toBeUndefined();
    expect(evaluation.absoluteGap).toBeUndefined();
    expect(evaluation.relativeGap).toBeUndefined();
    expect(evaluation.qualityThresholds.every((threshold) => threshold.reachedAtDecision === undefined)).toBe(true);
    expect(evaluation.contextEvaluations).toEqual([{
      context: '100 users · 25% fault',
      heuristicSuccessRate: 0.8,
      referenceSuccessRate: 0.95,
      heuristicP95: 0.3,
      referenceP95: 0.1,
    }]);
  });

  it('keeps exhaustive results out of heuristic Best Found', () => {
    const trace = heuristicTrace([0.4], 2, [configuration('a')]);
    const reference = { results: [result('reference', 'b', 100, 25, 100, 0)] };

    expect(service.evaluate(normalizer.normalize(trace, undefined, reference)).bestObservedScore).toBe(0.4);
  });

  it('aggregates the k6 iteration_duration_p(95) metric for reference scores', () => {
    const trace = heuristicTrace([0.8], 1, [configuration('a')]);
    const reference = { results: [result('reference', 'a', 100, 25, 0.9, 0.1)] };
    reference.results[0]['iteration_duration_p(95)'] = 0.1;
    delete reference.results[0]['iteration_duration_p95'];

    const evaluation = service.evaluate(normalizer.normalize(trace, undefined, reference));

    expect(evaluation.referenceCompatible).toBe(true);
    expect(evaluation.referenceBestScore).toBeCloseTo(0.8);
  });

  it('rejects a shared configuration when frontend aggregation diverges from its trace score', () => {
    const trace = heuristicTrace([0.81], 1, [configuration('a')]);
    const reference = { results: [result('reference', 'a', 100, 25, 0.9, 0.1)] };

    const evaluation = service.evaluate(normalizer.normalize(trace, undefined, reference));

    expect(evaluation.referenceCompatible).toBe(false);
    expect(evaluation.referenceCompatibilityReason).toContain('does not match');
  });
});

function heuristicTrace(
  scores: number[],
  total: number,
  configurations = scores.map((_, index) => configuration(String(index))),
): Record<string, unknown> {
  return {
    schemaVersion: 2,
    benchmark: 'benchmark',
    heuristic: 'knnAdaptive',
    runId: 'run',
    totalConfigurationSpaceSize: total,
    decisions: scores.map((score, index) => ({
      decision: index + 1,
      configuration: {
        hash: `hash-${index}`,
        summary: `configuration-${index}`,
        normalized: configurations[index],
      },
      selection: { heuristic: 'knnAdaptive' },
      expectedScenarios: [scenarioContext(100, 25)],
      aggregatedResult: { score, metrics: {} },
    })),
  };
}

function configuration(name: string): Record<string, unknown> {
  return { connectors: [{ name, source: 'source', destination: 'destination', source_env_RETRY: '1' }] };
}

function structuredObjective(): Record<string, unknown> {
  return {
    format: 'structured',
    metrics: [
      {
        name: 'checkout_success_rate',
        direction: 'maximize',
        effectiveWeight: 0.5,
        normalization: { type: 'minMax', min: 0, max: 1 },
      },
      {
        name: 'iteration_duration_p(95)',
        direction: 'minimize',
        effectiveWeight: 0.5,
        normalization: { type: 'reciprocal', scale: 22450 },
      },
    ],
  };
}

function scenarioContext(users: number, fault: number): Record<string, unknown> {
  return {
    scenario: `scenario-${users}-${fault}`,
    workload: { name: 'workload', users },
    fault: { provider: 'envoy', percentage: fault, services: ['service'] },
  };
}

function result(
  scenario: string,
  config: string,
  users: number,
  fault: number,
  success: number,
  p95: number,
): Record<string, unknown> {
  return {
    scenario,
    workload_name: 'workload',
    workload_users: users,
    fault_provider: 'envoy',
    fault_percentage: fault,
    fault_services: ['service'],
    checkout_success_rate: success,
    iteration_duration_p95: p95,
    connectors: (configuration(config)['connectors'] as unknown[]),
  };
}
