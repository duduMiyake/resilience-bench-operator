# ResilienceBench Visualizer

The Visualizer is a standalone Angular application for inspecting local ResilienceBench heuristic run artifacts. It runs entirely in the browser and does not require the Java Operator, Maven build, Kubernetes, S3, or a backend API.

## Run Locally

```bash
cd resilience-bench/visualizer
npm ci
npm start
```

Use `npm test` for the frontend test suite and `npm run build` for a production build.

## Inputs

The loader separates the run artifacts by purpose:

- `Trace` is required. It contains the heuristic decisions, search order, phases, KNN metadata, expected scenarios, and execution records.
- `Run Results` is optional but recommended. It contains detailed metrics from the heuristic run and is joined to decisions by `scenarioHash` first, then scenario name.
- `Exhaustive Reference` is optional. It contains scenario-level exhaustive data used as visual context and, when compatible, as the quantitative reference.

Run results and exhaustive reference results are intentionally kept separate. A result that does not match a heuristic decision is no longer treated as exhaustive reference automatically.

## How To Read The Visualizer

Start with the summary cards to understand the size of the search space and how much of it was evaluated. Then use Best Found to jump to the best observed heuristic decision. Use Result Space to see where the evaluated configurations landed inside the broader reference space when one is provided. Enable Show Search Path to see the order in which the heuristic moved through the active operational context. Use Search Progress to see when observed scores improved and how the best score evolved. The Decision Timeline shows the selection order, and Decision Details explains why the selected configuration was chosen before exposing raw technical data.

## Summary Cards

The summary cards show:

- Search Space
- Evaluated Configurations
- Explored, including both percentage and absolute counts
- Executed Scenarios
- Cache Hits

A run with zero executed scenarios and many cache hits is valid. It means the scenario results were retrieved from cache.

## Search Outcome

Search Outcome summarizes the best observed configuration evaluated by the heuristic run, the decision where it was first found, evaluated/total configurations, explored ratio, and search-space reduction. The official heuristic score is always the trace-provided `decision.aggregatedResult.score`; exhaustive points, predicted scores, selection scores, and scenario metrics never replace it. This version explicitly treats higher scores as better (`maximize`). The View Decision action selects the heuristic best globally.

The ratios are:

```text
explored ratio = evaluated configurations / total configurations
search-space reduction = 1 - explored ratio
```

## Quantitative Exhaustive Reference

The frontend reproduces the Operator aggregation for the external Exhaustive Reference. Scenarios are grouped by normalized resilience connector configuration, independently of workload and fault context. Connector/object keys and arrays are normalized and connectors are sorted, matching `ResilienceConfigurationKey`; grouping never depends on array position.

For every configuration, each numeric scenario-result field is averaged across its operational contexts, matching `ConfigurationResultAggregator`. The default Operator objective is then applied exactly:

```text
configuration score = mean(checkout_success_rate | successRate)
                    - mean(iteration_duration_p95 | p95Latency)
```

The trace does not currently carry `ObjectiveSpec`. Therefore the reference comparison uses this exact default objective and checks configurations shared by the heuristic trace and exhaustive data. If a recomputed shared score differs from `aggregatedResult.score`, the aggregate reference is marked incompatible instead of silently presenting divergent values.

For a compatible reference under maximize semantics:

```text
absolute gap = reference best - heuristic best
relative gap = (reference best - heuristic best) / reference best * 100
```

Relative gap is unavailable when the reference best is zero. Quality milestones are fixed at 90%, 95%, and 99%; each reports the first heuristic decision whose best score so far reaches `reference best * threshold`, or `Not reached`.

Aggregate comparison is valid only when every exhaustive configuration covers every workload × fault context expected by the heuristic run. Incomplete coverage suppresses Reference Best, gaps, and quality thresholds.

## By Operational Context

The secondary table is limited to contexts present in both datasets and remains available even when aggregate reference coverage is incomplete. It compares the best Checkout Success Rate (higher is better) and best p95 Iteration Duration (lower is better) independently. It does not synthesize a context score.

## Result Space

Result Space keeps the current metrics:

- X axis: Checkout Success Rate, where higher is better
- Y axis: p95 Iteration Duration (ms), where lower is better

The preferred direction is therefore toward the lower-right region. Reference points are shown with low emphasis. Initial Sample and Adaptive Search points use different visual styles, the selected decision has a larger ringed marker, and new-best decisions use a separate marker.

Operational Context filters affect Result Space so that workload and fault-rate combinations are not mixed in one scatter view.

## Show Search Path

Show Search Path connects evaluated heuristic configurations in the order selected by the heuristic for the active operational context. Reference-space configurations are not part of this path. If a decision has no result point for the current workload/fault filter, it is skipped safely instead of inventing coordinates.

## Operational Context

A configuration can produce scenario results across multiple operational contexts, such as workload users multiplied by fault rate. If only one workload and one fault rate exist, the UI shows a compact label. If multiple values exist, dropdown filters are shown for Workload and Fault Rate.

Changing these filters affects Result Space and context-specific result views. It does not change Search Progress, decision order, or Best Score So Far, because those are configuration-level heuristic values.

## Search Progress

Search Progress shows:

- Observed Score: the score obtained by evaluating the selected configuration
- Best Score So Far: the best score known after that evaluation

The chart visually separates Initial Sample decisions from Adaptive Search decisions using phase and selection mode from the trace. Initial Sample decisions provide the first observations used by the adaptive heuristic. Adaptive Search decisions are selected using information learned from previously evaluated configurations.

## Decision Timeline

Each item represents one configuration decision. The timeline distinguishes Initial Sample, Adaptive Search, New Best, and Currently Selected states. Selecting a timeline item updates the shared selected decision used by Result Space, Search Progress, Timeline, and Decision Details.

## Playback

Playback advances at the configuration-decision level. Previous, Play/Pause, and Next controls update the shared selected decision. Playback advances roughly every 1.2 seconds, pauses on manual selection, and stops at the final decision.

## Why Was This Configuration Selected?

For KNN Adaptive traces with metadata, Decision Details generates a deterministic explanation from the selected decision. It uses:

- Predicted Score
- Uncertainty
- Exploration Bonus
- Selection Score
- Observed Score
- Best Score So Far
- Improved Best

Selection Score is used by the heuristic to choose the candidate. Observed Score is the actual score measured after evaluation. Best Score is the best observed configuration score so far. The visual formula is:

```text
Predicted Score + Exploration Bonus = Selection Score
```

If the metadata is missing, the explanation block remains stable and reports that the formula is unavailable.

## Decision Details

Decision Details is organized as:

1. Summary / Explanation
2. Configuration Overview
3. Technical Decision Metadata
4. Raw Parameters
5. Nearest Neighbors
6. Scenario Executions

Technical sections are collapsed by default so interpretation comes first.

## KNN Metadata

KNN metadata is read directly from the trace. The visualizer does not recompute the heuristic, change scoring formulas, or infer new decisions. Nearest Neighbors are displayed as compact configuration identifiers with the full summary available as tooltip text.

## Scenario Executions

Scenario executions list the scenario name, operational context, source, and score. Sources are displayed as human-readable tags:

- Cache Hit
- Executed

Mixed cache and execution sources are supported in the same decision.

## Limitations

The visualizer can only explain values present in the trace/results artifacts. More precise UX explanations would benefit from explicit units in the result schema, explicit objective direction metadata, and a trace-level declaration describing whether a results file is run data or reference data. The frontend now handles that last distinction through separate loader inputs without changing the backend schema.


## Reference Space

Reference Space is optional and visually secondary. It does not affect heuristic decision ordering, heuristic Best Found, Search Progress, or the search path. Compatible reference data additionally feeds Search Outcome and By Operational Context as documented above.
