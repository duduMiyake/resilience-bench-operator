import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { ExplorerStateService } from './core/services/explorer-state.service';
import { ExplorerComponent } from './features/explorer/explorer.component';
import { RunLoaderComponent } from './features/loader/run-loader.component';
import { ComparisonLoaderComponent } from './features/comparison/comparison-loader.component';
import { ComparisonComponent } from './features/comparison/comparison.component';
import { ComparisonModel } from './core/models/comparison.models';
import { ExampleRunsService } from './core/services/example-runs.service';
import { RunNormalizerService } from './core/services/run-normalizer.service';
import { ComparisonService } from './core/services/comparison.service';

@Component({
  selector: 'app-root',
  imports: [ToastModule, ButtonModule, RunLoaderComponent, ExplorerComponent, ComparisonLoaderComponent, ComparisonComponent],
  template: `
    <p-toast position="top-right" />
    <nav class="mode-nav" aria-label="Visualizer mode">
      <strong>ResilienceBench</strong>
      <p-button label="Run Explorer" [outlined]="mode() !== 'explorer'" (onClick)="mode.set('explorer'); state.stopPlayback()" />
      <p-button label="Compare Runs" [outlined]="mode() !== 'compare'" (onClick)="mode.set('compare'); state.stopPlayback()" />
      <span>Repository examples:</span>
      <p-button label="KNN" [outlined]="true" [disabled]="!examples()" (onClick)="openExample(0)" />
      <p-button label="RandomSampling" [outlined]="true" [disabled]="!examples()" (onClick)="openExample(1)" />
      <p-button label="Compare examples" [outlined]="true" [disabled]="!examples()" (onClick)="compareExamples()" />
    </nav>
    @if (loading()) { <p role="status" class="load-status">Loading repository examples…</p> }
    @if (error()) { <p role="alert" class="load-status">{{ error() }} <button class="rb-button" (click)="loadExamples()">Retry</button></p> }
    @if (mode() === 'compare' && comparison(); as model) {
      <app-comparison [model]="model" (loadAnother)="comparison.set(null)" />
    } @else if (mode() === 'compare') {
      <app-comparison-loader />
    } @else if (state.run(); as run) {
      <app-explorer [run]="run" (loadAnother)="state.clear()" />
    } @else {
      <app-run-loader />
    }
  `,
  styles: `
    :host {
      display: block;
      min-height: 100vh;
    }

    .mode-nav {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 0.35rem;
      padding: 0.55rem max(1rem, calc((100vw - 1440px) / 2));
      border-bottom: 1px solid var(--rb-border);
      background: var(--rb-surface);
    }
    .mode-nav span, .load-status { color: var(--rb-muted); font-size: .85rem; }
    .load-status { padding: 1rem; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  readonly state = inject(ExplorerStateService);
  readonly mode = signal<'explorer' | 'compare'>('explorer');
  private readonly exampleService = inject(ExampleRunsService);
  private readonly normalizer = inject(RunNormalizerService);
  private readonly comparisonService = inject(ComparisonService);
  readonly examples = signal<Awaited<ReturnType<ExampleRunsService['load']>> | null>(null);
  readonly comparison = signal<ComparisonModel | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  constructor() { void this.loadExamples(); }

  async loadExamples(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.examples.set(await this.exampleService.load());
      if (!this.state.run() && this.mode() === 'explorer') this.openExample(0);
    } catch (cause) {
      this.error.set(cause instanceof Error ? cause.message : 'Examples unavailable. Open local files instead.');
    } finally { this.loading.set(false); }
  }

  openExample(index: number): void {
    const examples = this.examples();
    if (!examples) return;
    try {
      const run = examples.runs[index];
      this.state.setRun(this.normalizer.normalize(run.trace, run.results, examples.reference));
      this.mode.set('explorer');
      this.error.set(null);
    } catch (cause) { this.error.set(String(cause)); }
  }

  compareExamples(): void {
    const examples = this.examples();
    if (!examples) return;
    try {
      const model = this.comparisonService.build(examples.runs, examples.reference);
      if (model.errors.length) throw new Error(model.errors.join(' '));
      this.comparison.set(model);
      this.state.stopPlayback();
      this.mode.set('compare');
      this.error.set(null);
    } catch (cause) { this.error.set(String(cause)); }
  }
}
