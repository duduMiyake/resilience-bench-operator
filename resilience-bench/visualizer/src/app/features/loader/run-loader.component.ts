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
  readonly resultsFile = signal<LoadedJsonFile | null>(null);
  readonly error = signal<string | null>(null);
  readonly loading = signal(false);

  async selectTrace(event: FileSelectEvent): Promise<void> {
    await this.readSelection(event, 'trace', true);
  }

  async selectResults(event: FileSelectEvent): Promise<void> {
    await this.readSelection(event, 'results', false);
  }

  openRun(): void {
    const trace = this.traceFile();
    if (!trace) {
      this.error.set('Selecione um trace.json para abrir a rodada.');
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    try {
      const run = this.normalizer.normalize(trace.value, this.resultsFile()?.value);
      this.state.setRun(run);
      this.messages.add({
        severity: 'success',
        summary: 'Rodada carregada',
        detail: `${run.benchmark} / ${run.strategy}`,
      });
    } catch (error) {
      this.error.set(
        error instanceof Error ? error.message : 'NÃ£o foi possÃ­vel abrir os arquivos.',
      );
    } finally {
      this.loading.set(false);
    }
  }

  private async readSelection(
    event: FileSelectEvent,
    target: 'trace' | 'results',
    required: boolean,
  ): Promise<void> {
    const file = event.files[0];
    if (!file) {
      if (required) {
        this.error.set('Nenhum arquivo foi selecionado.');
      }
      return;
    }
    if (!file.name.toLowerCase().endsWith('.json')) {
      this.error.set(`${file.name} nÃ£o Ã© um arquivo JSON.`);
      return;
    }

    try {
      const loaded = {
        name: file.name,
        value: this.normalizer.parseJson(await file.text(), file.name),
      };
      if (target === 'trace') {
        this.traceFile.set(loaded);
      } else {
        this.resultsFile.set(loaded);
      }
      this.error.set(null);
    } catch (error) {
      this.error.set(
        error instanceof VisualizerParseError
          ? error.message
          : `NÃ£o foi possÃ­vel ler ${file.name}.`,
      );
    }
  }
}
