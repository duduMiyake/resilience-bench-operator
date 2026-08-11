import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
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
          <p class="muted">{{ state.visibleDecisions().length }} decisions in this run</p>
        </div>
        <div class="timeline-nav">
          <p-button icon="pi pi-step-backward" severity="secondary" [text]="true" ariaLabel="Previous decision" (onClick)="state.selectPrevious()" />
          <p-button [icon]="state.isPlaying() ? 'pi pi-pause' : 'pi pi-play'" severity="secondary" [text]="true" [ariaLabel]="state.isPlaying() ? 'Pause playback' : 'Play decisions'" (onClick)="state.togglePlayback()" />
          <p-button icon="pi pi-step-forward" severity="secondary" [text]="true" ariaLabel="Next decision" (onClick)="state.selectNext()" />
          <span class="decision-count">Decision {{ selectedPosition() }} / {{ state.visibleDecisions().length }}</span>
        </div>
      </div>

      @if (state.visibleDecisions().length) {
        <div class="phase-labels">
          @if (initialRange(); as range) { <span title="These configurations provide the first observations used by the adaptive heuristic.">INITIAL SAMPLE - Decisions {{ range }}</span> }
          @if (adaptiveRange(); as range) { <span title="These configurations are selected using information learned from previously evaluated configurations.">ADAPTIVE SEARCH - Decisions {{ range }}</span> }
        </div>
        <div class="timeline-track" role="list" aria-label="Decisions">
          @for (decision of state.visibleDecisions(); track decision.decision) {
            <button
              type="button"
              class="decision-node"
              [class.initial]="isInitial(decision)"
              [class.adaptive]="!isInitial(decision)"
              [class.selected]="decision.decision === state.selectedDecisionNumber()"
              [class.improved]="decision.aggregatedResult?.improvedBest"
              [attr.aria-current]="decision.decision === state.selectedDecisionNumber() ? 'step' : null"
              [title]="tooltip(decision)"
              (click)="state.selectDecision(decision.decision)"
            >
              {{ decision.decision }}
            </button>
          }
        </div>
        <div class="timeline-legend">
          <span><i class="initial-dot"></i>Initial Sample</span>
          <span><i class="adaptive-dot"></i>Adaptive Search</span>
          <span><i class="improved-dot"></i>New Best</span>
          <span><i class="selected-dot"></i>Currently Selected</span>
        </div>
      } @else {
        <div class="empty-state">No heuristic decisions are available for this run.</div>
      }
    </section>
  `,
  styles: `
    .timeline-panel { padding: 1rem; border: 1px solid var(--rb-border); border-radius: 6px; background: var(--rb-surface); }
    .timeline-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
    .timeline-heading p { margin: 0.2rem 0 0; font-size: 0.78rem; }
    .timeline-nav { display: flex; align-items: center; gap: 0.15rem; }
    .decision-count { margin-left: 0.35rem; color: var(--rb-muted); font-size: 0.78rem; white-space: nowrap; }
    .phase-labels { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 0.75rem; color: var(--rb-muted); font-size: 0.72rem; font-weight: 750; }
    .phase-labels span { padding: 0.25rem 0.45rem; border: 1px solid var(--rb-border); border-radius: 4px; background: #f8fafb; }
    .timeline-track { display: flex; min-height: 58px; align-items: center; gap: 0.45rem; margin-top: 0.5rem; padding: 0.5rem 0.1rem; overflow-x: auto; }
    .decision-node { position: relative; display: grid; width: 34px; height: 34px; flex: 0 0 34px; place-items: center; border: 2px solid transparent; border-radius: 50%; color: white; cursor: pointer; font-size: 0.75rem; font-weight: 700; }
    .decision-node.initial { background: #2563a5; }
    .decision-node.adaptive { background: #087f5b; }
    .decision-node.improved::after { position: absolute; top: -5px; right: -3px; width: 9px; height: 9px; border: 2px solid white; border-radius: 50%; background: #f59e0b; content: ''; }
    .decision-node.selected { outline: 3px solid #fed7aa; outline-offset: 2px; }
    .timeline-legend { display: flex; flex-wrap: wrap; gap: 1rem; margin-top: 0.4rem; color: var(--rb-muted); font-size: 0.75rem; }
    .timeline-legend span { display: inline-flex; align-items: center; gap: 0.35rem; }
    .timeline-legend i { width: 8px; height: 8px; border-radius: 50%; }
    .initial-dot { background: #2563a5; } .adaptive-dot { background: #087f5b; } .improved-dot { background: #f59e0b; } .selected-dot { background: #c2410c; }
    @media (max-width: 700px) { .timeline-heading { flex-direction: column; } .timeline-nav { flex-wrap: wrap; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DecisionTimelineComponent {
  readonly state = inject(ExplorerStateService);
  readonly initialRange = computed(() => rangeLabel(this.state.visibleDecisions().filter((decision) => this.isInitial(decision))));
  readonly adaptiveRange = computed(() => rangeLabel(this.state.visibleDecisions().filter((decision) => !this.isInitial(decision))));
  readonly selectedPosition = computed(() => Math.max(0, this.state.selectedDecisionIndex() + 1));

  isInitial(decision: { phase?: string; selectionMode?: string }): boolean {
    return decision.selectionMode === 'INITIAL_BATCH' || decision.phase === 'initialSample' || decision.phase === 'initialSelection';
  }

  tooltip(decision: { decision: number; phase?: string; selectionMode?: string; aggregatedResult?: { currentScore?: number; score?: number; improvedBest?: boolean } }): string {
    const phase = this.isInitial(decision) ? 'Initial Sample' : 'Adaptive Search';
    const score = decision.aggregatedResult?.currentScore ?? decision.aggregatedResult?.score;
    return [`Decision #${decision.decision}`, phase, `Observed Score: ${formatScore(score)}`, `New Best: ${decision.aggregatedResult?.improvedBest ? 'Yes' : 'No'}`].join('\n');
  }
}

function rangeLabel(decisions: Array<{ decision: number }>): string | undefined {
  if (!decisions.length) return undefined;
  const first = decisions[0].decision;
  const last = decisions[decisions.length - 1].decision;
  return first === last ? `${first}` : `${first}-${last}`;
}

function formatScore(value?: number): string {
  return value === undefined ? '-' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 4 }).format(value);
}
