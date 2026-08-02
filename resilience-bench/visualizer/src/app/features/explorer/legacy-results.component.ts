import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TableModule } from 'primeng/table';
import { NormalizedResult } from '../../core/models/visualizer.models';

@Component({
  selector: 'app-legacy-results',
  imports: [TableModule],
  template: `
    <section class="legacy-panel">
      <h2 class="section-heading">Configura&ccedil;&otilde;es e resultados</h2>
      @if (results().length) {
        <p-table
          [value]="results()"
          [scrollable]="true"
          [paginator]="results().length > 20"
          [rows]="20"
          styleClass="p-datatable-sm"
        >
          <ng-template #header>
            <tr>
              <th>Cen&aacute;rio</th>
              <th>Contexto</th>
              <th>Sucesso</th>
              <th>p95 (s)</th>
              <th>Configura&ccedil;&atilde;o</th>
            </tr>
          </ng-template>
          <ng-template #body let-result>
            <tr>
              <td>{{ result.scenario }}</td>
              <td>{{ value(result.workloadUsers) }} VUs ? {{ value(result.faultPercentage) }}%</td>
              <td>{{ value(result.checkoutSuccessRate) }}</td>
              <td>{{ value(result.iterationDurationP95) }}</td>
              <td>{{ connectorSummary(result) }}</td>
            </tr>
          </ng-template>
        </p-table>
      } @else {
        <div class="empty-state">Nenhum resultado exhaustive foi encontrado.</div>
      }
    </section>
  `,
  styles: `
    .legacy-panel {
      padding: 1rem;
      border: 1px solid var(--rb-border);
      border-radius: 6px;
      background: var(--rb-surface);
    }

    .legacy-panel h2 {
      margin-bottom: 0.8rem;
    }

    td {
      max-width: 420px;
      overflow-wrap: anywhere;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LegacyResultsComponent {
  readonly results = input.required<NormalizedResult[]>();

  value(value?: number): string {
    return value === undefined
      ? '-'
      : new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 5 }).format(value);
  }

  connectorSummary(result: NormalizedResult): string {
    if (!result.connectors.length) {
      return '-';
    }
    return result.connectors
      .map((value) => {
        if (typeof value !== 'object' || value === null) {
          return String(value);
        }
        const connector = value as Record<string, unknown>;
        const name = connector['name'] ?? 'connector';
        const source = connector['source'] ?? '?';
        const destination = connector['destination'] ?? '?';
        return `${name}: ${source} -> ${destination}`;
      })
      .join(', ');
  }
}
