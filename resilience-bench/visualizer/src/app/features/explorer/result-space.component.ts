import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ChartData, ChartOptions, Point } from 'chart.js';
import { ChartModule } from 'primeng/chart';
import { NormalizedResult } from '../../core/models/visualizer.models';
import { ExplorerStateService } from '../../core/services/explorer-state.service';

interface ResultPoint extends Point {
  scenario: string;
  decision?: number;
}

interface ChartSelectEvent {
  element: unknown;
}

@Component({
  selector: 'app-result-space',
  imports: [ChartModule],
  template: `
    <section class="chart-panel">
      <div class="panel-heading">
        <div>
          <h2 class="section-heading">Result Space</h2>
          <p>Sucesso do checkout versus p95 da dura&ccedil;&atilde;o da itera&ccedil;&atilde;o.</p>
        </div>
        <div class="legend" aria-label="Legenda">
          <span><i class="reference"></i>Refer&ecirc;ncia</span>
          <span><i class="visited"></i>Visitado</span>
          <span><i class="selected"></i>Selecionado</span>
        </div>
      </div>
      @if (hasPoints()) {
        <div class="chart-frame">
          <p-chart
            type="scatter"
            [data]="data()"
            [options]="options"
            height="100%"
            ariaLabel="Espa&ccedil;o de resultados"
            (onDataSelect)="selectPoint($event)"
          />
        </div>
      } @else {
        <div class="empty-state">
          N&atilde;o h&aacute; m&eacute;tricas de sucesso e p95 para este contexto.
        </div>
      }
    </section>
  `,
  styleUrl: './chart-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResultSpaceComponent {
  readonly state = inject(ExplorerStateService);

  readonly data = computed<ChartData<'scatter', ResultPoint[]>>(() => {
    const selectedDecision = this.state.selectedDecisionNumber();
    const references = this.toPoints(this.state.visibleReferenceResults());
    const visitedResults = this.state
      .visibleResults()
      .filter(
        (result) =>
          result.joinedDecision !== undefined && result.joinedDecision !== selectedDecision,
      );
    const selectedResults = this.state
      .visibleResults()
      .filter((result) => result.joinedDecision === selectedDecision);

    return {
      datasets: [
        {
          label: 'Refer\\u00eancia / exhaustive',
          data: references,
          backgroundColor: '#98a2b3',
          borderColor: '#667085',
          pointRadius: 4,
          pointHoverRadius: 6,
        },
        {
          label: 'Configura\\u00e7\\u00f5es visitadas',
          data: this.toPoints(visitedResults),
          backgroundColor: '#087f5b',
          borderColor: '#065f46',
          pointRadius: 5,
          pointHoverRadius: 7,
        },
        {
          label: 'Decis\\u00e3o selecionada',
          data: this.toPoints(selectedResults),
          backgroundColor: '#c2410c',
          borderColor: '#7c2d12',
          pointRadius: 7,
          pointHoverRadius: 8,
        },
      ],
    };
  });

  readonly hasPoints = computed(() =>
    this.data().datasets.some((dataset) => dataset.data.length > 0),
  );

  readonly options: ChartOptions<'scatter'> = {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    parsing: false,
    interaction: { mode: 'nearest', intersect: true },
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (context) => {
            const point = context.raw as ResultPoint;
            return [
              point.scenario,
              `Sucesso: ${formatMetric(point.x ?? 0)}`,
              `p95: ${formatMetric(point.y ?? 0)} s`,
              point.decision ? `Decis\\u00e3o: ${point.decision}` : 'Refer\\u00eancia',
            ];
          },
        },
      },
    },
    scales: {
      x: {
        title: { display: true, text: 'checkout_success_rate' },
        grid: { color: '#eaecf0' },
      },
      y: {
        title: { display: true, text: 'iteration_duration_p95 (s)' },
        grid: { color: '#eaecf0' },
      },
    },
  };

  selectPoint(event: ChartSelectEvent): void {
    const element = event.element as { datasetIndex?: number; index?: number } | undefined;
    if (element?.datasetIndex === undefined || element.index === undefined) {
      return;
    }
    const point = this.data().datasets[element.datasetIndex]?.data[element.index];
    if (point?.decision !== undefined) {
      this.state.selectDecision(point.decision);
    }
  }

  private toPoints(results: NormalizedResult[]): ResultPoint[] {
    return results
      .filter(
        (result) =>
          result.checkoutSuccessRate !== undefined && result.iterationDurationP95 !== undefined,
      )
      .map((result) => ({
        x: result.checkoutSuccessRate!,
        y: result.iterationDurationP95!,
        scenario: result.scenario,
        decision: result.joinedDecision,
      }));
  }
}

function formatMetric(value: number): string {
  return new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 4 }).format(value);
}
