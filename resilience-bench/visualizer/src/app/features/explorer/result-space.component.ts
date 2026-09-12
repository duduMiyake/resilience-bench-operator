import { TooltipModule } from 'primeng/tooltip';
import { metricHelp } from '../../core/models/metric-help';
import { ChangeDetectionStrategy, Component, ViewChild, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChartData, ChartOptions, Point, Plugin } from 'chart.js';
import { ChartModule, UIChart } from 'primeng/chart';
import { ButtonModule } from 'primeng/button';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import zoomPlugin from 'chartjs-plugin-zoom';
import { NormalizedResult } from '../../core/models/visualizer.models';
import { ExplorerStateService, isInitialDecision } from '../../core/services/explorer-state.service';
import { successPercent, displayNumber } from '../../core/models/display';
import { ParetoService } from '../../core/services/pareto.service';

interface ResultPoint extends Point {
  scenario: string;
  decision?: number;
  phase?: 'Initial Sample' | 'Adaptive Search' | 'Random sample';
  observedScore?: number;
  improvedBest?: boolean;
  pareto?: boolean;
  roles?: string[];
}

interface ChartSelectEvent {
  element: unknown;
}

@Component({
  selector: 'app-result-space',
  imports: [TooltipModule, FormsModule, ButtonModule, ChartModule, ToggleSwitchModule],
  template: `
    <section class="chart-panel">
      <div class="panel-heading">
        <div>
          <h2 class="section-heading"><span class="term-help" tabindex="0" [pTooltip]="metricHelp.p95" tooltipEvent="both" tooltipPosition="top" [autoHide]="false">Success × response time (p95)</span></h2>
          <p>Checkout success versus p95 latency for the selected operational context.</p>
        </div>
        <div class="panel-tools">
          <details><summary>Chart layers and zoom</summary><div class="view-toggles" aria-label="Chart visibility options">

          <label class="path-toggle">
            <p-toggleswitch [ngModel]="showReference()" (ngModelChange)="showReference.set($event)" ariaLabel="Show Reference" />
            <span class="term-help" tabindex="0" [pTooltip]="metricHelp.reference" tooltipEvent="both" tooltipPosition="top" [autoHide]="false">Show Reference</span>
          </label>
          <label class="path-toggle">
            <p-toggleswitch [ngModel]="showPareto()" (ngModelChange)="showPareto.set($event)" ariaLabel="Show Pareto" />
            <span class="term-help" tabindex="0" [pTooltip]="metricHelp.pareto" tooltipEvent="both" tooltipPosition="top" [autoHide]="false">Show Pareto</span>
          </label>
          <label class="path-toggle">
            <p-toggleswitch [ngModel]="autoFocusSelected()" (ngModelChange)="autoFocusSelected.set($event)" ariaLabel="Auto-focus selected" />
            <span>Auto-focus selected</span>
          </label>
          </div>
          <div class="chart-actions">
            <p-button class="chart-action" label="Focus Selected" severity="secondary" [outlined]="true" [disabled]="!selectedPoint()" (onClick)="focusSelected()" />
            <p-button class="chart-action" label="Reset Zoom" severity="secondary" [outlined]="true" (onClick)="resetZoom()" />
          </div>
          </details>
        </div>
      </div>
      <div class="trajectory-controls">
        <label><input type="checkbox" [checked]="state.showSearchPath()" (change)="state.showSearchPath.set($any($event.target).checked)" /> Follow decision path</label>
        @if (state.showSearchPath()) { <label><input type="checkbox" [checked]="showFullPath()" (change)="showFullPath.set($any($event.target).checked)" /> Complete path in background</label> }
        <p-button label="Previous decision" [outlined]="true" [disabled]="!hasPreviousDecision()" (onClick)="state.selectPrevious()" />
        <span>Decision {{ state.selectedDecisionNumber() ?? '—' }} / {{ state.visibleDecisions().length }}</span>
        <p-button label="Next decision" [outlined]="true" [disabled]="!hasNextDecision()" (onClick)="state.selectNext()" />
      </div>
      @if (state.showSearchPath() && singleContext()) {
        <div class="decision-route" aria-label="Decision path">
          @for (step of routeSteps(); track step.label) {
            <button class="rb-button" [class.current]="step.label === 'Current'" [disabled]="!step.decision" (click)="state.selectDecision(step.decision!)">
              <small>{{ step.label }}</small>
              <strong>{{ step.decision === undefined ? 'End of path' : 'Decision #' + step.decision }}</strong>
              <span>{{ step.point ? successPercent(step.point.x) + ' success · ' + displayNumber(step.point.y) + ' ms' : step.decision === undefined ? 'No further decision' : 'No result for this context' }}</span>
            </button>
          }
        </div>
        <p class="chart-note">Arrows show selection order, not KNN similarity. The next decision is shown for retrospective analysis. Coincident points retain their measured positions; use the numbered cards to inspect them.</p>
      } @else if (state.showSearchPath()) {
        <p class="chart-note">Choose one operational context to follow the path. Connections are hidden while different contexts are combined.</p>
      }
      @if (hasPoints()) {
        <div class="chart-frame result-frame">
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
  readonly metricHelp = metricHelp;
  readonly state = inject(ExplorerStateService);
  private readonly paretoService = inject(ParetoService);
  readonly showReference = signal(true);
  readonly showPareto = signal(false);
  @ViewChild('resultChart') chart?: UIChart;
  readonly autoFocusSelected = signal(false);
  readonly showFullPath = signal(false);
  readonly successPercent = successPercent;
  readonly displayNumber = displayNumber;
  readonly singleContext = computed(() => new Set([...this.state.visibleResults(), ...this.state.visibleReferenceResults()].map(r => JSON.stringify([r.workloadName, r.workloadUsers, r.faultProvider, r.faultPercentage, [...r.faultServices].sort()]))).size === 1);
  readonly routeSteps = computed(() => {
    const index = this.state.selectedDecisionIndex();
    const decisions = this.state.visibleDecisions();
    const points = this.state.searchPath();
    return ['Previous', 'Current', 'Next'].map((label, offset) => {
      const decision = index < 0 ? undefined : decisions[index + offset - 1]?.decision;
      return { label, decision, point: points.find(p => p.decision === decision) };
    });
  });
  readonly chartPlugins = [zoomPlugin, decisionPathAnnotations];
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
    const singleContext = this.singleContext();
    const pathPoints = path.map((point) => this.pathPoint(point));
    const frontier = singleContext ? this.paretoService.frontier(this.state.visibleReferenceResults()) : [];

    const datasets: ChartData<'scatter', ResultPoint[]>['datasets'] = [
      ...(this.showReference() ? [{
        label: 'Reference Space',
        data: this.toPoints(this.state.visibleReferenceResults()),
        backgroundColor: this.state.showSearchPath() ? 'rgba(152, 162, 179, 0.18)' : 'rgba(152, 162, 179, 0.35)',
        borderColor: this.state.showSearchPath() ? 'rgba(102, 112, 133, 0.28)' : 'rgba(102, 112, 133, 0.45)',
        pointRadius: this.state.showSearchPath() ? 1.5 : 3,
        pointHoverRadius: 3,
        order: 10,
      }] : []),
    ];

    if (this.showPareto() && frontier.length > 0) {
      datasets.push({
        label: 'Pareto Frontier (Reference)',
        data: this.toPoints(frontier),
        backgroundColor: '#111827',
        borderColor: '#111827',
        pointRadius: 5,
        pointHoverRadius: 7,
        pointStyle: 'rectRot',
        borderWidth: 2,
        showLine: frontier.length > 1,
        tension: 0,
      });
    }

    if (this.state.showSearchPath() && singleContext) {
      if (this.showFullPath()) datasets.push({ label: 'Complete path', data: pathPoints, borderColor: 'rgba(102,112,133,.18)', borderWidth: 1, pointRadius: 0, pointHitRadius: 0, showLine: true, order: 5 });
      // Do not bridge a decision whose result is absent in this context.
      const steps = this.routeSteps();
      for (let i = 0; i < 2; i++) {
        const from = steps[i].point, to = steps[i + 1].point;
        if (from && to) datasets.push({
          label: i === 0 ? 'Incoming decision' : 'Outgoing decision',
          data: [this.pathPoint(from), this.pathPoint(to)],
          borderColor: i === 0 ? '#2563a5' : '#c2410c',
          borderWidth: 3, pointRadius: 6, pointHoverRadius: 9,
          backgroundColor: i === 0 ? '#2563a5' : '#c2410c',
          showLine: true, tension: 0, order: -5,
        });
      }
    }
    datasets.push({ label: 'Evaluated configurations', data: this.toPoints(this.state.visibleResults()), backgroundColor: this.state.showSearchPath() ? 'rgba(8,127,91,.25)' : '#087f5b', pointRadius: this.state.showSearchPath() ? 3 : 4, pointHoverRadius: 7, order: 2 });
    const paretoPoints = singleContext ? pathPoints.filter((point) => point.pareto) : [];
    if (this.showPareto() && paretoPoints.length > 0) {
      datasets.push({
        label: 'Pareto Evaluated',
        data: paretoPoints,
        backgroundColor: '#ffffff',
        borderColor: '#111827',
        pointBorderWidth: 3,
        pointRadius: 7,
        pointHoverRadius: 9,
        pointStyle: 'rectRot',
      });
    }

    for (const role of ['Best Found', 'Current Decision']) {
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
    interaction: { mode: 'nearest', intersect: false },
    plugins: {
      legend: { display: true, position: 'bottom', labels: { usePointStyle: true } },
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
            if (point.pareto) lines.push('Pareto Frontier: Evaluated');
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
      x: { title: { display: true, text: 'Checkout success (%) — higher is better' }, ticks: { callback: (value) => `${(Number(value) * 100).toFixed(0)}%` }, grid: { color: '#eaecf0' } },
      y: {
        title: { display: true, text: 'Response time p95 (ms) — lower is better' },
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
          phase: decision ? (this.state.run()?.strategy === 'randomSampling' ? 'Random sample' : isInitialDecision(decision) ? 'Initial Sample' : 'Adaptive Search') : undefined,
          observedScore: decision ? decision.aggregatedResult?.score ?? decision.aggregatedResult?.currentScore : undefined,
          improvedBest: decision?.aggregatedResult?.improvedBest,
        };
      });
  }

  private pathPoint(point: { decision: number; x: number; y: number; phase: 'initial' | 'adaptive'; observedScore?: number; improvedBest?: boolean; pareto?: boolean }): ResultPoint {
    return {
      x: point.x,
      y: point.y,
      scenario: `Decision #${point.decision}`,
      decision: point.decision,
      phase: this.state.run()?.strategy === 'randomSampling' ? 'Random sample' : point.phase === 'initial' ? 'Initial Sample' : 'Adaptive Search',
      observedScore: point.observedScore,
      improvedBest: point.improvedBest,
      pareto: point.pareto,
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
    return { label: role, data: points, ...style, order: -10 };
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

const decisionPathAnnotations: Plugin<'scatter'> = {
  id: 'decision-path-annotations',
  afterDatasetsDraw(chart) {
    const { ctx, chartArea } = chart;
    const labeled = new Set<number>();
    ctx.save(); ctx.beginPath();
    ctx.rect(chartArea.left, chartArea.top, chartArea.width, chartArea.height); ctx.clip();
    chart.data.datasets.forEach((dataset, index) => {
      if (!['Incoming decision', 'Outgoing decision'].includes(dataset.label ?? '') || !chart.isDatasetVisible(index)) return;
      const meta = chart.getDatasetMeta(index), [from, to] = meta.data;
      if (!from || !to) return;
      const dx = to.x - from.x, dy = to.y - from.y;
      const color = dataset.label === 'Incoming decision' ? '#2563a5' : '#c2410c';
      if (Math.hypot(dx, dy) > 24) {
        const angle = Math.atan2(dy, dx), x = from.x + dx * .65, y = from.y + dy * .65;
        ctx.beginPath(); ctx.moveTo(x, y);
        ctx.lineTo(x - 10 * Math.cos(angle - .5), y - 10 * Math.sin(angle - .5));
        ctx.lineTo(x - 10 * Math.cos(angle + .5), y - 10 * Math.sin(angle + .5));
        ctx.closePath(); ctx.fillStyle = color; ctx.fill();
      }
      meta.data.forEach((element, pointIndex) => {
        const point = dataset.data[pointIndex] as ResultPoint;
        if (point.decision === undefined || labeled.has(point.decision)) return;
        labeled.add(point.decision);
        const label = '#' + point.decision;
        ctx.font = '600 13px sans-serif';
        const width = ctx.measureText(label).width + 12;
        const x = Math.max(chartArea.left, Math.min(element.x + 12, chartArea.right - width));
        const y = Math.max(chartArea.top + 20, Math.min(element.y - 12, chartArea.bottom));
        ctx.fillStyle = 'rgba(255,255,255,.96)'; ctx.fillRect(x, y - 18, width, 22);
        ctx.fillStyle = color; ctx.fillText(label, x + 6, y - 2);
      });
    });
    ctx.restore();
  },
};
