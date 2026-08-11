import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { NormalizedRun } from '../../core/models/visualizer.models';
import { ExplorerStateService } from '../../core/services/explorer-state.service';
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
  readonly showHelp = signal(false);

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
}

function distinctNumbers(values: Array<number | undefined>): number[] {
  return [...new Set(values.filter((value): value is number => value !== undefined))].sort(
    (a, b) => a - b,
  );
}
