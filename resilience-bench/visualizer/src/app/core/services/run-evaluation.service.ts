import { Injectable } from '@angular/core';
import {
  ContextEvaluation,
  QUALITY_THRESHOLDS,
  RunEvaluation,
} from '../models/run-evaluation.model';
import { JsonRecord, NormalizedDecision, NormalizedResult, NormalizedRun } from '../models/visualizer.models';
import { ObjectiveScorer } from './objective-scorer.service';

const SCORE_TOLERANCE = 1e-9;

@Injectable({ providedIn: 'root' })
export class RunEvaluationService {
  constructor(private readonly objectiveScorer: ObjectiveScorer) {}

  evaluate(run: NormalizedRun): RunEvaluation {
    const scoredDecisions = run.decisions.filter(
      (decision) => decision.aggregatedResult?.score !== undefined,
    );
    const best = scoredDecisions.reduce<NormalizedDecision | undefined>(
      (current, decision) =>
        !current || decision.aggregatedResult!.score! > current.aggregatedResult!.score!
          ? decision
          : current,
      undefined,
    );
    const evaluatedConfigurations = scoredDecisions.length;
    const totalConfigurations = run.totalConfigurationSpaceSize ?? 0;
    const exploredRatio = totalConfigurations > 0 ? evaluatedConfigurations / totalConfigurations : undefined;
    const evaluation: RunEvaluation = {
      objectiveDirection: 'maximize',
      evaluatedConfigurations,
      totalConfigurations,
      exploredRatio,
      searchSpaceReduction: exploredRatio === undefined ? undefined : 1 - exploredRatio,
      bestObservedScore: best?.aggregatedResult?.score,
      bestFoundAtDecision: best?.decision,
      referenceCompatible: false,
      qualityThresholds: QUALITY_THRESHOLDS.map((threshold) => ({ threshold })),
      contextEvaluations: contextEvaluations(run.results, run.referenceResults),
    };

    if (run.referenceResults.length === 0) {
      evaluation.referenceCompatibilityReason = 'No exhaustive reference was loaded.';
      return evaluation;
    }

    const expectedContexts = new Set(
      run.decisions.flatMap((decision) => decision.expectedScenarios.map(contextKey)),
    );
    if (expectedContexts.size === 0) {
      run.results.forEach((result) => expectedContexts.add(contextKey(result)));
    }
    const referenceGroups = groupByConfiguration(run.referenceResults);
    if (
      expectedContexts.size === 0 ||
      [...referenceGroups.values()].some(
        (results) => ![...expectedContexts].every((key) => results.some((result) => contextKey(result) === key)),
      )
    ) {
      evaluation.referenceCompatibilityReason =
        'The exhaustive reference does not cover all operational contexts of this run.';
      return evaluation;
    }

    const scoredReferences = [...referenceGroups.entries()].map(([key, results]) => ({
      key,
      score: run.objective?.format.toLowerCase() === 'structured'
        ? this.objectiveScorer.scoreConfiguration(results, run.objective)
        : { score: this.objectiveScorer.legacyScore(results) },
    }));
    const incompatibleReference = scoredReferences.find((reference) => reference.score.score === undefined);
    if (incompatibleReference) {
      evaluation.referenceCompatibilityReason = incompatibleReference.score.reason ??
        'The exhaustive reference contains incompatible metric values.';
      return evaluation;
    }
    const referenceScores = new Map(
      scoredReferences.map(({ key, score }) => [key, score.score as number]),
    );
    const sharedDecisions = scoredDecisions.filter((decision) =>
      referenceScores.has(configurationKey(decision.configuration.normalized)),
    );
    if (sharedDecisions.length === 0) {
      evaluation.referenceCompatibilityReason =
        'No shared configuration is available to validate the exhaustive aggregation.';
      return evaluation;
    }
    const mismatch = sharedDecisions.some((decision) => {
      const referenceScore = referenceScores.get(configurationKey(decision.configuration.normalized));
      return referenceScore !== undefined &&
        Math.abs(referenceScore - decision.aggregatedResult!.score!) > SCORE_TOLERANCE;
    });
    if (mismatch) {
      evaluation.referenceCompatibilityReason =
        'The exhaustive aggregate does not match trace-provided scores for shared configurations.';
      return evaluation;
    }

    const referenceBestScore = Math.max(...referenceScores.values());
    evaluation.referenceCompatible = Number.isFinite(referenceBestScore);
    if (!evaluation.referenceCompatible || evaluation.bestObservedScore === undefined) {
      evaluation.referenceCompatibilityReason = 'The exhaustive reference has no comparable configuration scores.';
      return evaluation;
    }

    evaluation.referenceBestScore = referenceBestScore;
    evaluation.absoluteGap = referenceBestScore - evaluation.bestObservedScore;
    evaluation.relativeGap = referenceBestScore <= 0
      ? undefined
      : evaluation.absoluteGap / referenceBestScore * 100;
    evaluation.qualityThresholds = referenceBestScore <= 0 ? [] : QUALITY_THRESHOLDS.map((threshold) => ({
      threshold,
      reachedAtDecision: firstReachedDecision(scoredDecisions, referenceBestScore * threshold),
    }));
    return evaluation;
  }
}

function groupByConfiguration(results: NormalizedResult[]): Map<string, NormalizedResult[]> {
  const groups = new Map<string, NormalizedResult[]>();
  for (const result of results) {
    const key = configurationKey({ connectors: result.connectors });
    groups.set(key, [...(groups.get(key) ?? []), result]);
  }
  return groups;
}

function configurationKey(configuration: JsonRecord): string {
  const connectors = Array.isArray(configuration['connectors']) ? configuration['connectors'] : [];
  return JSON.stringify({
    connectors: connectors
      .filter(isRecord)
      .map((connector) => {
        const normalized = sortJson(connector) as JsonRecord;
        normalized['strategy'] = hasResilienceSettings(normalized) ? 'CONFIGURED' : 'NONE';
        return sortJson(normalized);
      })
      .sort((left, right) => JSON.stringify(left).localeCompare(JSON.stringify(right))),
  });
}

function hasResilienceSettings(connector: JsonRecord): boolean {
  return Object.entries(connector).some(([key, value]) =>
    key.startsWith('source_env_') ||
    key.startsWith('destination_env_') ||
    (['retry', 'timeout', 'circuitBreaker', 'delay', 'abort'].includes(key) && !isEmptyJson(value)) ||
    (key === 'percentage' && value !== null && value !== undefined),
  );
}

function isEmptyJson(value: unknown): boolean {
  return value === null || value === undefined ||
    (Array.isArray(value) && value.length === 0) ||
    (isRecord(value) && Object.keys(value).length === 0);
}

function sortJson(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value
      .map((item) => isRecord(item) ? sortJson(item) : item)
      .sort((left, right) => sortableValue(left).localeCompare(sortableValue(right)));
  }
  if (isRecord(value)) {
    return Object.fromEntries(Object.keys(value).sort().map((key) => [key, sortJson(value[key])]));
  }
  return value;
}

function sortableValue(value: unknown): string {
  return isRecord(value) || Array.isArray(value) ? JSON.stringify(value) : String(value);
}

function contextEvaluations(
  heuristicResults: NormalizedResult[],
  referenceResults: NormalizedResult[],
): ContextEvaluation[] {
  const heuristic = groupByContext(heuristicResults);
  const reference = groupByContext(referenceResults);
  return [...heuristic.keys()]
    .filter((key) => reference.has(key))
    .sort()
    .map((key) => ({
      context: contextLabel(heuristic.get(key)![0]),
      heuristicSuccessRate: maxMetric(heuristic.get(key)!, 'checkoutSuccessRate'),
      referenceSuccessRate: maxMetric(reference.get(key)!, 'checkoutSuccessRate'),
      heuristicP95: minMetric(heuristic.get(key)!, 'iterationDurationP95'),
      referenceP95: minMetric(reference.get(key)!, 'iterationDurationP95'),
    }));
}

function groupByContext(results: NormalizedResult[]): Map<string, NormalizedResult[]> {
  const groups = new Map<string, NormalizedResult[]>();
  for (const result of results) {
    const key = contextKey(result);
    groups.set(key, [...(groups.get(key) ?? []), result]);
  }
  return groups;
}

function contextKey(context: {
  workloadName?: string; workloadUsers?: number; faultProvider?: string;
  faultPercentage?: number; faultServices?: string[];
}): string {
  return JSON.stringify([
    context.workloadName ?? null,
    context.workloadUsers ?? null,
    context.faultProvider ?? null,
    context.faultPercentage ?? null,
    [...(context.faultServices ?? [])].sort(),
  ]);
}

function contextLabel(result: NormalizedResult): string {
  return `${result.workloadUsers ?? '-'} users · ${result.faultPercentage ?? '-'}% fault`;
}

function firstReachedDecision(decisions: NormalizedDecision[], target: number): number | undefined {
  let best = -Infinity;
  for (const decision of [...decisions].sort((left, right) => left.decision - right.decision)) {
    best = Math.max(best, decision.aggregatedResult!.score!);
    if (best >= target) {
      return decision.decision;
    }
  }
  return undefined;
}

function maxMetric(results: NormalizedResult[], key: 'checkoutSuccessRate'): number | undefined {
  const values = results.map((result) => result[key]).filter(isNumber);
  return values.length ? Math.max(...values) : undefined;
}

function minMetric(results: NormalizedResult[], key: 'iterationDurationP95'): number | undefined {
  const values = results.map((result) => result[key]).filter(isNumber);
  return values.length ? Math.min(...values) : undefined;
}

function isNumber(value: number | undefined): value is number {
  return value !== undefined;
}

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
