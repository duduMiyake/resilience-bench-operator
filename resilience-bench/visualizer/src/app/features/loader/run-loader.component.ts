import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { FileSelectEvent, FileUploadModule } from 'primeng/fileupload';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';
import { LoadedJsonFile, VisualizerParseError } from '../../core/models/visualizer.models';
import { ExplorerStateService } from '../../core/services/explorer-state.service';
import { RunNormalizerService } from '../../core/services/run-normalizer.service';

@Component({
  selector: 'app-run-loader',
  imports: [ButtonModule, CardModule, FileUploadModule, MessageModule],
  templateUrl: './run-loader.component.html',
  styleUrl: './run-loader.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RunLoaderComponent {
  private readonly normalizer = inject(RunNormalizerService);
  private readonly state = inject(ExplorerStateService);
  private readonly messages = inject(MessageService);

  readonly traceFile = signal<LoadedJsonFile | null>(null);
  readonly runResultsFile = signal<LoadedJsonFile | null>(null);
  readonly referenceResultsFile = signal<LoadedJsonFile | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);

  async selectTrace(event: FileSelectEvent): Promise<void> {
    await this.readSelection(event, 'trace', true);
  }

  async selectRunResults(event: FileSelectEvent): Promise<void> {
    await this.readSelection(event, 'runResults', false);
  }

  async selectReferenceResults(event: FileSelectEvent): Promise<void> {
    await this.readSelection(event, 'referenceResults', false);
  }

  openRun(): void {
    const trace = this.traceFile();
    if (!trace) {
      this.error.set('Select a trace.json file to open the run.');
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    try {
      const run = this.normalizer.normalize(
        trace.value,
        this.runResultsFile()?.value,
        this.referenceResultsFile()?.value,
      );
      this.state.setRun(run);
      this.messages.add({
        severity: 'success',
        summary: 'Run loaded',
        detail: `${run.benchmark} / ${run.strategy}`,
      });
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'The selected files could not be opened.');
    } finally {
      this.loading.set(false);
    }
  }

  private async readSelection(
    event: FileSelectEvent,
    target: 'trace' | 'runResults' | 'referenceResults',
    required: boolean,
  ): Promise<void> {
    const file = event.files[0];
    if (!file) {
      if (required) {
        this.error.set('No file was selected.');
      }
      return;
    }
    if (!file.name.toLowerCase().endsWith('.json')) {
      this.error.set(`${file.name} is not a JSON file.`);
      return;
    }

    try {
      const loaded = {
        name: file.name,
        value: this.normalizer.parseJson(await file.text(), file.name),
      };
      if (target === 'trace') {
        this.traceFile.set(loaded);
      } else if (target === 'runResults') {
        this.runResultsFile.set(loaded);
      } else {
        this.referenceResultsFile.set(loaded);
      }
      this.error.set(null);
    } catch (error) {
      this.error.set(
        error instanceof VisualizerParseError ? error.message : `Could not read ${file.name}.`,
      );
    }
  }
}
