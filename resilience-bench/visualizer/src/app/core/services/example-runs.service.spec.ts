import { ExampleRunsService } from './example-runs.service';

describe('Repository examples', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('loads both runs and keeps their results separate from the reference', async () => {
    const fetchMock = vi.fn(async (url: string) => ({ ok: true, json: async () => ({ url }) }));
    vi.stubGlobal('fetch', fetchMock);
    const examples = await new ExampleRunsService().load();
    expect(fetchMock).toHaveBeenCalledTimes(5);
    expect(examples.runs[0].results).toEqual({ url: 'examples/KNN/results.json' });
    expect(examples.runs[1].trace).toEqual({ url: 'examples/RandomSampling/trace.json' });
    expect(examples.reference).toEqual({ url: 'examples/Exhaustive/exhaustive%20reference.json' });
  });

  it('reports an unavailable file instead of accepting an error response', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 404 })));
    await expect(new ExampleRunsService().load()).rejects.toThrow('Could not load example');
  });
});
