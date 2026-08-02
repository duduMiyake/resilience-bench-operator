import { computed, Injectable, signal } from '@angular/core';
import { NormalizedDecision, NormalizedResult, NormalizedRun } from '../models/visualizer.models';

@Injectable({ providedIn: 'root' })
export class ExplorerStateService {
  readonly run = signal<NormalizedRun | null>(null);
  readonly selectedDecisionNumber = signal<number | null>(null);
  readonly workloadUsers = signal<number | null>(null);
  readonly faultPercentage = signal<number | null>(null);

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

  readonly visibleDecisions = computed(() => {
    const run = this.run();
    if (!run) {
      return [];
    }
    return run.decisions.filter((decision) => {
      const contexts =
        decision.joinedResults.length > 0 ? decision.joinedResults : decision.expectedScenarios;
      return contexts.length === 0 || contexts.some((context) => this.matchesContext(context));
    });
  });

  setRun(run: NormalizedRun): void {
    this.run.set(run);
    this.workloadUsers.set(null);
    this.faultPercentage.set(null);
    this.selectedDecisionNumber.set(run.decisions[0]?.decision ?? null);
  }

  clear(): void {
    this.run.set(null);
    this.selectedDecisionNumber.set(null);
    this.workloadUsers.set(null);
    this.faultPercentage.set(null);
  }

  selectDecision(decision: number): void {
    this.selectedDecisionNumber.set(decision);
  }

  selectPrevious(): void {
    this.moveSelection(-1);
  }

  selectNext(): void {
    this.moveSelection(1);
  }

  private moveSelection(offset: number): void {
    const decisions = this.visibleDecisions();
    if (decisions.length === 0) {
      return;
    }
    const currentIndex = decisions.findIndex(
      (decision) => decision.decision === this.selectedDecisionNumber(),
    );
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
