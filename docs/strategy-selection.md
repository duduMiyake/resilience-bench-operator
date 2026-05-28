# Scenario selection strategies

`Benchmark.spec.strategy` controls how generated scenarios are selected for execution.

If `strategy` is omitted, ResilienceBench uses `exhaustive` to preserve legacy behavior.

## Exhaustive

`exhaustive` runs every scenario produced by the current scenario generator.

```yaml
apiVersion: resiliencebench.io/v1beta1
kind: Benchmark
metadata:
  name: onlineboutique
spec:
  workload: fixed-iterations-loadtest
  strategy:
    type: exhaustive
  scenarios:
    []
```

## Random sampling

`randomSampling` first generates the full scenario space, then shuffles it with a deterministic seed and selects a subset.

Defaults:

- `seed`: `42`
- `sampleRate`: `0.5` when neither `sampleRate` nor `maxScenarios` is provided

`sampleRate` must be in `(0, 1]`. The selected count is `ceil(totalScenarios * sampleRate)`, with at least one selected scenario when the full scenario space is not empty.

`maxScenarios` must be greater than `0`. When both are set, `sampleRate` is applied first and `maxScenarios` caps the result.

```yaml
apiVersion: resiliencebench.io/v1beta1
kind: Benchmark
metadata:
  name: onlineboutique
spec:
  workload: fixed-iterations-loadtest
  strategy:
    type: randomSampling
    sampleRate: 0.5
    seed: 42
  scenarios:
    []
```

```yaml
apiVersion: resiliencebench.io/v1beta1
kind: Benchmark
metadata:
  name: onlineboutique
spec:
  workload: fixed-iterations-loadtest
  strategy:
    type: randomSampling
    maxScenarios: 10
    seed: 42
  scenarios:
    []
```

```yaml
apiVersion: resiliencebench.io/v1beta1
kind: Benchmark
metadata:
  name: onlineboutique
spec:
  workload: fixed-iterations-loadtest
  strategy:
    type: randomSampling
    sampleRate: 0.5
    maxScenarios: 10
    seed: 42
  scenarios:
    []
```

## Comparing strategies

Run an `exhaustive` benchmark as the baseline, then run one or more `randomSampling` benchmarks with fixed seeds.

Useful comparisons:

- Number of executed scenarios.
- Total execution time.
- Best scenario found by each strategy.
- Top-k overlap between exhaustive and sampled rankings.
- Regret, such as the difference between the best sampled result and the best exhaustive result.

## Local experiment folders

Store local experiment outputs under `experiments/`, which is ignored by git:

```text
experiments/onlineboutique/exhaustive/
experiments/onlineboutique/random-sampling/
```

Do not commit raw benchmark results. Keep only documentation, scripts, or summaries that are intended to be shared.
