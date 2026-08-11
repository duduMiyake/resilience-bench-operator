import { ExplorerStateService } from './explorer-state.service';
import { RunNormalizerService } from './run-normalizer.service';
import { VisualizerParseError } from '../models/visualizer.models';
import { vi } from 'vitest';

describe('RunNormalizerService', () => {
  const service = new RunNormalizerService();

  it('normalizes trace v2, sorts events, and preserves optional metadata safely', () => {
    const run = service.normalize(v2Trace());

    expect(run.kind).toBe('heuristic');
    expect(run.schemaVersion).toBe(2);
    expect(run.benchmark).toBe('hipstershop');
    expect(run.decisions).toHaveLength(2);
    expect(run.decisions[1].metadata).toEqual({});
    expect(run.events.map((event) => event.sequence)).toEqual([1, 2]);
    expect(run.decisions[0].configuration.connectors[0].parameters).toContainEqual({
      path: 'source_env_GRPC_MAX_ATTEMPTS',
      value: '3',
    });
  });

  it('rejects invalid JSON with a friendly parser error', () => {
    expect(() => service.parseJson('{broken', 'trace.json')).toThrowError(VisualizerParseError);
    expect(() => service.parseJson('{broken', 'trace.json')).toThrowError(/JSON/);
  });

  it('joins by scenarioHash before considering scenario name', () => {
    const results = {
      results: [
        result('renamed-scenario', 'hash-one', 100, 25),
        result('scenario-one', 'different-hash', 300, 50),
      ],
    };

    const run = service.normalize(v2Trace(), results);
    const execution = run.decisions[0].executions[0];

    expect(execution.joinedBy).toBe('scenarioHash');
    expect(execution.result?.scenario).toBe('renamed-scenario');
    expect(execution.result?.scenarioHash).toBe('hash-one');
  });

  it('falls back to scenario name when a hash is not available', () => {
    const trace = v2Trace();
    const second = (trace['decisions'] as Array<Record<string, unknown>>)[1];
    second['executions'] = [{ scenario: 'scenario-two', source: 'executed' }];

    const run = service.normalize(trace, {
      results: [result('scenario-two', 'hash-two', 300, 50)],
    });

    expect(run.decisions[1].executions[0].joinedBy).toBe('scenarioName');
    expect(run.decisions[1].joinedResults[0].scenarioHash).toBe('hash-two');
  });

  it('collects workload and fault contexts and filters them independently', () => {
    const run = service.normalize(v2Trace(), {
      results: [
        result('scenario-one', 'hash-one', 100, 25),
        result('scenario-two', 'hash-two', 300, 50),
      ],
    });
    const state = new ExplorerStateService();
    state.setRun(run);

    expect(run.contexts.map((context) => context.key)).toEqual(['100|25', '300|50']);

    state.workloadUsers.set(300);
    state.faultPercentage.set(50);

    expect(state.visibleResults().map((item) => item.scenario)).toEqual(['scenario-two']);
    expect(state.visibleDecisions().map((item) => item.decision)).toEqual([1, 2]);
  });

  it('creates a normalized model that does not depend on later raw mutations', () => {
    const trace = v2Trace();
    const results = { results: [result('scenario-one', 'hash-one', 100, 25)] };
    const run = service.normalize(trace, results);

    trace['benchmark'] = 'changed';
    (
      (trace['decisions'] as Array<Record<string, unknown>>)[0]['configuration'] as Record<
        string,
        unknown
      >
    )['summary'] = 'changed';
    (results.results[0] as Record<string, unknown>)['checkout_success_rate'] = 0;
    ((results.results[0] as Record<string, unknown>)['connectors'] as unknown[]).push({
      name: 'late-change',
    });

    expect(run.benchmark).toBe('hipstershop');
    expect(run.decisions[0].configuration.summary).toContain('retry-checkout');
    expect(run.results[0].checkoutSuccessRate).toBe(0.91);
    expect(run.results[0].connectors).toEqual([]);
  });

  it('supports exhaustive legacy without synthesizing heuristic decisions', () => {
    const run = service.normalize({
      strategy: 'exhaustive',
      benchmark: 'hipstershop',
      steps: [
        {
          result: {
            scenario: 'baseline',
            checkout_success_rate: 0.99,
            'iteration_duration_p(95)': 0.12,
            workload_users: 100,
            fault_percentage: 25,
          },
        },
      ],
    });

    expect(run.kind).toBe('legacy-exhaustive');
    expect(run.decisions).toEqual([]);
    expect(run.events).toEqual([]);
    expect(run.results).toHaveLength(1);
    expect(run.results[0].iterationDurationP95).toBe(0.12);
    expect(run.warnings[0]).toContain('Legacy exhaustive trace');
  });

  it('keeps exhaustive reference data separate from heuristic run results', () => {
    const run = service.normalize(
      v2Trace(),
      { results: [result('scenario-one', 'hash-one', 100, 25)] },
      { results: [result('reference-only', 'reference-hash', 100, 25)] },
    );

    expect(run.results.map((item) => item.scenario)).toEqual(['scenario-one']);
    expect(run.referenceResults.map((item) => item.scenario)).toEqual(['reference-only']);
    expect(run.referenceResults[0].joinedDecision).toBeUndefined();
  });

  it('supports multiple operational contexts while keeping search progress decisions unfiltered', () => {
    const run = service.normalize(v2Trace(), {
      results: [
        result('scenario-one', 'hash-one', 100, 25),
        result('scenario-two', 'hash-two', 100, 50),
        result('scenario-three', 'hash-three', 300, 25),
        result('scenario-four', 'hash-four', 300, 50),
      ],
    });
    const state = new ExplorerStateService();
    state.setRun(run);

    expect(run.contexts.map((context) => context.key)).toEqual(['100|25', '100|50', '300|25', '300|50']);

    state.workloadUsers.set(300);
    state.faultPercentage.set(50);

    expect(state.visibleResults().map((item) => item.scenario)).toEqual(['scenario-four']);
    expect(state.visibleDecisions().map((item) => item.decision)).toEqual([1, 2]);
  });

  it('normalizes mixed Cache Hit and Executed scenario sources', () => {
    const trace = v2Trace();
    const first = (trace['decisions'] as Array<Record<string, unknown>>)[0];
    first['executions'] = [
      { scenario: 'scenario-one', scenarioHash: 'hash-one', source: 'cacheHit', resultScore: 0.8 },
      { scenario: 'scenario-two', scenarioHash: 'hash-two', source: 'executed', resultScore: 0.7 },
    ];

    const run = service.normalize(trace, {
      results: [result('scenario-one', 'hash-one', 100, 25), result('scenario-two', 'hash-two', 300, 50)],
    });

    expect(run.decisions[0].executions.map((execution) => execution.source)).toEqual(['cacheHit', 'executed']);
  });

  it('playback advances, pauses, stops at the final decision, and manual selection pauses it', () => {
    vi.useFakeTimers();
    const state = new ExplorerStateService();
    state.setRun(service.normalize(v2Trace()));

    state.selectNext();
    expect(state.selectedDecisionNumber()).toBe(2);
    state.selectPrevious();
    expect(state.selectedDecisionNumber()).toBe(1);

    state.play();
    expect(state.isPlaying()).toBe(true);
    vi.advanceTimersByTime(1200);
    expect(state.selectedDecisionNumber()).toBe(2);
    expect(state.isPlaying()).toBe(false);

    state.selectPrevious();
    state.play();
    state.selectDecision(2);
    expect(state.isPlaying()).toBe(false);

    vi.useRealTimers();
  });


  it('Best Found identifies the best observed heuristic decision and ignores reference results', () => {
    const trace = v2Trace();
    const decisions = trace['decisions'] as Array<Record<string, unknown>>;
    (decisions[1]['aggregatedResult'] as Record<string, unknown>)['score'] = 0.95;
    (decisions[1]['aggregatedResult'] as Record<string, unknown>)['bestScoreSoFar'] = 0.95;
    (decisions[1]['aggregatedResult'] as Record<string, unknown>)['improvedBest'] = true;

    const run = service.normalize(
      trace,
      { results: [result('scenario-one', 'hash-one', 100, 25), result('scenario-two', 'hash-two', 300, 50)] },
      { results: [highReferenceResult()] },
    );
    const state = new ExplorerStateService();
    state.setRun(run);

    expect(state.bestFound()).toEqual({ decision: 2, score: 0.95 });
  });

  it('View Decision selects the best decision and pauses playback', () => {
    vi.useFakeTimers();
    const trace = v2Trace();
    const decisions = trace['decisions'] as Array<Record<string, unknown>>;
    (decisions[1]['aggregatedResult'] as Record<string, unknown>)['score'] = 0.95;
    const state = new ExplorerStateService();
    state.setRun(service.normalize(trace));

    state.play();
    expect(state.isPlaying()).toBe(true);
    state.selectBestFound();

    expect(state.selectedDecisionNumber()).toBe(2);
    expect(state.isPlaying()).toBe(false);
    vi.useRealTimers();
  });

  it('Search Path follows decision order and only includes heuristic points in the active context', () => {
    const run = service.normalize(v2Trace(), {
      results: [
        result('scenario-two', 'hash-two', 300, 50),
        result('scenario-one', 'hash-one', 100, 25),
      ],
    });
    const state = new ExplorerStateService();
    state.setRun(run);

    expect(state.searchPath().map((point) => point.decision)).toEqual([1, 2]);

    state.workloadUsers.set(300);
    state.faultPercentage.set(50);

    expect(state.searchPath().map((point) => point.decision)).toEqual([2]);
  });

  it('Search Path skips decisions without a result in the selected context and never includes reference points', () => {
    const run = service.normalize(
      v2Trace(),
      { results: [result('scenario-one', 'hash-one', 100, 25)] },
      { results: [result('reference-only', 'reference-hash', 100, 25)] },
    );
    const state = new ExplorerStateService();
    state.setRun(run);

    expect(state.searchPath().map((point) => point.decision)).toEqual([1]);
    expect(state.searchPath().some((point) => point.decision === undefined)).toBe(false);
  });

  it('global selected decision is shared by playback, manual selection, and best selection', () => {
    vi.useFakeTimers();
    const trace = v2Trace();
    const decisions = trace['decisions'] as Array<Record<string, unknown>>;
    (decisions[1]['aggregatedResult'] as Record<string, unknown>)['score'] = 0.95;
    const state = new ExplorerStateService();
    state.setRun(service.normalize(trace));

    expect(state.selectedDecision()?.decision).toBe(1);
    state.selectDecision(2);
    expect(state.selectedDecision()?.decision).toBe(2);
    state.play();
    state.selectDecision(1);
    expect(state.isPlaying()).toBe(false);
    state.selectBestFound();
    expect(state.selectedDecision()?.decision).toBe(2);

    vi.useRealTimers();
  });

});

function v2Trace(): Record<string, unknown> {
  return {
    schemaVersion: 2,
    benchmark: 'hipstershop',
    heuristic: 'knnAdaptive',
    runId: 'run-1',
    totalConfigurationSpaceSize: 8,
    totalConfigurationsSelected: 2,
    totalConfigurationsEvaluated: 2,
    totalScenariosCompleted: 2,
    totalScenariosExecuted: 1,
    totalCacheHits: 1,
    decisions: [
      {
        decision: 1,
        phase: 'initialSample',
        selectionMode: 'INITIAL_BATCH',
        candidateCount: 8,
        remainingConfigurations: 7,
        configuration: {
          hash: 'configuration-one',
          summary: 'retry-checkout:frontendservice->checkoutservice=CONFIGURED',
          normalized: {
            connectors: [
              {
                name: 'retry-checkout',
                source: 'frontendservice',
                destination: 'checkoutservice',
                strategy: 'CONFIGURED',
                source_env_GRPC_MAX_ATTEMPTS: '3',
              },
            ],
          },
        },
        selection: {
          heuristic: 'knnAdaptive',
          metadata: {
            predictedScore: 0.7,
            nearestNeighbors: [{ configurationHash: 'neighbor', distance: 0.2, realScore: 0.6 }],
          },
        },
        expectedScenarios: [
          {
            scenario: 'scenario-one',
            workload: { name: 'k6', users: 100 },
            fault: { provider: 'envoy', percentage: 25, services: ['checkoutservice'] },
          },
        ],
        executions: [
          {
            scenario: 'scenario-one',
            scenarioHash: 'hash-one',
            source: 'cacheHit',
            resultScore: 0.8,
          },
        ],
        aggregatedResult: {
          currentScore: 0.8,
          bestScoreSoFar: 0.8,
          improvedBest: true,
          metrics: {},
        },
      },
      {
        decision: 2,
        phase: 'adaptiveSelection',
        selectionMode: 'SEQUENTIAL',
        configuration: {
          hash: 'configuration-two',
          summary: 'retry-payment:checkoutservice->paymentservice=CONFIGURED',
          normalized: { connectors: [] },
        },
        selection: { heuristic: 'knnAdaptive' },
        expectedScenarios: [
          {
            scenario: 'scenario-two',
            workload: { name: 'k6', users: 300 },
            fault: { provider: 'envoy', percentage: 50, services: ['paymentservice'] },
          },
        ],
        executions: [],
        aggregatedResult: {
          score: 0.75,
          bestScoreSoFar: 0.8,
          improvedBest: false,
          metrics: {},
        },
      },
    ],
    events: [
      { sequence: 2, type: 'CONFIGURATION_SELECTED', decision: 1 },
      { sequence: 1, type: 'RUN_STARTED' },
    ],
  };
}

function result(
  scenario: string,
  scenarioHash: string,
  users: number,
  fault: number,
): Record<string, unknown> {
  return {
    benchmark: 'hipstershop',
    scenario,
    scenarioHash,
    resultSource: 'executed',
    workload_name: 'k6',
    workload_users: users,
    fault_percentage: fault,
    checkout_success_rate: 0.91,
    iteration_duration_p95: 0.18,
    connectors: [],
  };
}


function highReferenceResult(): Record<string, unknown> {
  return {
    benchmark: 'hipstershop',
    scenario: 'reference-best',
    scenarioHash: 'reference-best-hash',
    resultSource: 'executed',
    workload_name: 'k6',
    workload_users: 300,
    fault_percentage: 50,
    checkout_success_rate: 0.99,
    iteration_duration_p95: 0.01,
    metrics: { score: 0.99 },
    connectors: [],
  };
}
