import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChartData, ChartOptions, Point } from 'chart.js';
import { ChartModule } from 'primeng/chart';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { NormalizedResult } from '../../core/models/visualizer.models';
import { ExplorerStateService, isInitialDecision } from '../../core/services/explorer-state.service';

interface ResultPoint extends Point {
  scenario: string;
  decision?: number;
  phase?: 'Initial Sample' | 'Adaptive Search';
  observedScore?: number;
  improvedBest?: boolean;
}

interface ChartSelectEvent {
  element: unknown;
}

@Component({
  selector: 'app-result-space',
  imports: [FormsModule, ChartModule, ToggleSwitchModule],
  template: `
    <section class="chart-panel">
      <div class="panel-heading">
        <div>
          <h2 class="section-heading">Result Space</h2>
          <p>Checkout success versus p95 latency for the selected operational context.</p>
        </div>
        <div class="panel-tools">
          <label class="path-toggle">
            <p-toggleswitch [ngModel]="state.showSearchPath()" (ngModelChange)="state.showSearchPath.set($event)" ariaLabel="Show Search Path" />
            <span>Show Search Path</span>
          </label>
          <div class="legend" aria-label="Legend">
            <span><i class="reference"></i>Reference Space</span>
            <span><i class="initial"></i>Initial Sample</span>
            <span><i class="adaptive"></i>Adaptive Search</span>
            <span><i class="selected"></i>Selected Decision</span>
            <span><i class="new-best"></i>New Best</span>
          </div>
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
    const visibleResults = this.state.visibleResults();
    const initialResults = visibleResults.filter((result) => this.isHeuristicPoint(result, selectedDecision) && this.isInitialResult(result));
    const adaptiveResults = visibleResults.filter((result) => this.isHeuristicPoint(result, selectedDecision) && !this.isInitialResult(result));
    const selectedResults = visibleResults.filter((result) => result.joinedDecision === selectedDecision);
    const newBestResults = visibleResults.filter((result) => {
      const decision = this.decisionFor(result);
      return Boolean(decision?.aggregatedResult?.improvedBest) && result.joinedDecision !== selectedDecision;
    });

    const datasets: ChartData<'scatter', ResultPoint[]>['datasets'] = [
      {
        label: 'Reference Space',
        data: this.toPoints(this.state.visibleReferenceResults()),
        backgroundColor: 'rgba(152, 162, 179, 0.35)',
        borderColor: 'rgba(102, 112, 133, 0.45)',
        pointRadius: 3,
        pointHoverRadius: 5,
      },
    ];

    if (this.state.showSearchPath()) {
      datasets.push({
        label: 'Search Path',
        data: this.state.searchPath().map((point) => ({
          x: point.x,
          y: point.y,
          scenario: `Decision #${point.decision}`,
          decision: point.decision,
          phase: point.phase === 'initial' ? 'Initial Sample' : 'Adaptive Search',
          observedScore: point.observedScore,
          improvedBest: point.improvedBest,
        })),
        borderColor: 'rgba(8, 127, 91, 0.45)',
        backgroundColor: 'rgba(8, 127, 91, 0.1)',
        borderWidth: 2,
        pointRadius: 0,
        pointHoverRadius: 0,
        showLine: true,
        spanGaps: false,
        tension: 0.12,
      });
    }

    datasets.push(
      {
        label: 'Initial Sample',
        data: this.toPoints(initialResults),
        backgroundColor: '#2563a5',
        borderColor: '#1d4f83',
        pointRadius: 5,
        pointHoverRadius: 7,
        pointStyle: 'circle',
      },
      {
        label: 'Adaptive Search',
        data: this.toPoints(adaptiveResults),
        backgroundColor: '#087f5b',
        borderColor: '#065f46',
        pointRadius: 5,
        pointHoverRadius: 7,
        pointStyle: 'rectRot',
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
        backgroundColor: '#fff7ed',
        borderColor: '#c2410c',
        pointBorderWidth: 4,
        pointRadius: 10,
        pointHoverRadius: 11,
        pointStyle: 'circle',
      },
    );

    return { datasets };
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
            const lines = point.decision
              ? [`Decision #${point.decision}`, point.phase ?? 'Adaptive Search']
              : ['Reference Configuration'];
            lines.push(
              `Checkout Success Rate: ${formatPercent(point.x ?? 0)}`,
              `p95 Iteration Duration: ${formatSeconds(point.y ?? 0)} s`,
            );
            if (point.decision) {
              lines.push(
                `Observed Score: ${formatNumber(point.observedScore)}`,
                `New Best: ${point.improvedBest ? 'Yes' : 'No'}`,
              );
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

  private isHeuristicPoint(result: NormalizedResult, selectedDecision: number | null): boolean {
    return result.joinedDecision !== undefined && result.joinedDecision !== selectedDecision;
  }

  private isInitialResult(result: NormalizedResult): boolean {
    const decision = this.decisionFor(result);
    return decision ? isInitialDecision(decision) : false;
  }

  private decisionFor(result: NormalizedResult) {
    return result.joinedDecision === undefined
      ? undefined
      : this.state.visibleDecisions().find((decision) => decision.decision === result.joinedDecision);
  }

  private toPoints(results: NormalizedResult[]): ResultPoint[] {
    return results
      .filter((result) => result.checkoutSuccessRate !== undefined && result.iterationDurationP95 !== undefined)
      .map((result) => {
        const decision = this.decisionFor(result);
        return {
          x: result.checkoutSuccessRate!,
          y: result.iterationDurationP95!,
          scenario: result.scenario,
          decision: result.joinedDecision,
          phase: decision ? (isInitialDecision(decision) ? 'Initial Sample' : 'Adaptive Search') : undefined,
          observedScore: decision ? decision.aggregatedResult?.currentScore ?? decision.aggregatedResult?.score : undefined,
          improvedBest: decision?.aggregatedResult?.improvedBest,
        };
      });
  }
}

function formatNumber(value: number | undefined): string {
  return value === undefined ? '-' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 }).format(value);
}

function formatPercent(value: number): string {
  return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value * 100)}%`;
}

function formatSeconds(value: number): string {
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value);
}
