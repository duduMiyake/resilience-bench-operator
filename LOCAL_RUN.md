# Running 100% locally

This setup writes benchmark result files to `./test-results` in this repository when the cluster is created with `kind-local-config.yaml`.

## Create a local kind cluster

```bash
kind create cluster --config kind-local-config.yaml
```

The kind config mounts this repository folder:

```text
./test-results
```

inside the kind node at:

```text
/mnt/data/test-results
```

The Kubernetes `PersistentVolume` then exposes it to the operator and k6 jobs at:

```text
/results
```

## Build and load the local operator image

```bash
cd resilience-bench
mvn com.google.cloud.tools:jib-maven-plugin:3.4.2:dockerBuild -B -f operator/pom.xml -Djib.to.image=resiliencebench-operator:local
kind load docker-image resiliencebench-operator:local
cd ..
```

## Deploy

```bash
kubectl apply -f ./crd
kubectl apply -k ./samples/overlays/hipstershop
```

## Benchmark strategy options

Configure the scenario selection strategy in the `Benchmark` resource:

```yaml
spec:
  strategy:
    type: exhaustive
```

### Exhaustive

Runs every scenario generated from the benchmark search space. This is the default when `spec.strategy` is omitted.

```yaml
spec:
  strategy:
    type: exhaustive
```

### Random sampling

Selects a reproducible random subset before execution starts.

```yaml
spec:
  strategy:
    type: randomSampling
    seed: 42
    sampleRate: 0.02
    maxScenarios: 250
```

- `seed`: keeps the selected scenarios reproducible.
- `sampleRate`: fraction of generated scenarios to select, from `0` to `1`.
- `maxScenarios`: maximum number of scenarios to run.

If both `sampleRate` and `maxScenarios` are present, the operator applies `sampleRate` first and then caps by `maxScenarios`.

### Bayesian optimization

Starts with an initial space-filling sample and then chooses the next scenario adaptively from the remaining search space using previous results.

```yaml
spec:
  strategy:
    type: bayesianOptimization
    seed: 42
    initialSamples: 20
    maxEvaluations: 100
    acquisitionFunction: expectedImprovement
    objective:
      maximize:
        - successRate
      minimize:
        - p95Latency
```

- `initialSamples`: number of scenarios placed in the first queue.
- `maxEvaluations`: total scenario execution budget.
- `acquisitionFunction`: currently only `expectedImprovement` is supported. It is optional because this is the default behavior.
- `objective.maximize`: metrics that increase the score.
- `objective.minimize`: metrics that decrease the score.

Metric aliases currently supported:

```text
successRate -> checkout_success_rate
p95Latency  -> iteration_duration_p95
```

## View results

Result files are written directly to:

```text
./test-results
```

Each scenario writes one JSON file, and the operator also writes an aggregated `*-results.json` file.
