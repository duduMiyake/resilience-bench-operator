import { computed, Injectable, signal } from '@angular/core';
import { NormalizedDecision, NormalizedResult, NormalizedRun } from '../models/visualizer.models';

export interface BestFoundSummary {
  decision: number;
  score: number;
}

export interface SearchPathPoint {
  decision: number;
  x: number;
  y: number;
  phase: 'initial' | 'adaptive';
  observedScore?: number;
  improvedBest?: boolean;
}

@Injectable({ providedIn: 'root' })
export class ExplorerStateService {
  readonly run = signal<NormalizedRun | null>(null);
  readonly selectedDecisionNumber = signal<number | null>(null);
  readonly workloadUsers = signal<number | null>(null);
  readonly faultPercentage = signal<number | null>(null);
  readonly isPlaying = signal(false);
  readonly showSearchPath = signal(true);

  private playbackTimer: ReturnType<typeof setInterval> | null = null;

  readonly selectedDecision = computed<NormalizedDecision | undefined>(() => {
    const decisionNumber = this.selectedDecisionNumber();
    return this.run()?.decisions.find((decision) => decision.decision === decisionNumber);
  });

  readonly visibleResults = computed(() => {
    const run = this.run();
    return run ? run.results.filter((result) => this.matchesResult(result)) : [];
  });

  readonly visibleReferenceResults = computed(() => {
    const run = this.run();
    return run ? run.referenceResults.filter((result) => this.matchesResult(result)) : [];
  });

  readonly visibleDecisions = computed(() => this.run()?.decisions ?? []);

  readonly selectedDecisionIndex = computed(() => {
    const selected = this.selectedDecisionNumber();
    return this.visibleDecisions().findIndex((decision) => decision.decision === selected);
  });

  readonly bestFound = computed<BestFoundSummary | undefined>(() => {
    const decisions = this.visibleDecisions();
    let best: BestFoundSummary | undefined;
    for (const decision of decisions) {
      const observed = decision.aggregatedResult?.score;
      if (observed === undefined) {
        continue;
      }
      if (!best || observed > best.score) {
        best = { decision: decision.decision, score: observed };
      }
    }
    return best;
  });

  readonly searchPath = computed<SearchPathPoint[]>(() => {
    const resultByDecision = new Map<number, NormalizedResult>();
    for (const result of this.visibleResults()) {
      if (
        result.joinedDecision !== undefined &&
        result.checkoutSuccessRate !== undefined &&
        result.iterationDurationP95 !== undefined &&
        !resultByDecision.has(result.joinedDecision)
      ) {
        resultByDecision.set(result.joinedDecision, result);
      }
    }

    return this.visibleDecisions()
      .slice()
      .sort((left, right) => left.decision - right.decision)
      .flatMap((decision) => {
        const result = resultByDecision.get(decision.decision);
        if (!result) {
          return [];
        }
        return [
          {
            decision: decision.decision,
            x: result.checkoutSuccessRate!,
            y: result.iterationDurationP95!,
            phase: isInitialDecision(decision) ? 'initial' : 'adaptive',
            observedScore: observedScore(decision),
            improvedBest: decision.aggregatedResult?.improvedBest,
          },
        ];
      });
  });

  setRun(run: NormalizedRun): void {
    this.stopPlayback();
    this.run.set(run);
    this.workloadUsers.set(null);
    this.faultPercentage.set(null);
    this.showSearchPath.set(true);
    this.selectedDecisionNumber.set(run.decisions[0]?.decision ?? null);
  }

  clear(): void {
    this.stopPlayback();
    this.run.set(null);
    this.selectedDecisionNumber.set(null);
    this.workloadUsers.set(null);
    this.faultPercentage.set(null);
    this.showSearchPath.set(true);
  }

  selectDecision(decision: number): void {
    this.stopPlayback();
    this.selectedDecisionNumber.set(decision);
  }

  selectPrevious(): void {
    this.stopPlayback();
    this.moveSelection(-1);
  }

  selectNext(): void {
    this.stopPlayback();
    this.moveSelection(1);
  }

  selectBestFound(): void {
    const best = this.bestFound();
    if (best) {
      this.selectDecision(best.decision);
    }
  }

  play(): void {
    if (this.isPlaying() || this.visibleDecisions().length <= 1) {
      return;
    }
    if (this.selectedDecisionIndex() === this.visibleDecisions().length - 1) {
      this.selectedDecisionNumber.set(this.visibleDecisions()[0]?.decision ?? null);
    }
    this.isPlaying.set(true);
    this.playbackTimer = setInterval(() => this.advancePlayback(), 1200);
  }

  pause(): void {
    this.stopPlayback();
  }

  togglePlayback(): void {
    this.isPlaying() ? this.pause() : this.play();
  }

  private advancePlayback(): void {
    const decisions = this.visibleDecisions();
    const currentIndex = this.selectedDecisionIndex();
    if (currentIndex < 0 || currentIndex >= decisions.length - 1) {
      this.stopPlayback();
      return;
    }
    this.selectedDecisionNumber.set(decisions[currentIndex + 1].decision);
    if (currentIndex + 1 >= decisions.length - 1) {
      this.stopPlayback();
    }
  }

  private stopPlayback(): void {
    if (this.playbackTimer) {
      clearInterval(this.playbackTimer);
      this.playbackTimer = null;
    }
    this.isPlaying.set(false);
  }

  private moveSelection(offset: number): void {
    const decisions = this.visibleDecisions();
    if (decisions.length === 0) {
      return;
    }
    const currentIndex = this.selectedDecisionIndex();
    const nextIndex = Math.min(decisions.length - 1, Math.max(0, currentIndex + offset));
    this.selectedDecisionNumber.set(decisions[nextIndex].decision);
  }

  private matchesResult(result: NormalizedResult): boolean {
    return this.matchesContext(result);
  }

  private matchesContext(context: { workloadUsers?: number; faultPercentage?: number }): boolean {
    const users = this.workloadUsers();
    const fault = this.faultPercentage();
    return (
      (users === null || context.workloadUsers === users) &&
      (fault === null || context.faultPercentage === fault)
    );
  }
}

export function isInitialDecision(decision: { phase?: string; selectionMode?: string }): boolean {
  return decision.selectionMode === 'INITIAL_BATCH' || decision.phase === 'initialSample' || decision.phase === 'initialSelection';
}

export function observedScore(decision: NormalizedDecision): number | undefined {
  return decision.aggregatedResult?.score ?? decision.aggregatedResult?.currentScore;
}
