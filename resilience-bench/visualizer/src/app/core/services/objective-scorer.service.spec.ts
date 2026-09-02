import { NormalizedObjective, NormalizedResult } from '../models/visualizer.models';
import { ObjectiveScorer } from './objective-scorer.service';

describe('ObjectiveScorer', () => {
  const scorer = new ObjectiveScorer();
  const objective: NormalizedObjective = {
    format: 'structured',
    metrics: [
      {
        name: 'success',
        direction: 'maximize',
        effectiveWeight: 0.5,
        normalization: { type: 'minMax', min: 0, max: 1 },
      },
      {
        name: 'latency',
        direction: 'minimize',
        effectiveWeight: 0.5,
        normalization: { type: 'reciprocal', scale: 22450 },
      },
    ],
  };

  it('normalizes minMax maximize and minimize metrics', () => {
    expect(scorer.scoreConfiguration([result({ success: 0.75, latency: 100 })], {
      ...objective,
      metrics: [{ ...objective.metrics[0], effectiveWeight: 1 }],
    }).score).toBe(0.75);
    expect(scorer.scoreConfiguration([result({ success: 0.25, latency: 100 })], {
      ...objective,
      metrics: [{ ...objective.metrics[0], direction: 'minimize', effectiveWeight: 1 }],
    }).score).toBe(0.75);
  });

  it('calculates reciprocal normalization and the weighted score', () => {
    const score = scorer.scoreConfiguration([result({ success: 0.972, latency: 22582.18567095 })], objective).score;
    expect(score).toBeCloseTo(0.7352661600, 10);
  });

  it('rejects missing structured metrics instead of substituting zero', () => {
    const score = scorer.scoreConfiguration([result({ success: 0.9 })], objective);
    expect(score.score).toBeUndefined();
    expect(score.reason).toContain("'latency'");
  });

  it('keeps the legacy raw score separate from structured scoring', () => {
    expect(scorer.legacyScore([result({ checkout_success_rate: 0.9, iteration_duration_p95: 0.1 })])).toBeCloseTo(0.8);
    expect(scorer.scoreConfiguration([result({ success: 0.9, latency: 0.1 })], objective).score).not.toBeCloseTo(0.8);
  });
});

function result(metrics: Record<string, number>): NormalizedResult {
  return {
    scenario: 'scenario',
    faultServices: [],
    connectors: [],
    metrics,
    raw: metrics,
  };
}
