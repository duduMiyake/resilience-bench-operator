import { Injectable } from '@angular/core';
import { ComparisonModel, ComparisonRun, ComparisonRunInput } from '../models/comparison.models';
import { RunEvaluationService } from './run-evaluation.service';
import { RunNormalizerService } from './run-normalizer.service';

const COLORS = ['#087f5b', '#2563a5', '#c2410c', '#7c3aed', '#b45309', '#0f766e'];
const POINT_STYLES = ['circle', 'triangle', 'rect', 'rectRot', 'star', 'crossRot'];

@Injectable({ providedIn: 'root' })
export class ComparisonService {
  constructor(
    private readonly normalizer: RunNormalizerService,
    private readonly evaluationService: RunEvaluationService,
  ) {}

  build(inputs: ComparisonRunInput[], referenceValue?: unknown): ComparisonModel {
    const errors: string[] = [];
    if (inputs.length < 2) {
      errors.push('Add at least two runs to compare.');
    }

    const runs: ComparisonRun[] = inputs.map((input, index) => {
      const run = this.normalizer.normalize(input.trace, input.results, referenceValue);
      return {
        id: `${run.runId}-${index}`,
        label: input.label || run.strategy,
        run,
        evaluation: this.evaluationService.evaluate(run),
        color: COLORS[index % COLORS.length],
        pointStyle: POINT_STYLES[index % POINT_STYLES.length],
      };
    });

    const first = runs[0];
    if (first) {
      for (const current of runs.slice(1)) {
        if (current.run.benchmark !== first.run.benchmark) {
          errors.push(`Run "${current.label}" uses benchmark "${current.run.benchmark}" instead of "${first.run.benchmark}".`);
        }
        if (contextSignature(current) !== contextSignature(first)) {
          errors.push(`Run "${current.label}" does not cover the same operational contexts as the first run.`);
        }
        if (current.run.totalConfigurationSpaceSize !== first.run.totalConfigurationSpaceSize) {
          errors.push(`Run "${current.label}" has a different configuration-space size.`);
        }
        if (objectiveSignature(current.run) !== objectiveSignature(first.run)) {
          errors.push(`Run "${current.label}" uses different objective scoring semantics from "${first.label}".`);
        }
        if (current.evaluation.objectiveDirection !== first.evaluation.objectiveDirection) {
          errors.push(`Run "${current.label}" uses different score direction semantics.`);
        }
      }
      if (first.run.totalConfigurationSpaceSize === undefined) {
        errors.push('Configuration-space size is unavailable in the runs.');
      }
    }

    const budgetValues = runs.map((item) => item.run.maxEvaluations ?? item.evaluation.evaluatedConfigurations);
    const warnings = new Set<string>();
    if (new Set(budgetValues).size > 1) {
      warnings.add('Runs use different evaluation budgets. Convergence remains comparable by evaluation number, but total search effort differs.');
    }

    const referenceResults = first?.run.referenceResults ?? [];
    const referenceCompatible = referenceResults.length > 0 && runs.every((item) => item.evaluation.referenceCompatible);
    const referenceReasons = runs
      .filter((item) => !item.evaluation.referenceCompatible)
      .map((item) => `${item.label}: ${item.evaluation.referenceCompatibilityReason ?? 'reference is not comparable'}`);
    if (referenceResults.length === 0) {
      warnings.add('No shared exhaustive reference was loaded. Reference metrics are unavailable.');
    } else if (!referenceCompatible) {
      warnings.add('The shared exhaustive reference is not valid for every run. Reference metrics are unavailable.');
    }

    return {
      runs,
      referenceResults,
      referenceCompatible,
      referenceCompatibilityReason: referenceReasons.join(' '),
      errors,
      warnings: [...warnings],
      contexts: first?.run.contexts.map((context) => context.key) ?? [],
    };
  }
}

function objectiveSignature(run: ComparisonRun['run']): string {
  if (!run.objective) return 'legacy';
  return JSON.stringify({
    format: run.objective.format,
    metrics: run.objective.metrics
      .map((metric) => ({
        name: metric.name,
        direction: metric.direction,
        effectiveWeight: metric.effectiveWeight,
        normalization: metric.normalization,
      }))
      .sort((left, right) => left.name.localeCompare(right.name)),
  });
}

function contextSignature(item: ComparisonRun): string {
  return item.run.contexts.map((context) => context.key).sort().join('|');
}
