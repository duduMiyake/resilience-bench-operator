import { Injectable } from '@angular/core';
import {
  AggregatedResult,
  ConfigurationParameter,
  JsonRecord,
  NearestNeighbor,
  NormalizedConfiguration,
  NormalizedConnector,
  NormalizedDecision,
  NormalizedEvent,
  NormalizedExecution,
  NormalizedResult,
  NormalizedRun,
  NormalizedObjective,
  NormalizedObjectiveMetric,
  NormalizedObjectiveNormalization,
  OperationalContext,
  ScenarioContext,
  VisualizerParseError,
} from '../models/visualizer.models';

@Injectable({ providedIn: 'root' })
export class RunNormalizerService {
  parseJson(text: string, label: string): unknown {
    try {
      return JSON.parse(text) as unknown;
    } catch {
      throw new VisualizerParseError(`${label} does not contain valid JSON.`);
    }
  }

  normalize(traceValue: unknown, runResultsValue?: unknown, referenceResultsValue?: unknown): NormalizedRun {
    const trace = asRecord(traceValue);
    if (!trace) {
      throw new VisualizerParseError('The trace must be a JSON object.');
    }

    const runResults = this.normalizeResults(runResultsValue);
    const referenceResults = this.normalizeResults(referenceResultsValue);
    if (numberValue(trace['schemaVersion']) === 2) {
      return this.normalizeV2(trace, runResults, referenceResults);
    }

    if (
      stringValue(trace['strategy']).toLowerCase() === 'exhaustive' &&
      Array.isArray(trace['steps'])
    ) {
      return this.normalizeLegacy(trace, runResults.length > 0 ? runResults : referenceResults);
    }

    throw new VisualizerParseError(
      'Unrecognized trace format. Use schemaVersion 2 or the legacy exhaustive format.',
    );
  }

  private normalizeV2(
    trace: JsonRecord,
    runResults: NormalizedResult[],
    referenceResults: NormalizedResult[],
  ): NormalizedRun {
    const decisions = arrayValue(trace['decisions'])
      .map(asRecord)
      .filter((value): value is JsonRecord => value !== undefined)
      .map((decision, index) => this.normalizeDecision(decision, index));

    const resultByHash = new Map<string, NormalizedResult>();
    const resultByName = new Map<string, NormalizedResult>();
    for (const result of runResults) {
      if (result.scenarioHash) {
        resultByHash.set(result.scenarioHash, result);
      }
      if (result.scenario) {
        resultByName.set(result.scenario, result);
      }
    }

    for (const decision of decisions) {
      const joined = new Set<NormalizedResult>();
      for (const execution of decision.executions) {
        const byHash = execution.scenarioHash
          ? resultByHash.get(execution.scenarioHash)
          : undefined;
        const result = byHash ?? resultByName.get(execution.scenario);
        if (!result) {
          continue;
        }
        execution.result = result;
        execution.joinedBy = byHash ? 'scenarioHash' : 'scenarioName';
        result.joinedDecision ??= decision.decision;
        result.joinedBy ??= execution.joinedBy;
        joined.add(result);
      }

      for (const expected of decision.expectedScenarios) {
        const byHash = expected.scenarioHash ? resultByHash.get(expected.scenarioHash) : undefined;
        const result = byHash ?? resultByName.get(expected.scenario);
        if (!result) {
          continue;
        }
        result.joinedDecision ??= decision.decision;
        result.joinedBy ??= byHash ? 'scenarioHash' : 'scenarioName';
        joined.add(result);
      }
      decision.joinedResults = [...joined];
    }

    const events = this.normalizeEvents(trace['events']);
    const contexts = collectContexts([...runResults, ...referenceResults], decisions);
    const warnings: string[] = [];
    if (runResults.length === 0) {
      warnings.push(
        'No run results file was loaded; scenario-level metrics may be unavailable.',
      );
    }

    return {
      kind: 'heuristic',
      schemaVersion: 2,
      benchmark: stringValue(trace['benchmark']) || 'unknown benchmark',
      strategy: stringValue(trace['heuristic']) || 'unknown heuristic',
      runId: stringValue(trace['runId']) || 'unnamed run',
      objective: normalizeObjective(trace['objective']),
      resultFile: optionalString(trace['resultFile']),
      startedAt: optionalString(trace['startedAt']),
      finishedAt: optionalString(trace['finishedAt']),
      initialSamples: optionalNumber(trace['initialSamples']),
      maxEvaluations: optionalNumber(trace['maxEvaluations']),
      totalConfigurationSpaceSize: optionalNumber(trace['totalConfigurationSpaceSize']),
      totalConfigurationsSelected: optionalNumber(trace['totalConfigurationsSelected']),
      totalConfigurationsEvaluated: optionalNumber(trace['totalConfigurationsEvaluated']),
      totalScenariosCompleted: optionalNumber(trace['totalScenariosCompleted']),
      totalScenariosExecuted: optionalNumber(trace['totalScenariosExecuted']),
      totalCacheHits: optionalNumber(trace['totalCacheHits']),
      totalTraceInconsistencies: optionalNumber(trace['totalTraceInconsistencies']),
      decisions,
      results: runResults,
      referenceResults,
      events,
      contexts,
      warnings,
    };
  }

  private normalizeLegacy(trace: JsonRecord, results: NormalizedResult[]): NormalizedRun {
    const steps = arrayValue(trace['steps']);
    const stepResults = steps
      .map(asRecord)
      .filter((step): step is JsonRecord => step !== undefined)
      .flatMap((step) => {
        const result = asRecord(step['result']) ?? step;
        return looksLikeResult(result) ? [normalizeResult(result)] : [];
      });
    const normalizedResults = results.length > 0 ? results : stepResults;

    return {
      kind: 'legacy-exhaustive',
      schemaVersion: null,
      benchmark:
        stringValue(trace['benchmark']) ||
        firstDefined(normalizedResults.map((result) => result.benchmark)) ||
        'unknown benchmark',
      strategy: 'exhaustive',
      runId: stringValue(trace['runId']) || 'legacy run',
      totalConfigurationSpaceSize:
        optionalNumber(trace['totalConfigurationSpaceSize']) ?? normalizedResults.length,
      totalConfigurationsSelected: undefined,
      totalConfigurationsEvaluated: undefined,
      totalScenariosCompleted: normalizedResults.length,
      totalScenariosExecuted: normalizedResults.filter(
        (result) => result.resultSource !== 'cacheHit',
      ).length,
      totalCacheHits: normalizedResults.filter((result) => result.resultSource === 'cacheHit')
        .length,
      decisions: [],
      results: normalizedResults,
      referenceResults: normalizedResults,
      events: [],
      contexts: collectContexts(normalizedResults, []),
      warnings: [
        'Legacy exhaustive trace: heuristic decisions and selection metadata are unavailable.',
      ],
    };
  }

  private normalizeDecision(raw: JsonRecord, index: number): NormalizedDecision {
    const configurationRaw = asRecord(raw['configuration']) ?? {};
    const selection = asRecord(raw['selection']) ?? {};
    const metadata = asRecord(selection['metadata']) ?? {};
    const nearestNeighbors = arrayValue(metadata['nearestNeighbors'])
      .map(asRecord)
      .filter((neighbor): neighbor is JsonRecord => neighbor !== undefined)
      .map(normalizeNeighbor);

    return {
      decision: optionalNumber(raw['decision']) ?? index + 1,
      phase: stringValue(raw['phase']) || 'unknown',
      selectionMode: stringValue(raw['selectionMode']) || 'UNKNOWN',
      batch: optionalNumber(raw['batch']),
      selectedAt: optionalString(raw['selectedAt']),
      evaluatedConfigurations: optionalNumber(raw['evaluatedConfigurations']),
      candidateCount: optionalNumber(raw['candidateCount']),
      remainingConfigurations: optionalNumber(raw['remainingConfigurations']),
      configuration: normalizeConfiguration(configurationRaw),
      heuristic: stringValue(selection['heuristic']) || 'unknown',
      metadata: cloneRecord(metadata),
      nearestNeighbors,
      expectedScenarios: arrayValue(raw['expectedScenarios'])
        .map(asRecord)
        .filter((scenario): scenario is JsonRecord => scenario !== undefined)
        .map(normalizeScenarioContext),
      executions: arrayValue(raw['executions'])
        .map(asRecord)
        .filter((execution): execution is JsonRecord => execution !== undefined)
        .map(normalizeExecution),
      aggregatedResult: normalizeAggregatedResult(raw['aggregatedResult']),
      joinedResults: [],
    };
  }

  private normalizeResults(value: unknown): NormalizedResult[] {
    if (value === undefined || value === null) {
      return [];
    }
    const record = asRecord(value);
    const rawResults =
      record && Array.isArray(record['results'])
        ? record['results']
        : Array.isArray(value)
          ? value
          : undefined;
    if (!rawResults) {
      throw new VisualizerParseError('The results file must contain an array in "results".');
    }
    return rawResults
      .map(asRecord)
      .filter((result): result is JsonRecord => result !== undefined)
      .map(normalizeResult);
  }

  private normalizeEvents(value: unknown): NormalizedEvent[] {
    return arrayValue(value)
      .map(asRecord)
      .filter((event): event is JsonRecord => event !== undefined)
      .map((event, index) => ({
        sequence: optionalNumber(event['sequence']) ?? index + 1,
        type: stringValue(event['type']) || 'UNKNOWN',
        timestamp: optionalString(event['timestamp']),
        decision: optionalNumber(event['decision']),
        raw: cloneRecord(event),
      }))
      .sort((left, right) => left.sequence - right.sequence);
  }
}

function normalizeObjective(value: unknown): NormalizedObjective | undefined {
  const raw = asRecord(value);
  if (!raw) return undefined;
  const metrics = arrayValue(raw['metrics'])
    .map(asRecord)
    .filter((metric): metric is JsonRecord => metric !== undefined)
    .map(normalizeObjectiveMetric);
  return {
    format: stringValue(raw['format']) || 'unknown',
    metrics,
  };
}

function normalizeObjectiveMetric(raw: JsonRecord): NormalizedObjectiveMetric {
  const normalization = asRecord(raw['normalization']) ?? {};
  const normalizedNormalization: NormalizedObjectiveNormalization = {
    type: stringValue(normalization['type']),
    min: optionalNumber(normalization['min']),
    max: optionalNumber(normalization['max']),
    scale: optionalNumber(normalization['scale']),
  };
  return {
    name: stringValue(raw['name']),
    direction: stringValue(raw['direction']),
    effectiveWeight: optionalNumber(raw['effectiveWeight']) ?? 0,
    normalization: normalizedNormalization,
  };
}

function normalizeConfiguration(raw: JsonRecord): NormalizedConfiguration {
  const normalized = asRecord(raw['normalized']) ?? {};
  const connectors = arrayValue(normalized['connectors'])
    .map(asRecord)
    .filter((connector): connector is JsonRecord => connector !== undefined)
    .map(normalizeConnector);
  return {
    hash: optionalString(raw['hash']),
    summary: stringValue(raw['summary']) || 'Configuração sem resumo',
    normalized: cloneRecord(normalized),
    connectors,
    parameters: flattenParameters(normalized).filter(
      (parameter) => !parameter.path.startsWith('connectors.'),
    ),
  };
}

function normalizeConnector(raw: JsonRecord): NormalizedConnector {
  const excluded = new Set(['name', 'source', 'destination', 'strategy']);
  const parameters = flattenParameters(raw).filter((parameter) => !excluded.has(parameter.path));
  return {
    name: stringValue(raw['name']) || 'connector',
    source: stringValue(raw['source']) || '-',
    destination: stringValue(raw['destination']) || '-',
    strategy: stringValue(raw['strategy']) || 'CONFIGURED',
    parameters,
  };
}

function flattenParameters(value: unknown, prefix = ''): ConfigurationParameter[] {
  if (Array.isArray(value)) {
    return value.flatMap((item, index) =>
      flattenParameters(item, prefix ? `${prefix}.${index}` : String(index)),
    );
  }
  const record = asRecord(value);
  if (record) {
    return Object.entries(record).flatMap(([key, item]) =>
      flattenParameters(item, prefix ? `${prefix}.${key}` : key),
    );
  }
  return prefix ? [{ path: prefix, value: displayValue(value) }] : [];
}

function normalizeScenarioContext(raw: JsonRecord): ScenarioContext {
  const workload = asRecord(raw['workload']) ?? {};
  const fault = asRecord(raw['fault']) ?? {};
  return {
    scenario: stringValue(raw['scenario']),
    scenarioHash: optionalString(raw['scenarioHash']),
    workloadName: optionalString(workload['name']) ?? optionalString(raw['workload_name']),
    workloadUsers: optionalNumber(workload['users']) ?? optionalNumber(raw['workload_users']),
    faultProvider: optionalString(fault['provider']) ?? optionalString(raw['fault_provider']),
    faultPercentage:
      optionalNumber(fault['percentage']) ??
      optionalNumber(raw['fault_percentage']) ??
      optionalNumber(raw['percentage']),
    faultServices: arrayValue(fault['services']).map(stringValue).filter(Boolean),
  };
}

function normalizeExecution(raw: JsonRecord): NormalizedExecution {
  return {
    ...normalizeScenarioContext(raw),
    source: stringValue(raw['source']) || 'unknown',
    resultScore: optionalNumber(raw['resultScore']),
    completedAt: optionalString(raw['completedAt']),
  };
}

function normalizeAggregatedResult(value: unknown): AggregatedResult | undefined {
  const raw = asRecord(value);
  if (!raw) {
    return undefined;
  }
  const best = asRecord(raw['bestConfigurationSoFar']) ?? {};
  return {
    evaluatedAt: optionalString(raw['evaluatedAt']),
    score: optionalNumber(raw['score']),
    currentScore: optionalNumber(raw['currentScore']),
    bestScoreSoFar: optionalNumber(raw['bestScoreSoFar']),
    bestConfigurationHash: optionalString(best['hash']),
    bestConfigurationSummary: optionalString(best['summary']),
    improvedBest: typeof raw['improvedBest'] === 'boolean' ? raw['improvedBest'] : undefined,
    metrics: cloneRecord(asRecord(raw['metrics']) ?? {}),
  };
}

function normalizeNeighbor(raw: JsonRecord): NearestNeighbor {
  return {
    configurationHash: optionalString(raw['configurationHash']),
    configurationSummary: optionalString(raw['configurationSummary']),
    distance: optionalNumber(raw['distance']),
    realScore: optionalNumber(raw['realScore']),
    raw: cloneRecord(raw),
  };
}

function normalizeResult(raw: JsonRecord): NormalizedResult {
  const metrics = { ...raw, ...(asRecord(raw['metrics']) ?? {}) };
  const fault = asRecord(raw['fault']) ?? {};
  return {
    scenario: stringValue(raw['scenario']) || stringValue(raw['name']) || 'unnamed scenario',
    scenarioHash: optionalString(raw['scenarioHash']),
    benchmark: optionalString(raw['benchmark']),
    resultSource: optionalString(raw['resultSource']) ?? optionalString(raw['source']),
    workloadName: optionalString(raw['workload_name']) ?? optionalString(raw['workloadName']),
    workloadUsers: optionalNumber(raw['workload_users']) ?? optionalNumber(raw['workloadUsers']),
    faultProvider:
      optionalString(raw['fault_provider']) ??
      optionalString(fault['provider']) ??
      optionalString(raw['provider']),
    faultPercentage:
      optionalNumber(raw['fault_percentage']) ??
      optionalNumber(fault['percentage']) ??
      optionalNumber(raw['percentage']),
    faultServices: arrayValue(raw['fault_services'] ?? fault['services']).map(stringValue).filter(Boolean),
    connectors: arrayValue(raw['connectors']).map(cloneJsonValue),
    checkoutSuccessRate: metricNumber(metrics, ['checkout_success_rate', 'successRate']),
    iterationDurationP95: metricNumber(metrics, [
      'iteration_duration_p(95)',
      'iteration_duration_p95',
      'p95Latency',
    ]),
    metrics: cloneRecord(metrics),
    raw: cloneRecord(raw),
  };
}

function collectContexts(
  results: NormalizedResult[],
  decisions: NormalizedDecision[],
): OperationalContext[] {
  const contexts = new Map<string, OperationalContext>();
  const add = (workloadUsers?: number, faultPercentage?: number) => {
    const key = `${workloadUsers ?? '-'}|${faultPercentage ?? '-'}`;
    contexts.set(key, {
      key,
      workloadUsers,
      faultPercentage,
      label: `${workloadUsers ?? '-'} users - ${faultPercentage ?? '-'}% fault`,
    });
  };
  results.forEach((result) => add(result.workloadUsers, result.faultPercentage));
  decisions
    .flatMap((decision) => decision.expectedScenarios)
    .forEach((scenario) => add(scenario.workloadUsers, scenario.faultPercentage));
  return [...contexts.values()].sort(
    (left, right) =>
      (left.workloadUsers ?? -1) - (right.workloadUsers ?? -1) ||
      (left.faultPercentage ?? -1) - (right.faultPercentage ?? -1),
  );
}

function metricNumber(metrics: JsonRecord, keys: string[]): number | undefined {
  for (const key of keys) {
    const value = optionalNumber(metrics[key]);
    if (value !== undefined) {
      return value;
    }
  }
  return undefined;
}

function looksLikeResult(value: JsonRecord): boolean {
  return 'scenario' in value || 'checkout_success_rate' in value || 'metrics' in value;
}

function asRecord(value: unknown): JsonRecord | undefined {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as JsonRecord)
    : undefined;
}

function cloneRecord(value: JsonRecord): JsonRecord {
  return cloneJsonValue(value) as JsonRecord;
}

function cloneJsonValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(cloneJsonValue);
  }
  const record = asRecord(value);
  if (record) {
    return Object.fromEntries(
      Object.entries(record).map(([key, item]) => [key, cloneJsonValue(item)]),
    );
  }
  return value;
}

function arrayValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function stringValue(value: unknown): string {
  return typeof value === 'string'
    ? value
    : value === null || value === undefined
      ? ''
      : String(value);
}

function optionalString(value: unknown): string | undefined {
  const valueAsString = stringValue(value).trim();
  return valueAsString || undefined;
}

function numberValue(value: unknown): number {
  return typeof value === 'number' ? value : Number(value);
}

function optionalNumber(value: unknown): number | undefined {
  if (value === null || value === undefined || value === '') {
    return undefined;
  }
  const parsed = numberValue(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function displayValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '-';
  }
  if (typeof value === 'string') {
    return value;
  }
  return JSON.stringify(value);
}

function firstDefined<T>(values: Array<T | undefined>): T | undefined {
  return values.find((value): value is T => value !== undefined);
}
