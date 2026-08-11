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
- `Exhaustive Reference` is optional. It contains full or broader result-space data used only as visual context.

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

## Best Found

Best Found summarizes the best observed configuration evaluated by the heuristic run. It is based on observed decision results from the trace, not exhaustive reference points, predicted score, or selection score. The View Decision action selects that decision globally, which updates Result Space, Search Progress, Decision Timeline, and Decision Details.

## Result Space

Result Space keeps the current metrics:

- X axis: Checkout Success Rate, where higher is better
- Y axis: p95 Iteration Duration (s), where lower is better

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

Reference Space is optional and visually secondary. It does not affect heuristic decision ordering, Best Found, Search Progress, or the search path. Its purpose is only to provide context for where the heuristic explored.
