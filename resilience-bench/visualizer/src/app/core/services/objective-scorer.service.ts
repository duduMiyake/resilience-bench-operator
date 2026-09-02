import { Injectable } from '@angular/core';
import {
  NormalizedObjective,
  NormalizedObjectiveMetric,
  NormalizedResult,
} from '../models/visualizer.models';

export interface ObjectiveScore {
  score?: number;
  reason?: string;
}

@Injectable({ providedIn: 'root' })
export class ObjectiveScorer {
  scoreConfiguration(results: NormalizedResult[], objective: NormalizedObjective): ObjectiveScore {
    if (objective.format.toLowerCase() !== 'structured') {
      return { reason: `Unsupported objective format: ${objective.format}` };
    }
    if (objective.metrics.length === 0) {
      return { reason: 'Structured objective contains no metrics.' };
    }

    let score = 0;
    for (const metric of objective.metrics) {
      const values = results.map((result) => numericValue(result.metrics[metric.name]));
      if (values.length === 0 || values.some((value) => value === undefined)) {
        return { reason: `Reference metric '${metric.name}' is missing or not numeric.` };
      }
      const numericValues = values as number[];
      const mean = numericValues.reduce((sum, value) => sum + value, 0) / numericValues.length;
      const normalized = normalizeMetric(mean, metric);
      if (normalized === undefined) {
        return { reason: `Invalid normalization for reference metric '${metric.name}'.` };
      }
      score += metric.effectiveWeight * normalized;
    }

    return { score: Math.max(0, Math.min(1, score)) };
  }

  legacyScore(results: NormalizedResult[]): number {
    const mean = (values: Array<number | undefined>) => {
      const present = values.filter((value): value is number => value !== undefined);
      return present.length === 0 ? 0 : present.reduce((sum, value) => sum + value, 0) / present.length;
    };
    return mean(results.map((result) => firstNumeric(result, ['checkout_success_rate', 'successRate']))) -
      mean(results.map((result) => firstNumeric(result, ['iteration_duration_p95', 'iteration_duration_p(95)', 'p95Latency'])));
  }
}

function normalizeMetric(value: number, metric: NormalizedObjectiveMetric): number | undefined {
  const normalization = metric.normalization;
  if (normalization.type.toLowerCase() === 'minmax') {
    if (normalization.min === undefined || normalization.max === undefined || normalization.max <= normalization.min) {
      return undefined;
    }
    const direction = metric.direction.toLowerCase();
    if (direction !== 'maximize' && direction !== 'minimize') return undefined;
    const numerator = direction === 'maximize' ? value - normalization.min : normalization.max - value;
    return Math.max(0, Math.min(1, numerator / (normalization.max - normalization.min)));
  }
  if (normalization.type.toLowerCase() === 'reciprocal') {
    if (metric.direction.toLowerCase() !== 'minimize' || normalization.scale === undefined || normalization.scale <= 0 || value < 0) {
      return undefined;
    }
    return normalization.scale / (normalization.scale + value);
  }
  return undefined;
}

function firstNumeric(result: NormalizedResult, names: string[]): number | undefined {
  return names.map((name) => result.metrics[name]).find(isFiniteNumber);
}

function numericValue(value: unknown): number | undefined {
  if (typeof value === 'number') return Number.isFinite(value) ? value : undefined;
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}
