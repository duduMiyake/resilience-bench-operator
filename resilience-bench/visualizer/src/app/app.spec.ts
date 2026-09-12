import { App } from './app';
import { TestBed } from '@angular/core/testing';
import { ExampleRunsService } from './core/services/example-runs.service';

describe('App', () => {
  it('opens KNN automatically and allows switching examples without a file picker', async () => {
    const run = (heuristic: string) => ({ label: heuristic, trace: { schemaVersion: 2, benchmark: 'test', runId: heuristic, heuristic, decisions: [] }, results: { results: [] } });
    TestBed.configureTestingModule({ providers: [{ provide: ExampleRunsService, useValue: { load: async () => ({ runs: [run('knnAdaptive'), run('randomSampling')], reference: { results: [] } }) } }] });
    const app = TestBed.runInInjectionContext(() => new App());
    await Promise.resolve();
    expect(app.state.run()?.strategy).toBe('knnAdaptive');
    app.openExample(1);
    expect(app.state.run()?.strategy).toBe('randomSampling');
    expect(app.loading()).toBe(false);
  });

  it('leaves manual loading available when examples fail', async () => {
    TestBed.configureTestingModule({ providers: [{ provide: ExampleRunsService, useValue: { load: async () => { throw new Error('Missing example'); } } }] });
    const app = TestBed.runInInjectionContext(() => new App());
    await Promise.resolve();
    expect(app.state.run()).toBeNull();
    expect(app.error()).toBe('Missing example');
    expect(app.loading()).toBe(false);
  });
  it('defines the standalone application shell', () => {
    expect(App).toBeDefined();
  });
});
