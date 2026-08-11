import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ChartData, ChartOptions } from 'chart.js';
import { ChartModule } from 'primeng/chart';
import { ExplorerStateService, isInitialDecision } from '../../core/services/explorer-state.service';

interface ChartSelectEvent {
  element: unknown;
}

@Component({
  selector: 'app-search-progress',
  imports: [ChartModule],
  template: `
    <section class="chart-panel">
      <div class="panel-heading">
        <div>
          <h2 class="section-heading">Search Progress</h2>
          <p>Observed Score for each decision and Best Score So Far after that evaluation.</p>
        </div>
        <div class="phase-summary">
          @if (initialRange(); as range) { <span><i class="initial"></i>Initial Sample {{ range }}</span> }
          @if (adaptiveRange(); as range) { <span><i class="adaptive"></i>Adaptive Search {{ range }}</span> }
        </div>
      </div>
      @if (hasScores()) {
        <div class="chart-frame compact">
          <p-chart type="line" [data]="data()" [options]="options" height="100%" ariaLabel="Search Progress" (onDataSelect)="selectPoint($event)" />
        </div>
      } @else {
        <div class="empty-state">The trace does not contain evaluated scores.</div>
      }
    </section>
  `,
  styleUrl: './chart-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchProgressComponent {
  readonly state = inject(ExplorerStateService);
  readonly decisions = computed(() => this.state.visibleDecisions());

  readonly initialRange = computed(() => rangeLabel(this.decisions().filter(isInitial)));
  readonly adaptiveRange = computed(() => rangeLabel(this.decisions().filter((decision) => !isInitial(decision))));

  readonly data = computed<ChartData<'line'>>(() => {
    const decisions = this.decisions();
    const selected = this.state.selectedDecisionNumber();
    const labels = decisions.map((decision) => String(decision.decision));
    const score = (decisionIndex: number) => {
      const result = decisions[decisionIndex].aggregatedResult;
      return result?.currentScore ?? result?.score ?? null;
    };

    const datasets: ChartData<'line'>['datasets'] = [
      {
        label: 'Observed Score - Initial Sample',
        data: decisions.map((decision, index) => (isInitial(decision) ? score(index) : null)),
        borderColor: '#2563a5',
        backgroundColor: '#2563a5',
        pointRadius: 4,
        showLine: false,
      },
      {
        label: 'Observed Score - Adaptive Search',
        data: decisions.map((decision, index) => (!isInitial(decision) ? score(index) : null)),
        borderColor: '#087f5b',
        backgroundColor: '#087f5b',
        pointRadius: 4,
        showLine: false,
      },
    ];

    if (decisions.some((decision) => decision.aggregatedResult?.bestScoreSoFar !== undefined)) {
      datasets.push({
        label: 'Best Score So Far',
        data: decisions.map((decision) => decision.aggregatedResult?.bestScoreSoFar ?? null),
        borderColor: '#c2410c',
        backgroundColor: '#c2410c',
        borderWidth: 2,
        pointRadius: 0,
        tension: 0.15,
        spanGaps: true,
      });
    }

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
            const observed = decision.aggregatedResult?.currentScore ?? decision.aggregatedResult?.score;
            return [
              isInitialDecision(decision) ? 'Initial Sample' : 'Adaptive Search',
              `Observed Score: ${formatScore(observed)}`,
              `Best Score So Far: ${formatScore(decision.aggregatedResult?.bestScoreSoFar)}`,
              `New Best: ${decision.aggregatedResult?.improvedBest ? 'Yes' : 'No'}`,
            ];
          },
        },
      },
    },
    scales: {
      x: { title: { display: true, text: 'Decision' }, grid: { display: false } },
      y: { title: { display: true, text: 'Score' }, grid: { color: '#eaecf0' } },
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
