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

## k-NN adaptive

`knnAdaptive` starts with an initial space-filling sample, then selects one additional scenario at a time using the evaluated scenarios that are closest to each remaining candidate.

Defaults:

- `seed`: `42`
- `initialSamples`: `20`, capped by the evaluation budget and by the full scenario space size
- `maxEvaluations`: full scenario space size when neither `maxEvaluations` nor `maxScenarios` is provided
- `neighbors`: `3`
- `explorationWeight`: `0.1`
- `objective`: `successRate - p95Latency` when no explicit objective is provided

`neighbors` must be greater than `0`. `explorationWeight` must be greater than or equal to `0`.

For each remaining candidate, `knnAdaptive` finds the `neighbors` evaluated scenarios with the smallest configuration distance, predicts the candidate score using a distance-weighted average of their real scores, and adds an exploration bonus:

```text
selectionScore = predictedScore + explorationWeight * uncertainty
```

`uncertainty` is the distance to the closest evaluated scenario. A higher `explorationWeight` favors candidates in less explored regions; `0` makes the strategy choose only by predicted score.

```yaml
apiVersion: resiliencebench.io/v1beta1
kind: Benchmark
metadata:
  name: onlineboutique
spec:
  workload: fixed-iterations-loadtest
  strategy:
    type: knnAdaptive
    seed: 42
    initialSamples: 20
    maxEvaluations: 100
    neighbors: 3
    explorationWeight: 0.1
    objective:
      maximize:
        - successRate
      minimize:
        - p95Latency
  scenarios:
    []
```
