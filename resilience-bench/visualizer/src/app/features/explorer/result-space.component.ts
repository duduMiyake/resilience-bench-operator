import { ChangeDetectionStrategy, Component, ViewChild, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChartData, ChartOptions, Point } from 'chart.js';
import { ChartModule, UIChart } from 'primeng/chart';
import { ButtonModule } from 'primeng/button';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import zoomPlugin from 'chartjs-plugin-zoom';
import { NormalizedResult } from '../../core/models/visualizer.models';
import { ExplorerStateService, isInitialDecision } from '../../core/services/explorer-state.service';

interface ResultPoint extends Point {
  scenario: string;
  decision?: number;
  phase?: 'Initial Sample' | 'Adaptive Search';
  observedScore?: number;
  improvedBest?: boolean;
  roles?: string[];
}

interface ChartSelectEvent {
  element: unknown;
}

@Component({
  selector: 'app-result-space',
  imports: [FormsModule, ButtonModule, ChartModule, ToggleSwitchModule],
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
          <label class="path-toggle">
            <p-toggleswitch [ngModel]="autoFocusSelected()" (ngModelChange)="autoFocusSelected.set($event)" ariaLabel="Auto-focus selected" />
            <span>Auto-focus selected</span>
          </label>
          <div class="decision-navigation" aria-label="Decision navigation">
            <p-button icon="pi pi-chevron-left" severity="secondary" [text]="true" [disabled]="!hasPreviousDecision()" ariaLabel="Previous decision" (onClick)="state.selectPrevious()" />
            <span>Decision {{ selectedPosition() }} of {{ state.visibleDecisions().length }}</span>
            <p-button icon="pi pi-chevron-right" severity="secondary" [text]="true" [disabled]="!hasNextDecision()" ariaLabel="Next decision" (onClick)="state.selectNext()" />
          </div>
          <div class="chart-actions">
            <p-button class="chart-action" label="Focus Selected" severity="secondary" [outlined]="true" [disabled]="!selectedPoint()" (onClick)="focusSelected()" />
            <p-button class="chart-action" label="Reset Zoom" severity="secondary" [text]="true" (onClick)="resetZoom()" />
          </div>
          <div class="legend" aria-label="Legend">
            <span><i class="reference"></i>Reference Space</span>
            <span><i class="path"></i>Search Path</span>
            <span><i class="start"></i>Start</span>
            <span><i class="selected"></i>Current Decision</span>
            <span><i class="best-found"></i>Best Found</span>
            <span><i class="end"></i>End</span>
          </div>
        </div>
      </div>
      @if (hasPoints()) {
        <div class="chart-frame">
          <span class="better-x">Better -&gt;</span>
          <span class="better-y">Better down</span>
          <span class="preferred-zone">Preferred region</span>
          <p-chart #resultChart type="scatter" [data]="data()" [options]="options" [plugins]="chartPlugins" height="100%" ariaLabel="Result Space" (onDataSelect)="selectPoint($event)" />
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
  @ViewChild('resultChart') chart?: UIChart;
  readonly autoFocusSelected = signal(false);
  readonly chartPlugins = [zoomPlugin];
  readonly selectedPosition = computed(() => Math.max(0, this.state.selectedDecisionIndex() + 1));
  readonly hasPreviousDecision = computed(() => this.state.selectedDecisionIndex() > 0);
  readonly hasNextDecision = computed(() => {
    const index = this.state.selectedDecisionIndex();
    return index >= 0 && index < this.state.visibleDecisions().length - 1;
  });

  private readonly autoFocusEffect = effect(() => {
    const selected = this.state.selectedDecisionNumber();
    if (this.autoFocusSelected() && selected !== null) {
      queueMicrotask(() => this.focusSelected());
    }
  });

  readonly data = computed<ChartData<'scatter', ResultPoint[]>>(() => {
    const path = this.state.searchPath();
    const pathPoints = path.map((point) => this.pathPoint(point));

    const datasets: ChartData<'scatter', ResultPoint[]>['datasets'] = [
      {
        label: 'Reference Space',
        data: this.toPoints(this.state.visibleReferenceResults()),
        backgroundColor: this.state.showSearchPath() ? 'rgba(152, 162, 179, 0.18)' : 'rgba(152, 162, 179, 0.35)',
        borderColor: this.state.showSearchPath() ? 'rgba(102, 112, 133, 0.28)' : 'rgba(102, 112, 133, 0.45)',
        pointRadius: 3,
        pointHoverRadius: 5,
      },
    ];

    if (this.state.showSearchPath()) {
      datasets.push({
        label: 'Search Path',
        data: pathPoints,
        borderColor: 'rgba(8, 127, 91, 0.45)',
        backgroundColor: 'rgba(8, 127, 91, 0.1)',
        borderWidth: 2,
        pointRadius: 2,
        pointHoverRadius: 5,
        pointStyle: (context) => (context.raw as ResultPoint).phase === 'Initial Sample' ? 'circle' : 'rectRot',
        showLine: true,
        spanGaps: false,
        tension: 0.12,
      });
    }

    for (const role of ['Start', 'Best Found', 'End', 'Current Decision']) {
      const points = pathPoints.filter((point) => this.markerRole(point) === role);
      if (points.length > 0) {
        datasets.push(this.markerDataset(role, points));
      }
    }

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
              `p95 Iteration Duration: ${formatMilliseconds(point.y ?? 0)} ms`,
            );
            if (point.decision) {
              if (point.roles?.length) {
                lines.push(`Role: ${point.roles.join(' · ')}`);
              }
              lines.push(
                `Observed Score: ${formatNumber(point.observedScore)}`,
                `New Best: ${point.improvedBest ? 'Yes' : 'No'}`,
              );
            }
            return lines;
          },
        },
      },
      zoom: {
        pan: { enabled: true, mode: 'xy' },
        zoom: {
          wheel: { enabled: true, modifierKey: 'ctrl' },
          pinch: { enabled: true },
          mode: 'xy',
        },
      },
    },
    scales: {
      x: { title: { display: true, text: 'Checkout Success Rate' }, grid: { color: '#eaecf0' } },
      y: {
        title: { display: true, text: 'p95 Iteration Duration (ms)' },
        grid: { color: '#eaecf0' },
        ticks: { callback: (value) => formatMilliseconds(Number(value)) },
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

  selectedPoint(): ResultPoint | undefined {
    const selected = this.state.selectedDecisionNumber();
    return this.toPoints(this.state.visibleResults()).find((point) => point.decision === selected);
  }

  focusSelected(): void {
    const chart = this.chart?.chart;
    const point = this.selectedPoint();
    if (!chart || !point || !chart.scales?.x || !chart.scales?.y) {
      return;
    }
    const xBounds = this.focusBounds(chart.scales.x, 'x', point.x);
    const yBounds = this.focusBounds(chart.scales.y, 'y', point.y);
    chart.zoomScale('x', xBounds, 'active');
    chart.zoomScale('y', yBounds, 'active');
  }

  resetZoom(): void {
    this.chart?.chart?.resetZoom('active');
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

  private pathPoint(point: { decision: number; x: number; y: number; phase: 'initial' | 'adaptive'; observedScore?: number; improvedBest?: boolean }): ResultPoint {
    return {
      x: point.x,
      y: point.y,
      scenario: `Decision #${point.decision}`,
      decision: point.decision,
      phase: point.phase === 'initial' ? 'Initial Sample' : 'Adaptive Search',
      observedScore: point.observedScore,
      improvedBest: point.improvedBest,
      roles: this.roleMap(this.state.searchPath().map((item) => item.decision)).get(point.decision),
    };
  }

  private roleMap(decisions: number[]): Map<number, string[]> {
    const roles = new Map<number, string[]>();
    const add = (decision: number | undefined, role: string) => {
      if (decision !== undefined) roles.set(decision, [...(roles.get(decision) ?? []), role]);
    };
    add(decisions[0], 'Start');
    add(this.state.bestFound()?.decision, 'Best Found');
    add(decisions.at(-1), 'End');
    add(this.state.selectedDecisionNumber() ?? undefined, 'Current');
    return roles;
  }

  private markerDataset(role: string, points: ResultPoint[]) {
    const style = role === 'Current Decision'
      ? { backgroundColor: '#fff7ed', borderColor: '#c2410c', pointBorderWidth: 4, pointRadius: 10, pointHoverRadius: 11, pointStyle: 'circle' }
      : role === 'Best Found'
        ? { backgroundColor: '#fef3c7', borderColor: '#b45309', pointBorderWidth: 3, pointRadius: 8, pointHoverRadius: 10, pointStyle: 'triangle' }
        : { backgroundColor: '#e0f2fe', borderColor: '#2563a5', pointBorderWidth: 3, pointRadius: 7, pointHoverRadius: 9, pointStyle: 'circle' };
    return { label: role, data: points, ...style };
  }

  private markerRole(point: ResultPoint): string | undefined {
    if (point.roles?.includes('Current')) return 'Current Decision';
    if (point.roles?.includes('Best Found')) return 'Best Found';
    if (point.roles?.includes('Start')) return 'Start';
    if (point.roles?.includes('End')) return 'End';
    return undefined;
  }

  private focusBounds(scale: { min: number; max: number }, axis: 'x' | 'y', value: number): { min: number; max: number } {
    const initial = this.chart?.chart?.getInitialScaleBounds?.()[axis];
    const fullMin = initial?.min ?? scale.min;
    const fullMax = initial?.max ?? scale.max;
    const fullRange = fullMax - fullMin;
    const currentRange = scale.max - scale.min;
    const range = Math.max(fullRange * 0.35, currentRange * 0.5, Number.EPSILON);
    const min = Math.max(fullMin, value - range / 2);
    return { min, max: Math.min(fullMax, min + range) };
  }
}

function formatNumber(value: number | undefined): string {
  return value === undefined ? '-' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 }).format(value);
}

function formatPercent(value: number): string {
  return `${new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value * 100)}%`;
}

function formatMilliseconds(value: number): string {
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value);
}
