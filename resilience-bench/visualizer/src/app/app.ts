import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { ExplorerStateService } from './core/services/explorer-state.service';
import { ExplorerComponent } from './features/explorer/explorer.component';
import { RunLoaderComponent } from './features/loader/run-loader.component';
import { ComparisonLoaderComponent } from './features/comparison/comparison-loader.component';

@Component({
  selector: 'app-root',
  imports: [ToastModule, ButtonModule, RunLoaderComponent, ExplorerComponent, ComparisonLoaderComponent],
  template: `
    <p-toast position="top-right" />
    <nav class="mode-nav" aria-label="Visualizer mode">
      <p-button label="Run Explorer" icon="pi pi-search" [text]="mode() !== 'explorer'" [outlined]="mode() === 'explorer'" (onClick)="mode.set('explorer'); state.clear()" />
      <p-button label="Compare Runs" icon="pi pi-arrows-alt" [text]="mode() !== 'compare'" [outlined]="mode() === 'compare'" (onClick)="mode.set('compare'); state.clear()" />
    </nav>
    @if (mode() === 'compare') {
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
      gap: 0.35rem;
      padding: 0.55rem max(1rem, calc((100vw - 1440px) / 2));
      border-bottom: 1px solid var(--rb-border);
      background: var(--rb-surface);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  readonly state = inject(ExplorerStateService);
  readonly mode = signal<'explorer' | 'compare'>('explorer');
}
