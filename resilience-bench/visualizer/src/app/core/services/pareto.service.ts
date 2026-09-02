import { Injectable } from '@angular/core';
import { NormalizedResult } from '../models/visualizer.models';

@Injectable({ providedIn: 'root' })
export class ParetoService {
  frontier(results: NormalizedResult[]): NormalizedResult[] {
    return results
      .filter((candidate) => candidate.checkoutSuccessRate !== undefined && candidate.iterationDurationP95 !== undefined)
      .filter((candidate, index, all) => !all.some((other, otherIndex) => otherIndex !== index &&
        other.checkoutSuccessRate! >= candidate.checkoutSuccessRate! &&
        other.iterationDurationP95! <= candidate.iterationDurationP95! &&
        (other.checkoutSuccessRate! > candidate.checkoutSuccessRate! || other.iterationDurationP95! < candidate.iterationDurationP95!)))
      .sort((left, right) => left.checkoutSuccessRate! - right.checkoutSuccessRate!);
  }

  configurationKey(result: NormalizedResult): string {
    return stableJson(result.connectors);
  }
}

function stableJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableJson).sort().join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value as Record<string, unknown>).sort().map((key) => `${JSON.stringify(key)}:${stableJson((value as Record<string, unknown>)[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}
