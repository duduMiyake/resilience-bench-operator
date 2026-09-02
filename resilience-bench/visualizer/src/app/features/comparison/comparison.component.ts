import { ChangeDetectionStrategy, Component, ViewChild, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChartData, ChartOptions, Point } from 'chart.js';
import { ButtonModule } from 'primeng/button';
import { ChartModule, UIChart } from 'primeng/chart';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import zoomPlugin from 'chartjs-plugin-zoom';
import { ComparisonModel, ComparisonPoint, ComparisonRun } from '../../core/models/comparison.models';
import { NormalizedResult } from '../../core/models/visualizer.models';
import { ParetoService } from '../../core/services/pareto.service';

@Component({
  selector: 'app-comparison',
  imports: [FormsModule, ButtonModule, ChartModule, MessageModule, SelectModule],
  templateUrl: './comparison.component.html',
  styleUrl: './comparison.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComparisonComponent {
  private readonly paretoService = inject(ParetoService);
  readonly model = input.required<ComparisonModel>();
  readonly loadAnother = output<void>();
  readonly focusedRunId = signal('');
  readonly contextKey = signal<string | null>(null);
  readonly showReference = signal(true);
  readonly showPareto = signal(true);
  readonly showTrajectory = signal(true);
  readonly visibleRunIds = signal<Set<string>>(new Set());
  readonly chartPlugins = [zoomPlugin];
  @ViewChild('comparisonChart') chart?: UIChart;
  readonly contextOptions = computed(() => [
    { label: 'All operational contexts', value: null },
    ...this.model().contexts.map((key) => ({ label: contextLabel(this.model().runs[0]?.run.contexts.find((context) => context.key === key)?.label ?? key), value: key })),
  ]);
  readonly focusedRun = computed(() => this.model().runs.find((run) => run.id === this.focusedRunId()) ?? this.model().runs[0]);
  readonly visibleRuns = computed(() => this.model().runs.filter((run) => this.visibleRunIds().has(run.id)));
  readonly resultSpaceData = computed<ChartData<'scatter', ComparisonPoint[]>>(() => {
    const model = this.model();
    const frontier = this.paretoService.frontier(model.referenceResults.filter((result) => this.matchesContext(result)));
    const frontierKeys = new Set(frontier.map((result) => this.paretoService.configurationKey(result)));
    const datasets: ChartData<'scatter', ComparisonPoint[]>['datasets'] = [];
    if (this.showReference()) {
      datasets.push({
        label: 'Exhaustive Reference',
        data: this.toPoints(model.referenceResults, 'reference', 'Exhaustive Reference', new Set()),
        backgroundColor: 'rgba(152, 162, 179, 0.18)',
        borderColor: 'rgba(102, 112, 133, 0.28)',
        pointRadius: 3,
        pointHoverRadius: 5,
        pointStyle: 'circle',
      });
    }
    if (this.showPareto() && frontier.length > 0) {
      const frontierPoints = frontier.map((result) => this.point(result, 'reference', 'Pareto Frontier (Reference)'));
      datasets.push({
        label: 'Pareto Frontier (Reference)',
        data: frontierPoints,
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
    for (const run of this.visibleRuns()) {
      const points = this.toPoints(run.run.results, run.id, run.label, frontierKeys);
      datasets.push({
        label: run.label,
        data: points,
        backgroundColor: run.color,
        borderColor: run.color,
        pointRadius: points.map((point) => point.pareto ? 7 : 5),
        pointHoverRadius: points.map((point) => point.pareto ? 9 : 7),
        pointStyle: run.pointStyle as never,
        pointBorderWidth: points.map((point) => point.pareto ? 3 : 1),
      });
    }
    const focused = this.focusedRun();
    if (focused && this.showTrajectory() && this.visibleRunIds().has(focused.id)) {
      const path = this.pathPoints(focused);
      datasets.push({
        label: `${focused.label} trajectory`,
        data: path,
        borderColor: focused.color,
        backgroundColor: focused.color,
        pointRadius: 2,
        pointHoverRadius: 5,
        pointStyle: focused.pointStyle as never,
        borderWidth: 2,
        showLine: true,
        spanGaps: false,
        tension: 0.12,
      });
    }
    return { datasets };
  });
  readonly convergenceData = computed<ChartData<'line'>>(() => {
    const datasets: ChartData<'line'>['datasets'] = this.visibleRuns().map((run) => {
      const points = convergence(run);
      return {
        label: run.label,
        data: points.map((point) => ({ x: point.x, y: point.y })),
        borderColor: run.color,
        backgroundColor: run.color,
        pointStyle: run.pointStyle as never,
        pointRadius: 4,
        pointHoverRadius: 6,
        borderWidth: 2,
        tension: 0.12,
        spanGaps: false,
      };
    });
    const maxLength = Math.max(0, ...this.model().runs.map((run) => convergence(run).length));
    if (this.model().referenceCompatible) {
      const reference = this.model().runs[0]?.evaluation.referenceBestScore;
      if (reference !== undefined) {
        datasets.push({
          label: 'Exhaustive Reference Best',
          data: Array.from({ length: maxLength }, (_, index) => ({ x: index + 1, y: reference })),
          borderColor: 'rgba(102, 112, 133, 0.45)',
          backgroundColor: 'rgba(102, 112, 133, 0.45)',
          pointRadius: 0,
          borderDash: [5, 5],
          borderWidth: 1,
          pointStyle: 'line' as never,
        });
      }
    }
    return { labels: Array.from({ length: maxLength }, (_, index) => String(index + 1)), datasets };
  });
  readonly resultSpaceOptions: ChartOptions<'scatter'> = chartOptions('Result Space');
  readonly convergenceOptions: ChartOptions<'line'> = chartOptions('Convergence');

  constructor() {
    effect(() => {
      const first = this.model().runs[0]?.id;
      if (first && !this.model().runs.some((run) => run.id === this.focusedRunId())) this.focusedRunId.set(first);
      const ids = new Set(this.model().runs.map((run) => run.id));
      if (![...this.visibleRunIds()].some((id) => ids.has(id))) this.visibleRunIds.set(ids);
    });
  }

  formatScore(value: number | undefined): string { return value === undefined ? '—' : value.toFixed(4); }
  formatPercent(value: number | undefined): string { return value === undefined ? '—' : `${(value * 100).toFixed(1)}%`; }
  formatGap(value: number | undefined): string { return value === undefined ? '—' : `${value.toFixed(4)} score points`; }
  formatRelativeGap(value: number | undefined): string { return value === undefined ? '—' : `${value.toFixed(2)}%`; }
  symbol(pointStyle: string): string {
    return ({ circle: '●', triangle: '▲', rect: '■', rectRot: '◆', star: '★', crossRot: '✚' } as Record<string, string>)[pointStyle] ?? '●';
  }
  resetZoom(): void { this.chart?.chart?.resetZoom('active'); }
  toggleRun(id: string): void {
    const visible = new Set(this.visibleRunIds());
    visible.has(id) ? visible.delete(id) : visible.add(id);
    this.visibleRunIds.set(visible);
  }
  threshold(run: ComparisonRun, target: number): string {
    return run.evaluation.qualityThresholds.find((item) => item.threshold === target)?.reachedAtDecision === undefined
      ? 'Not reached'
      : `Evaluation #${run.evaluation.qualityThresholds.find((item) => item.threshold === target)!.reachedAtDecision}`;
  }

  private toPoints(results: NormalizedResult[], runId: string, label: string, frontierKeys: Set<string>): ComparisonPoint[] {
    const run = this.model().runs.find((item) => item.id === runId);
    return results
      .filter((result) => this.matchesContext(result))
      .filter((result) => result.checkoutSuccessRate !== undefined && result.iterationDurationP95 !== undefined)
      .map((result) => ({ x: result.checkoutSuccessRate!, y: result.iterationDurationP95!, runId, label, scenario: result.scenario, pointStyle: run?.pointStyle ?? 'circle', pareto: frontierKeys.has(this.paretoService.configurationKey(result)) }));
  }

  private point(result: NormalizedResult, runId: string, label: string): ComparisonPoint {
    return { x: result.checkoutSuccessRate!, y: result.iterationDurationP95!, runId, label, scenario: result.scenario, pointStyle: 'rectRot' };
  }

  private matchesContext(result: NormalizedResult): boolean {
    const context = this.contextKey();
    return !context || contextOf(result) === context;
  }

  private pathPoints(run: ComparisonRun): ComparisonPoint[] {
    const context = this.contextKey();
    return run.run.decisions.slice().sort((left, right) => left.decision - right.decision).flatMap((decision) => {
      const result = decision.joinedResults.find((candidate) => !context || contextOf(candidate) === context && candidate.checkoutSuccessRate !== undefined && candidate.iterationDurationP95 !== undefined);
      return result?.checkoutSuccessRate === undefined || result.iterationDurationP95 === undefined
        ? []
        : [{ x: result.checkoutSuccessRate, y: result.iterationDurationP95, runId: run.id, label: run.label, scenario: `Evaluation #${decision.decision}`, evaluation: decision.decision, pointStyle: run.pointStyle }];
    });
  }
}

function convergence(run: ComparisonRun): Array<{ x: number; y: number }> {
  let best = -Infinity;
  return run.run.decisions
    .slice()
    .sort((left, right) => left.decision - right.decision)
    .filter((decision) => decision.aggregatedResult?.score !== undefined)
    .map((decision, index) => {
      best = Math.max(best, decision.aggregatedResult!.bestScoreSoFar ?? decision.aggregatedResult!.score!);
      return { x: index + 1, y: best };
    });
}

function contextOf(result: NormalizedResult): string {
  return `${result.workloadUsers ?? '-'}|${result.faultPercentage ?? '-'}`;
}

function contextLabel(value: string): string { return value; }

function chartOptions(title: string): ChartOptions<'scatter'> & ChartOptions<'line'> {
  return {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    parsing: false,
    interaction: { mode: 'nearest', intersect: false },
    plugins: {
      legend: { display: true, position: 'bottom', labels: { usePointStyle: true, boxWidth: 9 } },
      title: { display: false, text: title },
      zoom: { pan: { enabled: true, mode: 'xy' }, zoom: { wheel: { enabled: true, modifierKey: 'ctrl' }, pinch: { enabled: true }, mode: 'xy' } },
    },
    scales: {
      x: { title: { display: true, text: title === 'Convergence' ? 'Evaluation' : 'Checkout Success Rate' }, grid: { color: '#eaecf0' } },
      y: { title: { display: true, text: title === 'Convergence' ? 'Best Score So Far' : 'p95 Iteration Duration (ms)' }, grid: { color: '#eaecf0' } },
    },
  } as ChartOptions<'scatter'> & ChartOptions<'line'>;
}
