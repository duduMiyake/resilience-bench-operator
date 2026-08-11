import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { JsonRecord, NormalizedConnector, NormalizedDecision } from '../../core/models/visualizer.models';
import { ExplorerStateService, isInitialDecision, observedScore } from '../../core/services/explorer-state.service';

@Component({
  selector: 'app-decision-details',
  imports: [TableModule, TagModule],
  templateUrl: './decision-details.component.html',
  styleUrl: './decision-details.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecisionDetailsComponent {
  readonly state = inject(ExplorerStateService);
  readonly knownMetadataKeys = ['predictedScore', 'uncertainty', 'explorationBonus', 'selectionScore'] as const;

  readonly genericMetadata = computed(() => {
    const metadata = this.state.selectedDecision()?.metadata ?? {};
    const excluded = new Set<string>([...this.knownMetadataKeys, 'nearestNeighbors']);
    return Object.entries(metadata)
      .filter(([key]) => !excluded.has(key))
      .map(([key, value]) => ({ key, label: this.metadataLabel(key), value: display(value) }));
  });

  isInitial(decision: NormalizedDecision): boolean {
    return isInitialDecision(decision);
  }

  hasKnnFormula(decision: NormalizedDecision): boolean {
    return ['predictedScore', 'explorationBonus', 'selectionScore'].every((key) => typeof decision.metadata[key] === 'number');
  }

  decisionExplanation(decision: NormalizedDecision): string {
    if (this.isInitial(decision)) {
      return 'This configuration belongs to the initial sample. Initial configurations are selected before the adaptive KNN search begins, so predicted score, uncertainty, exploration bonus, and selection score are not used for this decision. The results from the initial sample provide the first observations for the adaptive search.';
    }
    return this.knnExplanation(decision) ?? 'This adaptive decision does not include enough KNN metadata to explain the selection formula.';
  }

  knnExplanation(decision: NormalizedDecision): string | undefined {
    if (!this.hasKnnFormula(decision)) {
      return undefined;
    }
    const predicted = display(decision.metadata['predictedScore']);
    const uncertainty = display(decision.metadata['uncertainty']);
    const bonus = display(decision.metadata['explorationBonus']);
    const selection = display(decision.metadata['selectionScore']);
    const observed = display(observedScore(decision));
    const best = display(decision.aggregatedResult?.bestScoreSoFar);
    const ending = decision.aggregatedResult?.improvedBest
      ? `After evaluation, the observed score was ${observed}. This became the new best configuration found so far with a best score of ${best}.`
      : `After evaluation, the observed score was ${observed}. The best known score remained ${best}.`;
    return `The heuristic predicted a score of ${predicted} for this configuration. Because the configuration had an uncertainty of ${uncertainty}, an exploration bonus of ${bonus} was added. This produced a selection score of ${selection}, making it an attractive candidate for exploration. ${ending}`;
  }

  bestStatus(decision: NormalizedDecision): string {
    if (decision.aggregatedResult?.improvedBest) {
      return 'New best configuration found';
    }
    return `Best known score remained ${display(decision.aggregatedResult?.bestScoreSoFar)}`;
  }

  metadataValue(key: string): string {
    return display(this.state.selectedDecision()?.metadata[key]);
  }

  value(value: unknown): string {
    return display(value);
  }

  shortHash(hash?: string): string {
    return hash ? hash.slice(0, 8) : '-';
  }

  executionSeverity(source: string): 'success' | 'info' | 'secondary' {
    return source === 'cacheHit' ? 'info' : source === 'executed' ? 'success' : 'secondary';
  }

  phaseLabel(phase?: string): string {
    return labelFromMap(phase, { initialSample: 'Initial Sample', initialSelection: 'Initial Sample', adaptiveSelection: 'Adaptive Search', completed: 'Completed' });
  }

  selectionModeLabel(mode?: string): string {
    return labelFromMap(mode, { INITIAL_BATCH: 'Initial Batch', SEQUENTIAL: 'Sequential Selection' });
  }

  metadataLabel(key: string): string {
    return labelFromMap(key, { predictedScore: 'Predicted Score', uncertainty: 'Uncertainty', explorationBonus: 'Exploration Bonus', selectionScore: 'Selection Score', nearestNeighbors: 'Nearest Neighbors', candidateCount: 'Candidate Count', remainingConfigurations: 'Remaining Configurations' });
  }

  metadataTooltip(key: string): string {
    return labelFromMap(key, {
      predictedScore: 'Estimated performance of the candidate based on previously evaluated neighboring configurations.',
      uncertainty: 'How far the candidate is from known evaluated configurations. Higher values indicate less explored regions.',
      explorationBonus: 'Bonus added to encourage evaluation of uncertain or less explored configurations.',
      selectionScore: 'Value used by the heuristic to rank candidates for the next evaluation.',
      nearestNeighbors: 'Previously evaluated configurations used by KNN to estimate this candidate score.',
      candidateCount: 'Configurations available before this selection.',
      remainingConfigurations: 'Configurations still available after this selection.',
    });
  }

  parameterLabel(path: string): string {
    const key = path.split('.').pop() ?? path;
    return labelFromMap(key.replace(/^source_env_/, ''), {
      GRPC_MAX_ATTEMPTS: 'Max Attempts',
      GRPC_INITIAL_BACKOFF: 'Initial Backoff',
      GRPC_MAX_BACKOFF: 'Max Backoff',
      GRPC_BACKOFF_MULTIPLIER: 'Backoff Multiplier',
    });
  }

  retryLabel(strategy: string): string {
    return strategy === 'NONE' || strategy === 'BASELINE' ? 'Disabled' : 'Enabled';
  }

  retrySeverity(strategy: string): 'success' | 'secondary' {
    return strategy === 'NONE' || strategy === 'BASELINE' ? 'secondary' : 'success';
  }

  connectorTitle(connector: NormalizedConnector): string {
    return `${connector.source} -> ${connector.destination}`;
  }

  executionSourceLabel(source: string): string {
    return labelFromMap(source, { cacheHit: 'Cache Hit', executed: 'Executed' });
  }

  contextLabel(item: { workloadUsers?: number; faultPercentage?: number }): string {
    return `${display(item.workloadUsers)} users - ${display(item.faultPercentage)}% fault`;
  }

  metadataEntries(value: JsonRecord): Array<{ key: string; value: string }> {
    return Object.entries(value).map(([key, item]) => ({ key, value: display(item) }));
  }
}

function display(value: unknown): string {
  if (value === undefined || value === null || value === '') return '-';
  if (typeof value === 'number') return new Intl.NumberFormat('en-US', { maximumFractionDigits: 6 }).format(value);
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  return typeof value === 'string' ? value : JSON.stringify(value);
}

function labelFromMap(value: string | undefined, labels: Record<string, string>): string {
  if (!value) return '-';
  return labels[value] ?? humanizeKey(value);
}

function humanizeKey(value: string): string {
  return value
    .replace(/^source env /i, '')
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .toLowerCase()
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}
