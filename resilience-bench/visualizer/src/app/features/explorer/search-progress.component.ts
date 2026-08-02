import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ChartData, ChartOptions } from 'chart.js';
import { ChartModule } from 'primeng/chart';
import { ExplorerStateService } from '../../core/services/explorer-state.service';

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
          <p>Score observado por decis&atilde;o e melhor score informado pelo trace.</p>
        </div>
        <div class="phase-summary">
          <span><i class="initial"></i>Inicial</span>
          <span><i class="adaptive"></i>Adaptativa</span>
        </div>
      </div>
      @if (hasScores()) {
        <div class="chart-frame compact">
          <p-chart
            type="line"
            [data]="data()"
            [options]="options"
            height="100%"
            ariaLabel="Progresso da busca"
            (onDataSelect)="selectPoint($event)"
          />
        </div>
      } @else {
        <div class="empty-state">O trace n&atilde;o cont&eacute;m scores avaliados.</div>
      }
    </section>
  `,
  styleUrl: './chart-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchProgressComponent {
  readonly state = inject(ExplorerStateService);

  readonly decisions = computed(() => this.state.visibleDecisions());

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
        label: 'Amostra inicial',
        data: decisions.map((decision, index) =>
          decision.selectionMode === 'INITIAL_BATCH' ? score(index) : null,
        ),
        borderColor: '#2563a5',
        backgroundColor: '#2563a5',
        pointRadius: 4,
        showLine: false,
      },
      {
        label: 'Sele\\u00e7\\u00e3o adaptativa',
        data: decisions.map((decision, index) =>
          decision.selectionMode !== 'INITIAL_BATCH' ? score(index) : null,
        ),
        borderColor: '#087f5b',
        backgroundColor: '#087f5b',
        pointRadius: 4,
        showLine: false,
      },
    ];

    if (decisions.some((decision) => decision.aggregatedResult?.bestScoreSoFar !== undefined)) {
      datasets.push({
        label: 'Melhor score',
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
      label: 'Decis\\u00e3o selecionada',
      data: decisions.map((decision, index) =>
        decision.decision === selected ? score(index) : null,
      ),
      borderColor: '#7c2d12',
      backgroundColor: '#fff7ed',
      pointBorderWidth: 3,
      pointRadius: 7,
      showLine: false,
    });

    return { labels, datasets };
  });

  readonly hasScores = computed(() =>
    this.decisions().some(
      (decision) =>
        decision.aggregatedResult?.score !== undefined ||
        decision.aggregatedResult?.currentScore !== undefined,
    ),
  );

  readonly options: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    interaction: { mode: 'nearest', intersect: true },
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: { usePointStyle: true, boxWidth: 8 },
      },
      tooltip: {
        callbacks: {
          title: (items) => (items.length ? `Decis\\u00e3o ${items[0].label}` : ''),
        },
      },
    },
    scales: {
      x: {
        title: { display: true, text: 'Decis\\u00e3o' },
        grid: { display: false },
      },
      y: {
        title: { display: true, text: 'Score' },
        grid: { color: '#eaecf0' },
      },
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
