import { computed, Injectable, signal } from '@angular/core';
import { NormalizedDecision, NormalizedResult, NormalizedRun } from '../models/visualizer.models';

@Injectable({ providedIn: 'root' })
export class ExplorerStateService {
  readonly run = signal<NormalizedRun | null>(null);
  readonly selectedDecisionNumber = signal<number | null>(null);
  readonly workloadUsers = signal<number | null>(null);
  readonly faultPercentage = signal<number | null>(null);
  readonly isPlaying = signal(false);

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

  setRun(run: NormalizedRun): void {
    this.stopPlayback();
    this.run.set(run);
    this.workloadUsers.set(null);
    this.faultPercentage.set(null);
    this.selectedDecisionNumber.set(run.decisions[0]?.decision ?? null);
  }

  clear(): void {
    this.stopPlayback();
    this.run.set(null);
    this.selectedDecisionNumber.set(null);
    this.workloadUsers.set(null);
    this.faultPercentage.set(null);
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
