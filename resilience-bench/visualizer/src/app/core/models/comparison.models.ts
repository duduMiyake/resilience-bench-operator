import { RunEvaluation } from './run-evaluation.model';
import { NormalizedResult, NormalizedRun } from './visualizer.models';

export interface ComparisonRunInput {
  label: string;
  trace: unknown;
  results: unknown;
}

export interface ComparisonRun {
  id: string;
  label: string;
  run: NormalizedRun;
  evaluation: RunEvaluation;
  color: string;
  pointStyle: string;
}

export interface ComparisonModel {
  runs: ComparisonRun[];
  referenceResults: NormalizedResult[];
  referenceCompatible: boolean;
  referenceCompatibilityReason?: string;
  errors: string[];
  warnings: string[];
  contexts: string[];
}

export interface ComparisonPoint {
  x: number;
  y: number;
  runId: string;
  label: string;
  pointStyle: string;
  scenario?: string;
  evaluation?: number;
}

export interface ConvergencePoint {
  x: number;
  y: number;
}

export interface ComparisonSummaryRow {
  run: ComparisonRun;
  referenceBestScore?: number;
  absoluteGap?: number;
  relativeGap?: number;
}
