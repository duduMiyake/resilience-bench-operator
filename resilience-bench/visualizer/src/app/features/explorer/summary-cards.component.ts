import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NormalizedRun } from '../../core/models/visualizer.models';

@Component({
  selector: 'app-summary-cards',
  template: `
    <section class="summary-grid" aria-label="Resumo da rodada">
      @for (item of items(); track item.label) {
        <article class="summary-item">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          @if (item.detail) {
            <small>{{ item.detail }}</small>
          }
        </article>
      }
    </section>
  `,
  styles: `
    .summary-grid {
      display: grid;
      grid-template-columns: repeat(5, minmax(130px, 1fr));
      border: 1px solid var(--rb-border);
      border-radius: 6px;
      background: var(--rb-surface);
      overflow: hidden;
    }

    .summary-item {
      display: grid;
      min-height: 92px;
      align-content: center;
      gap: 0.2rem;
      padding: 0.9rem 1rem;
      border-right: 1px solid var(--rb-border);
    }

    .summary-item:last-child {
      border-right: 0;
    }

    span,
    small {
      color: var(--rb-muted);
      font-size: 0.75rem;
    }

    strong {
      color: var(--rb-ink);
      font-size: 1.35rem;
      letter-spacing: 0;
    }

    @media (max-width: 900px) {
      .summary-grid {
        grid-template-columns: repeat(2, 1fr);
      }

      .summary-item {
        border-bottom: 1px solid var(--rb-border);
      }
    }

    @media (max-width: 520px) {
      .summary-grid {
        grid-template-columns: 1fr;
      }

      .summary-item {
        border-right: 0;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SummaryCardsComponent {
  readonly run = input.required<NormalizedRun>();

  readonly items = computed(() => {
    const run = this.run();
    const explored = percentage(run.totalConfigurationsSelected, run.totalConfigurationSpaceSize);
    return [
      { label: 'Espa\\u00e7o de busca', value: format(run.totalConfigurationSpaceSize) },
      {
        label: 'Configura\\u00e7\\u00f5es avaliadas',
        value: format(run.totalConfigurationsEvaluated),
      },
      {
        label: 'Explorado',
        value: explored,
        detail: valuePair(run.totalConfigurationsSelected, run.totalConfigurationSpaceSize),
      },
      { label: 'Cen\\u00e1rios executados', value: format(run.totalScenariosExecuted) },
      { label: 'Cache hits', value: format(run.totalCacheHits) },
    ];
  });
}

function format(value?: number): string {
  return value === undefined ? '-' : new Intl.NumberFormat('pt-BR').format(value);
}

function percentage(selected?: number, total?: number): string {
  return selected === undefined || total === undefined || total === 0
    ? '-'
    : `${((selected / total) * 100).toFixed(1)}%`;
}

function valuePair(selected?: number, total?: number): string | undefined {
  return selected === undefined || total === undefined
    ? undefined
    : `${format(selected)} de ${format(total)}`;
}
