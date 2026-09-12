import { Injectable } from '@angular/core';
import { ComparisonRunInput } from '../models/comparison.models';

@Injectable({ providedIn: 'root' })
export class ExampleRunsService {
  async load(): Promise<{ runs: ComparisonRunInput[]; reference: unknown }> {
    const paths = ['KNN/trace.json', 'KNN/results.json', 'RandomSampling/trace.json',
      'RandomSampling/results.json', 'Exhaustive/exhaustive reference.json'];
    const [knnTrace, knnResults, randomTrace, randomResults, reference] = await Promise.all(
      paths.map(async (path) => {
        const response = await fetch(`examples/${path.split('/').map(encodeURIComponent).join('/')}`);
        if (!response.ok) throw new Error(`Could not load example ${path} (${response.status}). You can still open local files.`);
        return response.json();
      }),
    );
    return { runs: [
      { label: 'KNN', trace: knnTrace, results: knnResults },
      { label: 'RandomSampling', trace: randomTrace, results: randomResults },
    ], reference };
  }
}
