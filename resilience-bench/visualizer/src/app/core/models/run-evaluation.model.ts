export const QUALITY_THRESHOLDS = [0.9, 0.95, 0.99] as const;

export interface RunEvaluation {
  objectiveDirection: 'maximize';
  evaluatedConfigurations: number;
  totalConfigurations: number;
  exploredRatio?: number;
  searchSpaceReduction?: number;
  bestObservedScore?: number;
  bestFoundAtDecision?: number;
  referenceBestScore?: number;
  absoluteGap?: number;
  relativeGap?: number;
  referenceCompatible: boolean;
  referenceCompatibilityReason?: string;
  qualityThresholds: QualityThresholdEvaluation[];
  contextEvaluations: ContextEvaluation[];
}

export interface QualityThresholdEvaluation {
  threshold: (typeof QUALITY_THRESHOLDS)[number];
  reachedAtDecision?: number;
}

export interface ContextEvaluation {
  context: string;
  heuristicSuccessRate?: number;
  referenceSuccessRate?: number;
  heuristicP95?: number;
  referenceP95?: number;
}
