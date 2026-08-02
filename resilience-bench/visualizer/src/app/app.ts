import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastModule } from 'primeng/toast';
import { ExplorerStateService } from './core/services/explorer-state.service';
import { ExplorerComponent } from './features/explorer/explorer.component';
import { RunLoaderComponent } from './features/loader/run-loader.component';

@Component({
  selector: 'app-root',
  imports: [ToastModule, RunLoaderComponent, ExplorerComponent],
  template: `
    <p-toast position="top-right" />
    @if (state.run(); as run) {
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
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  readonly state = inject(ExplorerStateService);
}
