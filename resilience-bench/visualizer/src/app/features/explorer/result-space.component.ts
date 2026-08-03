import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ChartData, ChartOptions, Point } from 'chart.js';
import { ChartModule } from 'primeng/chart';
import { NormalizedResult } from '../../core/models/visualizer.models';
import { ExplorerStateService } from '../../core/services/explorer-state.service';

interface ResultPoint extends Point {
  scenario: string;
  decision?: number;
  observedScore?: number;
  improvedBest?: boolean;
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
          <p>Checkout success versus p95 latency for the selected operational context.</p>
        </div>
        <div class="legend" aria-label="Legend">
          <span><i class="reference"></i>Reference Space</span>
          <span><i class="visited"></i>Evaluated by Heuristic</span>
          <span><i class="selected"></i>Selected Decision</span>
          <span><i class="new-best"></i>New Best</span>
        </div>
      </div>
      @if (hasPoints()) {
        <div class="chart-frame">
          <span class="better-x">Better -&gt;</span>
          <span class="better-y">Better down</span>
          <span class="preferred-zone">Preferred region</span>
          <p-chart type="scatter" [data]="data()" [options]="options" height="100%" ariaLabel="Result Space" (onDataSelect)="selectPoint($event)" />
        </div>
      } @else {
        <div class="empty-state">No checkout success and p95 latency metrics are available for this context.</div>
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
    const decisionByNumber = new Map(this.state.visibleDecisions().map((decision) => [decision.decision, decision]));
    const visibleResults = this.state.visibleResults();
    const visitedResults = visibleResults.filter((result) => result.joinedDecision !== undefined && result.joinedDecision !== selectedDecision);
    const selectedResults = visibleResults.filter((result) => result.joinedDecision === selectedDecision);
    const newBestResults = visibleResults.filter((result) => {
      const decision = result.joinedDecision === undefined ? undefined : decisionByNumber.get(result.joinedDecision);
      return Boolean(decision?.aggregatedResult?.improvedBest) && result.joinedDecision !== selectedDecision;
    });

    return {
      datasets: [
        {
          label: 'Reference Space',
          data: this.toPoints(this.state.visibleReferenceResults()),
          backgroundColor: 'rgba(152, 162, 179, 0.42)',
          borderColor: 'rgba(102, 112, 133, 0.55)',
          pointRadius: 3,
          pointHoverRadius: 5,
        },
        {
          label: 'Evaluated by Heuristic',
          data: this.toPoints(visitedResults),
          backgroundColor: '#087f5b',
          borderColor: '#065f46',
          pointRadius: 5,
          pointHoverRadius: 7,
        },
        {
          label: 'New Best',
          data: this.toPoints(newBestResults),
          backgroundColor: '#f59e0b',
          borderColor: '#92400e',
          pointRadius: 7,
          pointHoverRadius: 8,
          pointStyle: 'triangle',
        },
        {
          label: 'Selected Decision',
          data: this.toPoints(selectedResults),
          backgroundColor: '#c2410c',
          borderColor: '#7c2d12',
          pointRadius: 8,
          pointHoverRadius: 9,
        },
      ],
    };
  });

  readonly hasPoints = computed(() => this.data().datasets.some((dataset) => dataset.data.length > 0));

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
            const lines = [
              point.decision ? `Decision #${point.decision}` : 'Reference configuration',
              `Success Rate: ${formatPercent(point.x ?? 0)}`,
              `p95: ${formatSeconds(point.y ?? 0)} s`,
            ];
            if (point.observedScore !== undefined) {
              lines.push(`Observed Score: ${formatNumber(point.observedScore)}`);
            }
            if (point.improvedBest) {
              lines.push('New best found');
            }
            return lines;
          },
        },
      },
    },
    scales: {
      x: { title: { display: true, text: 'Checkout Success Rate' }, grid: { color: '#eaecf0' } },
      y: { title: { display: true, text: 'p95 Iteration Duration (s)' }, grid: { color: '#eaecf0' } },
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
    const decisionByNumber = new Map(this.state.visibleDecisions().map((decision) => [decision.decision, decision]));
    return results
      .filter((result) => result.checkoutSuccessRate !== undefined && result.iterationDurationP95 !== undefined)
      .map((result) => {
        const decision = result.joinedDecision === undefined ? undefined : decisionByNumber.get(result.joinedDecision);
        return {
          x: result.checkoutSuccessRate!,
          y: result.iterationDurationP95!,
          scenario: result.scenario,
          decision: result.joinedDecision,
          observedScore: decision?.aggregatedResult?.currentScore ?? decision?.aggregatedResult?.score,
          improvedBest: decision?.aggregatedResult?.improvedBest,
        };
      });
  }
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 }).format(value);
}

function formatPercent(value: number): string {
  return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value * 100)}%`;
}

function formatSeconds(value: number): string {
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value);
}
