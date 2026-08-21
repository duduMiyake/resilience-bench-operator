import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { FileSelectEvent, FileUploadModule } from 'primeng/fileupload';
import { MessageModule } from 'primeng/message';
import { ComparisonModel } from '../../core/models/comparison.models';
import { LoadedJsonFile, VisualizerParseError } from '../../core/models/visualizer.models';
import { ComparisonService } from '../../core/services/comparison.service';
import { RunNormalizerService } from '../../core/services/run-normalizer.service';
import { ComparisonComponent } from './comparison.component';

interface RunDraft {
  label: string;
  trace: LoadedJsonFile | null;
  results: LoadedJsonFile | null;
}

@Component({
  selector: 'app-comparison-loader',
  imports: [ButtonModule, CardModule, FileUploadModule, MessageModule, ComparisonComponent],
  templateUrl: './comparison-loader.component.html',
  styleUrl: './comparison-loader.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComparisonLoaderComponent {
  private readonly comparisonService = inject(ComparisonService);
  private readonly normalizer = inject(RunNormalizerService);
  readonly runs = signal<RunDraft[]>([this.emptyRun(1), this.emptyRun(2)]);
  readonly referenceFile = signal<LoadedJsonFile | null>(null);
  readonly comparison = signal<ComparisonModel | null>(null);
  readonly error = signal<string | null>(null);

  addRun(): void {
    this.runs.update((runs) => [...runs, this.emptyRun(runs.length + 1)]);
  }

  removeRun(index: number): void {
    if (this.runs().length <= 2) return;
    this.runs.update((runs) => runs.filter((_, current) => current !== index));
  }

  async selectTrace(index: number, event: FileSelectEvent): Promise<void> {
    await this.readRunFile(index, 'trace', event);
  }

  async selectResults(index: number, event: FileSelectEvent): Promise<void> {
    await this.readRunFile(index, 'results', event);
  }

  async selectReference(event: FileSelectEvent): Promise<void> {
    const file = event.files[0];
    if (!file) return;
    try {
      this.referenceFile.set(await this.readFile(file));
      this.error.set(null);
    } catch (cause) {
      this.error.set(cause instanceof Error ? cause.message : `Could not read ${file.name}.`);
    }
  }

  compare(): void {
    const drafts = this.runs();
    if (drafts.some((run) => !run.trace || !run.results)) {
      this.error.set('Each run needs both a trace file and a results file.');
      this.comparison.set(null);
      return;
    }
    try {
      const model = this.comparisonService.build(
        drafts.map((run) => ({ label: run.label, trace: run.trace!.value, results: run.results!.value })),
        this.referenceFile()?.value,
      );
      this.error.set(model.errors.length ? model.errors.join(' ') : null);
      this.comparison.set(model.errors.length ? null : model);
    } catch (cause) {
      this.error.set(cause instanceof Error ? cause.message : 'The selected files could not be compared.');
      this.comparison.set(null);
    }
  }

  private async readRunFile(index: number, kind: 'trace' | 'results', event: FileSelectEvent): Promise<void> {
    const file = event.files[0];
    if (!file) return;
    try {
      const loaded = await this.readFile(file);
      this.runs.update((runs) => runs.map((run, current) => current === index ? { ...run, [kind]: loaded } : run));
      this.error.set(null);
    } catch (cause) {
      this.error.set(cause instanceof Error ? cause.message : `Could not read ${file.name}.`);
    }
  }

  private async readFile(file: File): Promise<LoadedJsonFile> {
    if (!file.name.toLowerCase().endsWith('.json')) {
      throw new VisualizerParseError(`${file.name} is not a JSON file.`);
    }
    try {
      return { name: file.name, value: this.normalizer.parseJson(await file.text(), file.name) };
    } catch {
      throw new VisualizerParseError(`${file.name} does not contain valid JSON.`);
    }
  }

  private emptyRun(index: number): RunDraft {
    return { label: '', trace: null, results: null };
  }
}
