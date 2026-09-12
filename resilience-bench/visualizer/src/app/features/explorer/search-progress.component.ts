import { TooltipModule } from 'primeng/tooltip';
import { metricHelp } from '../../core/models/metric-help';
import { ChangeDetectionStrategy, Component, computed, inject, ViewChild } from '@angular/core';
import { ChartData, ChartOptions } from 'chart.js';
import { RunEvaluationService } from '../../core/services/run-evaluation.service';
import { ChartModule, UIChart } from 'primeng/chart';
import { downloadUrl } from '../../core/services/download';
import { ExplorerStateService, isInitialDecision } from '../../core/services/explorer-state.service';

interface ChartSelectEvent {
  element: unknown;
}

@Component({
  selector: 'app-search-progress',
  imports: [TooltipModule, ChartModule],
  template: `
    <section class="chart-panel">
      <div class="panel-heading">
        <div>
          <h2 class="section-heading"><span class="term-help" tabindex="0" [pTooltip]="metricHelp.convergence" tooltipEvent="both" tooltipPosition="top" [autoHide]="false">Search Progress</span></h2>
          <p>How quickly did the search improve? The step line shows the best result available after each evaluation.</p>
        </div>
        <div class="phase-summary">
          @if (initialRange(); as range) { <span><i class="initial"></i>{{ state.run()?.strategy === 'randomSampling' ? 'Random sample' : 'Initial Sample' }} {{ range }}</span> }
          @if (adaptiveRange(); as range) { <span><i class="adaptive"></i>Adaptive Search {{ range }}</span> }
        </div>

      </div>
      @if (hasScores()) {
        <div class="chart-frame compact">
          <p-chart #progressChart type="line" [data]="data()" [options]="options" height="100%" ariaLabel="Search Progress" (onDataSelect)="selectPoint($event)" />
        </div>
        <button class="rb-button" (click)="exportChart()">Export chart (PNG)</button>
      } @else {
        <div class="empty-state">The trace does not contain evaluated scores.</div>
      }
      <details class="score-table">
        <summary>View evaluation scores</summary>
        <div class="table-scroll" tabindex="0" role="region" aria-label="Evaluation scores">
          <table>
            <caption>One row per evaluated configuration. Select a decision to inspect it.</caption>
            <thead><tr><th scope="col">Decision</th><th scope="col"><span class="term-help" tabindex="0" [pTooltip]="metricHelp.observedScore" tooltipEvent="both" tooltipPosition="top" [autoHide]="false">Observed score</span></th><th scope="col"><span class="term-help" tabindex="0" [pTooltip]="metricHelp.bestSoFar" tooltipEvent="both" tooltipPosition="top" [autoHide]="false">Best so far</span></th><th scope="col">Result</th></tr></thead>
            <tbody>@for (decision of decisions(); track decision.decision; let index = $index) {
              <tr [class.active-row]="decision.decision === state.selectedDecisionNumber()">
                <th scope="row"><button class="rb-button" [attr.aria-pressed]="decision.decision === state.selectedDecisionNumber()" (click)="state.selectDecision(decision.decision)">Decision {{ decision.decision }}</button></th>
                <td>{{ formatScore(decision.aggregatedResult?.score) }}</td><td>{{ formatScore(bestScores()[index]) }}</td>
                <td>{{ index === 0 || bestScores()[index] > bestScores()[index - 1] ? 'New best' : 'No improvement' }}</td>
              </tr>
            }</tbody>
          </table>
        </div>
      </details>
    </section>
  `,
  styleUrl: './chart-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchProgressComponent {
  readonly metricHelp = metricHelp;
  @ViewChild('progressChart') chart?: UIChart;
  exportChart(): void { const url = this.chart?.chart?.toBase64Image(); if (url) downloadUrl('search-progress.png', url); }
  readonly formatScore = formatScore;
  readonly state = inject(ExplorerStateService);
  private readonly evaluator = inject(RunEvaluationService);
  readonly decisions = computed(() => this.state.visibleDecisions().filter((d) => d.aggregatedResult?.score !== undefined).slice().sort((a, b) => a.decision - b.decision));
  readonly bestScores = computed(() => {
    let best = -Infinity;
    return this.decisions().map((d) => best = Math.max(best, d.aggregatedResult!.score!));
  });

  readonly initialRange = computed(() => rangeLabel(this.decisions().filter(isInitial)));
  readonly adaptiveRange = computed(() => rangeLabel(this.decisions().filter((decision) => !isInitial(decision))));

  readonly data = computed<ChartData<'line'>>(() => {
    const decisions = this.decisions();
    const selected = this.state.selectedDecisionNumber();
    const labels = decisions.map((decision) => String(decision.decision));
    const score = (decisionIndex: number) => {
      const result = decisions[decisionIndex].aggregatedResult;
      return result?.score ?? null;
    };

    const datasets: ChartData<'line'>['datasets'] = [
      {
        label: 'Observed Score',
        data: decisions.map((_, index) => score(index)),
        borderColor: '#667085',
        backgroundColor: '#667085',
        borderWidth: 1,
        pointRadius: 3,
        pointHoverRadius: 5,
        tension: 0.1,
        showLine: false,
        spanGaps: true,
      },
    ];

    if (decisions.length) {
      datasets.push({
        label: 'Best Score So Far',
        data: this.bestScores(),
        borderColor: '#c2410c',
        backgroundColor: '#c2410c',
        borderWidth: 2,
        pointRadius: decisions.map((decision) => decision.aggregatedResult?.improvedBest ? 3 : 0),
        pointHoverRadius: 5,
        stepped: 'after',
        spanGaps: true,
      });
    }

    const run = this.state.run();
    const evaluation = run ? this.evaluator.evaluate(run) : undefined;
    if (evaluation?.referenceCompatible) datasets.push({ label: 'Exhaustive Reference Best', data: decisions.map(() => evaluation.referenceBestScore!), borderColor: '#98a2b3', borderDash: [5, 5], pointRadius: 0 });

    datasets.push({
      label: 'Selected Decision',
      data: decisions.map((decision, index) => (decision.decision === selected ? score(index) : null)),
      borderColor: '#7c2d12',
      backgroundColor: '#fff7ed',
      pointBorderColor: '#7c2d12',
      pointBorderWidth: 4,
      pointRadius: 9,
      showLine: false,
    });

    return { labels, datasets };
  });

  readonly hasScores = computed(() =>
    this.decisions().some(
      (decision) => decision.aggregatedResult?.score !== undefined || decision.aggregatedResult?.currentScore !== undefined,
    ),
  );

  readonly options: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    interaction: { mode: 'nearest', intersect: true },
    plugins: {
      legend: { display: true, position: 'bottom', labels: { usePointStyle: true, boxWidth: 8 } },
      tooltip: {
        callbacks: {
          title: (items) => (items.length ? `Decision #${items[0].label}` : ''),
          label: () => '',
          afterBody: (items) => {
            const first = items[0];
            const decision = first ? this.decisions()[first.dataIndex] : undefined;
            if (!decision) {
              return [];
            }
            const observed = decision.aggregatedResult?.score;
            return [
              this.state.run()?.strategy === 'randomSampling' ? 'Random sample' : isInitialDecision(decision) ? 'Initial Sample' : 'Adaptive Search',
              `Observed Score: ${formatScore(observed)}`,
              `Best Score So Far: ${formatScore(this.bestScores()[first!.dataIndex])}`,
              `New Best: ${decision.aggregatedResult?.improvedBest ? 'Yes' : 'No'}`,
            ];
          },
        },
      },
    },
    scales: {
      x: { title: { display: true, text: 'Evaluated configurations (decision number)' }, grid: { display: false } },
      y: { title: { display: true, text: 'Score — higher is better' }, grid: { color: '#eaecf0' } },
    },
  };

  selectPoint(event: ChartSelectEvent): void {
    const element = event.element as { index?: number } | undefined;
    const decision = element?.index === undefined ? undefined : this.decisions()[element.index];
    if (decision) {
      this.state.selectDecision(decision.decision);
    }
  }
}

function isInitial(decision: { phase?: string; selectionMode?: string }): boolean {
  return isInitialDecision(decision);
}

function rangeLabel(decisions: Array<{ decision: number }>): string | undefined {
  if (!decisions.length) {
    return undefined;
  }
  const first = decisions[0].decision;
  const last = decisions[decisions.length - 1].decision;
  return first === last ? `#${first}` : `#${first}-${last}`;
}

function formatScore(value: number | null | undefined): string {
  return value !== null && value !== undefined && Number.isFinite(value)
    ? new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 }).format(value)
    : '-';
}
