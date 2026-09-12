import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ExplorerStateService } from '../../core/services/explorer-state.service';

@Component({
  selector: 'app-decision-timeline',
  template: `
    <section class="timeline-panel" aria-label="Decision navigation">
      <button class="rb-button" (click)="state.selectPrevious()" [disabled]="state.selectedDecisionIndex() <= 0">Previous</button>
      <button class="rb-button" (click)="state.togglePlayback()" [disabled]="state.visibleDecisions().length < 2">{{ state.isPlaying() ? 'Pause' : 'Play' }}</button>
      <label>Decision
        <select [value]="state.selectedDecisionNumber()" (change)="state.selectDecision(+$any($event.target).value)">
          @for (decision of state.visibleDecisions(); track decision.decision) {
            <option [value]="decision.decision">{{ decision.decision }}{{ decision.aggregatedResult?.improvedBest ? ' · New best' : '' }}</option>
          }
        </select>
        / {{ state.visibleDecisions().length }}
      </label>
      <button class="rb-button" (click)="state.selectNext()" [disabled]="state.selectedDecisionIndex() >= state.visibleDecisions().length - 1">Next</button>
      <button class="rb-button" (click)="nextImprovement()" [disabled]="!nextBest()">Next improvement</button>
    </section>
  `,
  styles: `
    .timeline-panel { display: flex; flex-wrap: wrap; align-items: center; gap: .75rem; padding: 1rem; background: white; border-radius: 6px; }
    button, select { padding: .55rem .75rem; background: white; border: 1px solid var(--rb-border); border-radius: 5px; font: inherit; }
    button { cursor: pointer; } button:disabled { opacity: .5; cursor: default; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecisionTimelineComponent {
  readonly state = inject(ExplorerStateService);
  nextBest() { return this.state.visibleDecisions().slice(this.state.selectedDecisionIndex() + 1).find(d => d.aggregatedResult?.improvedBest); }
  nextImprovement(): void { const decision = this.nextBest(); if (decision) this.state.selectDecision(decision.decision); }
}
