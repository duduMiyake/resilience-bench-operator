import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
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

  readonly workloadOptions = computed<FilterOption[]>(() => [
    { label: 'Todos os workloads', value: null },
    ...distinctNumbers(this.run().contexts.map((context) => context.workloadUsers)).map(
      (value) => ({ label: `${value} VUs`, value }),
    ),
  ]);

  readonly faultOptions = computed<FilterOption[]>(() => [
    { label: 'Todas as falhas', value: null },
    ...distinctNumbers(this.run().contexts.map((context) => context.faultPercentage)).map(
      (value) => ({ label: `${value}% falha`, value }),
    ),
  ]);

  readonly showWorkloadFilter = computed(() => this.workloadOptions().length > 2);
  readonly showFaultFilter = computed(() => this.faultOptions().length > 2);

  strategyLabel(): string {
    return this.run().kind === 'legacy-exhaustive' ? 'Exhaustive legado' : this.run().strategy;
  }
}

function distinctNumbers(values: Array<number | undefined>): number[] {
  return [...new Set(values.filter((value): value is number => value !== undefined))].sort(
    (a, b) => a - b,
  );
}
