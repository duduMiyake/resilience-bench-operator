import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { ExplorerStateService } from '../../core/services/explorer-state.service';

@Component({
  selector: 'app-decision-timeline',
  imports: [ButtonModule],
  template: `
    <section class="timeline-panel">
      <div class="timeline-heading">
        <div>
          <h2 class="section-heading">Decision Timeline</h2>
          <p class="muted">{{ state.visibleDecisions().length }} decis&otilde;es neste contexto</p>
        </div>
        <div class="timeline-nav">
          <p-button
            icon="pi pi-chevron-left"
            severity="secondary"
            [text]="true"
            ariaLabel="Decis&atilde;o anterior"
            (onClick)="state.selectPrevious()"
          />
          <p-button
            icon="pi pi-chevron-right"
            severity="secondary"
            [text]="true"
            ariaLabel="Pr&oacute;xima decis&atilde;o"
            (onClick)="state.selectNext()"
          />
        </div>
      </div>

      @if (state.visibleDecisions().length) {
        <div class="timeline-track" role="list" aria-label="Decis&otilde;es">
          @for (decision of state.visibleDecisions(); track decision.decision) {
            <button
              type="button"
              class="decision-node"
              [class.initial]="decision.selectionMode === 'INITIAL_BATCH'"
              [class.adaptive]="decision.selectionMode !== 'INITIAL_BATCH'"
              [class.selected]="decision.decision === state.selectedDecisionNumber()"
              [class.improved]="decision.aggregatedResult?.improvedBest"
              [attr.aria-current]="
                decision.decision === state.selectedDecisionNumber() ? 'step' : null
              "
              [title]="decision.phase"
              (click)="state.selectDecision(decision.decision)"
            >
              {{ decision.decision }}
            </button>
          }
        </div>
        <div class="timeline-legend">
          <span><i class="initial-dot"></i>Inicial</span>
          <span><i class="adaptive-dot"></i>Adaptativa</span>
          <span><i class="improved-dot"></i>Novo melhor</span>
        </div>
      } @else {
        <div class="empty-state">
          N&atilde;o h&aacute; decis&otilde;es heur&iacute;sticas para esta rodada.
        </div>
      }
    </section>
  `,
  styles: `
    .timeline-panel {
      padding: 1rem;
      border: 1px solid var(--rb-border);
      border-radius: 6px;
      background: var(--rb-surface);
    }

    .timeline-heading {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
    }

    .timeline-heading p {
      margin: 0.2rem 0 0;
      font-size: 0.78rem;
    }

    .timeline-nav {
      display: flex;
    }

    .timeline-track {
      display: flex;
      min-height: 58px;
      align-items: center;
      gap: 0.45rem;
      margin-top: 0.5rem;
      padding: 0.5rem 0.1rem;
      overflow-x: auto;
    }

    .decision-node {
      position: relative;
      display: grid;
      width: 34px;
      height: 34px;
      flex: 0 0 34px;
      place-items: center;
      border: 2px solid transparent;
      border-radius: 50%;
      color: white;
      cursor: pointer;
      font-size: 0.75rem;
      font-weight: 700;
    }

    .decision-node.initial {
      background: #2563a5;
    }

    .decision-node.adaptive {
      background: #087f5b;
    }

    .decision-node.improved::after {
      position: absolute;
      top: -5px;
      right: -3px;
      width: 9px;
      height: 9px;
      border: 2px solid white;
      border-radius: 50%;
      background: #c2410c;
      content: '';
    }

    .decision-node.selected {
      outline: 3px solid #fed7aa;
      outline-offset: 2px;
    }

    .timeline-legend {
      display: flex;
      flex-wrap: wrap;
      gap: 1rem;
      margin-top: 0.4rem;
      color: var(--rb-muted);
      font-size: 0.75rem;
    }

    .timeline-legend span {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
    }

    .timeline-legend i {
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }

    .initial-dot {
      background: #2563a5;
    }
    .adaptive-dot {
      background: #087f5b;
    }
    .improved-dot {
      background: #c2410c;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecisionTimelineComponent {
  readonly state = inject(ExplorerStateService);
}
