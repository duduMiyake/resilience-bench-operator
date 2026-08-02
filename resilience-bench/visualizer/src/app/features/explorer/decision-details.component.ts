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
    return value ? 'Sim' : 'N\\u00e3o';
  }
  return typeof value === 'string' ? value : JSON.stringify(value);
}
