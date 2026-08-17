import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { NormalizedRun } from '../../core/models/visualizer.models';
import { ExplorerStateService } from '../../core/services/explorer-state.service';
import { RunEvaluationService } from '../../core/services/run-evaluation.service';
import { DecisionDetailsComponent } from './decision-details.component';
import { DecisionTimelineComponent } from './decision-timeline.component';
import { LegacyResultsComponent } from './legacy-results.component';
import { ResultSpaceComponent } from './result-space.component';
import { SearchProgressComponent } from './search-progress.component';
import { SummaryCardsComponent } from './summary-cards.component';

interface FilterOption {
  label: string;
  value: number | null;
}

@Component({
  selector: 'app-explorer',
  imports: [
    FormsModule,
    ButtonModule,
    MessageModule,
    SelectModule,
    TagModule,
    TooltipModule,
    SummaryCardsComponent,
    ResultSpaceComponent,
    SearchProgressComponent,
    DecisionTimelineComponent,
    DecisionDetailsComponent,
    LegacyResultsComponent,
  ],
  templateUrl: './explorer.component.html',
  styleUrl: './explorer.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExplorerComponent {
  readonly run = input.required<NormalizedRun>();
  readonly loadAnother = output<void>();
  readonly state = inject(ExplorerStateService);
  private readonly evaluationService = inject(RunEvaluationService);
  readonly showHelp = signal(false);
  readonly evaluation = computed(() => this.evaluationService.evaluate(this.run()));

  readonly workloadValues = computed(() => distinctNumbers(this.run().contexts.map((context) => context.workloadUsers)));
  readonly faultValues = computed(() => distinctNumbers(this.run().contexts.map((context) => context.faultPercentage)));

  readonly workloadOptions = computed<FilterOption[]>(() => [
    { label: 'All workloads', value: null },
    ...this.workloadValues().map((value) => ({ label: `${value} users`, value })),
  ]);

  readonly faultOptions = computed<FilterOption[]>(() => [
    { label: 'All fault rates', value: null },
    ...this.faultValues().map((value) => ({ label: `${value}% fault`, value })),
  ]);

  readonly showWorkloadFilter = computed(() => this.workloadValues().length > 1);
  readonly showFaultFilter = computed(() => this.faultValues().length > 1);

  readonly compactContext = computed(() => {
    if (this.showWorkloadFilter() || this.showFaultFilter()) {
      return undefined;
    }
    const users = this.workloadValues()[0];
    const fault = this.faultValues()[0];
    if (users === undefined && fault === undefined) {
      return 'Operational Context: unavailable';
    }
    return `Operational Context: ${users ?? '-'} users - ${fault ?? '-'}% fault`;
  });

  strategyLabel(): string {
    return this.run().kind === 'legacy-exhaustive' ? 'Legacy Exhaustive' : this.run().strategy;
  }

  formatScore(score: number): string {
    return new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 }).format(score);
  }

  formatOutcomeMetric(value: number | undefined): string {
    return value === undefined ? '—' : value.toFixed(4);
  }

  formatOutcomePercent(ratio: number | undefined): string {
    return ratio === undefined ? '—' : `${(ratio * 100).toFixed(1)}%`;
  }

  formatRelativeGap(value: number | undefined): string {
    return value === undefined ? '—' : `${value.toFixed(2)}%`;
  }

  outcomeSummary(): string {
    const result = this.evaluation();
    const best = this.formatOutcomeMetric(result.bestObservedScore);
    const decision = result.bestFoundAtDecision === undefined ? 'an unknown decision' : `Decision #${result.bestFoundAtDecision}`;
    if (!result.referenceCompatible || result.relativeGap === undefined || result.referenceBestScore === undefined) {
      return `Best heuristic result: ${best}, found at ${decision}.`;
    }
    const comparison = result.relativeGap > 0
      ? `${this.formatRelativeGap(result.relativeGap)} below the exhaustive reference best (${this.formatOutcomeMetric(result.referenceBestScore)})`
      : result.relativeGap === 0
        ? `matches the exhaustive reference best (${this.formatOutcomeMetric(result.referenceBestScore)})`
        : `${this.formatRelativeGap(Math.abs(result.relativeGap))} above the exhaustive reference best (${this.formatOutcomeMetric(result.referenceBestScore)})`;
    return `Best heuristic result: ${best}, found at ${decision}, ${comparison}.`;
  }

  formatPercent(ratio: number | undefined): string {
    return ratio === undefined ? '—' : `${(ratio * 100).toFixed(1)}%`;
  }

  formatMetric(value: number | undefined): string {
    return value === undefined ? '—' : this.formatScore(value);
  }

  formatContextSuccessRate(value: number | undefined): string {
    return value === undefined ? '—' : `${(value * 100).toFixed(2)}%`;
  }

  formatContextDuration(value: number | undefined): string {
    return value === undefined ? '—' : `${(value / 1000).toFixed(2)} s`;
  }

  formatSuccessGap(heuristic: number | undefined, reference: number | undefined): string {
    if (heuristic === undefined || reference === undefined) return '—';
    const gap = (heuristic - reference) * 100;
    if (Math.abs(gap) < 1e-9) return 'Matches reference';
    return `${Math.abs(gap).toFixed(2)} pp ${gap < 0 ? 'below' : 'above'} reference`;
  }

  formatLatencyGap(heuristic: number | undefined, reference: number | undefined): string {
    if (heuristic === undefined || reference === undefined) return '—';
    const gap = (heuristic - reference) / 1000;
    if (Math.abs(gap) < 1e-9) return 'Matches reference';
    return `${Math.abs(gap).toFixed(2)} s ${gap > 0 ? 'slower' : 'faster'}`;
  }
}

function distinctNumbers(values: Array<number | undefined>): number[] {
  return [...new Set(values.filter((value): value is number => value !== undefined))].sort(
    (a, b) => a - b,
  );
}
