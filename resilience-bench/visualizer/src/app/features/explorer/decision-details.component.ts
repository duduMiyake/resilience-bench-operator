import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { JsonRecord } from '../../core/models/visualizer.models';
import { ExplorerStateService } from '../../core/services/explorer-state.service';

@Component({
  selector: 'app-decision-details',
  imports: [TableModule, TagModule],
  templateUrl: './decision-details.component.html',
  styleUrl: './decision-details.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecisionDetailsComponent {
  readonly state = inject(ExplorerStateService);
  readonly knownMetadataKeys = [
    'predictedScore',
    'uncertainty',
    'explorationBonus',
    'selectionScore',
  ] as const;

  readonly genericMetadata = computed(() => {
    const metadata = this.state.selectedDecision()?.metadata ?? {};
    const excluded = new Set<string>([...this.knownMetadataKeys, 'nearestNeighbors']);
    return Object.entries(metadata)
      .filter(([key]) => !excluded.has(key))
      .map(([key, value]) => ({ key, value: display(value) }));
  });

  metadataValue(key: string): string {
    return display(this.state.selectedDecision()?.metadata[key]);
  }

  value(value: unknown): string {
    return display(value);
  }

  shortHash(hash?: string): string {
    return hash ? hash.slice(0, 12) : '-';
  }

  executionSeverity(source: string): 'success' | 'info' | 'secondary' {
    return source === 'cacheHit' ? 'info' : source === 'executed' ? 'success' : 'secondary';
  }

  phaseLabel(phase?: string): string {
    return labelFromMap(phase, {
      initialSample: 'Amostra inicial',
      initialSelection: 'Amostra inicial',
      adaptiveSelection: 'Escolha adaptativa',
      completed: 'Conclu\u00edda',
    });
  }

  selectionModeLabel(mode?: string): string {
    return labelFromMap(mode, {
      INITIAL_BATCH: 'Lote inicial',
      SEQUENTIAL: 'Escolha sequencial',
    });
  }

  metadataLabel(key: string): string {
    return labelFromMap(key, {
      predictedScore: 'Score previsto',
      uncertainty: 'Incerteza',
      explorationBonus: 'B\u00f4nus de explora\u00e7\u00e3o',
      selectionScore: 'Score de escolha',
      nearestNeighbors: 'Vizinhos mais pr\u00f3ximos',
    });
  }

  parameterLabel(path: string): string {
    return labelFromMap(path.split('.').pop(), {
      GRPC_MAX_ATTEMPTS: 'Tentativas m\u00e1ximas',
      GRPC_INITIAL_BACKOFF: 'Backoff inicial',
      GRPC_MAX_BACKOFF: 'Backoff m\u00e1ximo',
      GRPC_BACKOFF_MULTIPLIER: 'Multiplicador do backoff',
    });
  }

  connectorStrategyLabel(strategy: string): string {
    return labelFromMap(strategy, {
      CONFIGURED: 'Configurado',
      BASELINE: 'Baseline',
    });
  }

  executionSourceLabel(source: string): string {
    return labelFromMap(source, {
      cacheHit: 'Cache',
      executed: 'Executado',
    });
  }

  metadataEntries(value: JsonRecord): Array<{ key: string; value: string }> {
    return Object.entries(value).map(([key, item]) => ({ key, value: display(item) }));
  }
}

function display(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '-';
  }
  if (typeof value === 'number') {
    return new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 6 }).format(value);
  }
  if (typeof value === 'boolean') {
    return value ? 'Sim' : 'N\u00e3o';
  }
  return typeof value === 'string' ? value : JSON.stringify(value);
}

function labelFromMap(value: string | undefined, labels: Record<string, string>): string {
  if (!value) {
    return '-';
  }
  return labels[value] ?? humanizeKey(value);
}

function humanizeKey(value: string): string {
  return value
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}
