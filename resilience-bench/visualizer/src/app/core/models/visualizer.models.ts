export type JsonRecord = Record<string, unknown>;

export type RunKind = 'heuristic' | 'legacy-exhaustive';

export interface NormalizedObjective {
  format: string;
  metrics: NormalizedObjectiveMetric[];
}

export interface NormalizedObjectiveMetric {
  name: string;
  direction: string;
  effectiveWeight: number;
  normalization: NormalizedObjectiveNormalization;
}

export interface NormalizedObjectiveNormalization {
  type: string;
  min?: number;
  max?: number;
  scale?: number;
}

export interface NormalizedRun {
  kind: RunKind;
  schemaVersion: number | null;
  benchmark: string;
  strategy: string;
  runId: string;
  objective?: NormalizedObjective;
  resultFile?: string;
  startedAt?: string;
  finishedAt?: string;
  initialSamples?: number;
  maxEvaluations?: number;
  totalConfigurationSpaceSize?: number;
  totalConfigurationsSelected?: number;
  totalConfigurationsEvaluated?: number;
  totalScenariosCompleted?: number;
  totalScenariosExecuted?: number;
  totalCacheHits?: number;
  totalTraceInconsistencies?: number;
  decisions: NormalizedDecision[];
  results: NormalizedResult[];
  referenceResults: NormalizedResult[];
  events: NormalizedEvent[];
  contexts: OperationalContext[];
  warnings: string[];
}

export interface NormalizedDecision {
  decision: number;
  phase: string;
  selectionMode: string;
  batch?: number;
  selectedAt?: string;
  evaluatedConfigurations?: number;
  candidateCount?: number;
  remainingConfigurations?: number;
  configuration: NormalizedConfiguration;
  heuristic: string;
  metadata: JsonRecord;
  nearestNeighbors: NearestNeighbor[];
  expectedScenarios: ScenarioContext[];
  executions: NormalizedExecution[];
  aggregatedResult?: AggregatedResult;
  joinedResults: NormalizedResult[];
}

export interface NormalizedConfiguration {
  hash?: string;
  summary: string;
  normalized: JsonRecord;
  connectors: NormalizedConnector[];
  parameters: ConfigurationParameter[];
}

export interface NormalizedConnector {
  name: string;
  source: string;
  destination: string;
  strategy: string;
  parameters: ConfigurationParameter[];
}

export interface ConfigurationParameter {
  path: string;
  value: string;
}

export interface ScenarioContext {
  scenario: string;
  scenarioHash?: string;
  workloadName?: string;
  workloadUsers?: number;
  faultProvider?: string;
  faultPercentage?: number;
  faultServices: string[];
}

export interface NormalizedExecution extends ScenarioContext {
  source: string;
  resultScore?: number;
  completedAt?: string;
  joinedBy?: 'scenarioHash' | 'scenarioName';
  result?: NormalizedResult;
}

export interface AggregatedResult {
  evaluatedAt?: string;
  score?: number;
  currentScore?: number;
  bestScoreSoFar?: number;
  bestConfigurationHash?: string;
  bestConfigurationSummary?: string;
  improvedBest?: boolean;
  metrics: JsonRecord;
}

export interface NearestNeighbor {
  configurationHash?: string;
  configurationSummary?: string;
  distance?: number;
  realScore?: number;
  raw: JsonRecord;
}

export interface NormalizedResult {
  scenario: string;
  scenarioHash?: string;
  benchmark?: string;
  resultSource?: string;
  workloadName?: string;
  workloadUsers?: number;
  faultProvider?: string;
  faultPercentage?: number;
  faultServices: string[];
  connectors: unknown[];
  checkoutSuccessRate?: number;
  iterationDurationP95?: number;
  metrics: JsonRecord;
  raw: JsonRecord;
  joinedDecision?: number;
  joinedBy?: 'scenarioHash' | 'scenarioName';
}

export interface NormalizedEvent {
  sequence: number;
  type: string;
  timestamp?: string;
  decision?: number;
  raw: JsonRecord;
}

export interface OperationalContext {
  key: string;
  workloadUsers?: number;
  faultPercentage?: number;
  label: string;
}

export interface LoadedJsonFile {
  name: string;
  value: unknown;
}

export class VisualizerParseError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'VisualizerParseError';
  }
}
