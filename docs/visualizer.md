# ResilienceBench Visualizer

## Result-first interface and repository examples

On startup, the application loads the KNN trace/results and exhaustive reference
from `resilience-bench/visualizer/execuções`. The Angular asset configuration serves
only JSON files from this directory at `examples/`; no copies or backend are needed.
KNN opens automatically on each reload. The top navigation also opens RandomSampling
or compares both examples. Manual file loading remains available, including after
an example fails to load. Replacing these files updates the examples served by the
development server; production deployments require rebuilding. These example JSON
files are included in the frontend deployment, so deploy only shareable datasets.

The Result view presents three indicators: best observed score, evaluated/total
configurations, and absolute reference gap. Executions and cache reuse appear as
secondary provenance. The score explanation reads the structured objective from
the trace (weights and normalization); absent metadata is identified explicitly.
The search-progress chart shows discrete observations and a stepwise running best
computed from official scores, with a compatible reference line and a table alternative.

Investigate search contains compact decision navigation, playback, next improvement,
context filters, a full-width result-space plot and decision details below it. Users can pin
a configuration and compare its parameters and score to another decision. Random
sampling is described separately from adaptive KNN. Scenario metrics belong to the
selected configuration; independent per-metric optima are in a collapsed section.

Evaluation scores expand into a full-width table below Search Progress, with a
sticky header and scrollable rows. Parameter labels omit technical environment
prefixes (for example, `source_env_GRPC_BACKOFF_MULTIPLIER` becomes `Backoff multiplier`);
the original path remains in the tooltip. Success rates show two decimal places
and a percent sign, with standard rounding (75.566667% displays as 75.57%).
Formatting changes presentation only; stored metrics and scoring remain unchanged.

Actions have visible button borders, backgrounds and hover/focus/disabled states.
Underlined metric labels expose explanatory tooltips on hover or keyboard focus;
Escape dismisses them. Shared descriptions cover absolute/relative gap, observed
score, best-so-far, evaluations, reference data, convergence, Pareto and p95.
Absolute gap includes its formula and a numerical example in score points.
Understand the score displays the weighted formula `Score = w1 × q1 + … + wn × qn`
with the actual effective weights, followed by each quality normalization formula.
`mi` denotes the mean metric in original units. MinMax includes direction inversion
and clamping; reciprocal uses the recorded scale. Missing structured objectives
keep the metadata warning instead of presenting an assumed formula as the run objective.

Pareto and trajectory are off by default. They require a single context. Follow
decision path highlights incoming and outgoing transitions around the selected
decision, with arrows and decision numbers. Previous/current/next cards allow
selection even for coincident points. The next decision is retrospective, not a
prediction. The complete path is opt-in and faint; reference points have reduced
emphasis while following the path. Distances in the success/latency plot
are not the configuration distances used by KNN. All evaluated result points remain
available with the trajectory disabled. Context filters do not change aggregate scores.

Compare Runs leads with stepwise convergence and a five-column summary. Reference
milestones and result-space exploration are collapsed. Export analysis downloads
JSON with normalized runs, objective, decisions, metrics and comparison results;
convergence charts can also be exported as PNG. Individual runs do not demonstrate
statistical superiority. Grouping repeated seeds with confidence intervals remains
a future research extension; no statistical intervals are inferred from single runs.

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

Start with Result for the outcome and search progress, then open Investigate search for decision explanations and context-specific metrics. Compare Runs evaluates multiple runs of the same experimental problem.

## Result summary

The initial summary shows best observed score, evaluated/total configurations
with explored percentage, and the absolute gap to a compatible reference.
Executed scenarios and cache hits appear as secondary provenance. Evaluated
configurations are decisions with an official aggregated score; selected but
unfinished configurations are not counted as evaluated.

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

Structured traces carry the objective metrics, effective weights and normalization. The reference uses these recorded semantics. The legacy formula above is only a fallback for traces without a structured objective. Shared configurations are checked against `aggregatedResult.score`; mismatches mark the reference incompatible.

For a compatible reference under maximize semantics:

```text
absolute gap = reference best - heuristic best
relative gap = (reference best - heuristic best) / reference best * 100
```

Relative gap and percentage milestones are unavailable when the reference best is non-positive. For positive references, quality milestones are 90%, 95%, and 99%; each reports the first decision reaching `reference best * threshold`, or `Not reached`.

Aggregate comparison is valid only when every exhaustive configuration covers every workload × fault context expected by the heuristic run. Incomplete coverage suppresses Reference Best, gaps, and quality thresholds.

## By Operational Context

The secondary table is limited to contexts present in both datasets and remains available even when aggregate reference coverage is incomplete. It compares the best Checkout Success Rate (higher is better) and best p95 Iteration Duration (lower is better) independently. It does not synthesize a context score.

## Result Space

Result Space keeps the current metrics:

- X axis: Checkout Success Rate, where higher is better
- Y axis: p95 Iteration Duration (ms), where lower is better

The preferred direction is therefore toward the lower-right region. Reference points are shown with low emphasis. Initial Sample and Adaptive Search points use different visual styles, the selected decision has a larger ringed marker, and new-best decisions use a separate marker.

Operational Context filters affect Result Space so that workload and fault-rate combinations are not mixed in one scatter view.

When an operational context is selected, the visualizer also draws the Pareto frontier of the exhaustive reference: success rate is maximized and p95 duration is minimized. Heuristic points that match a Pareto configuration are highlighted, indicating that the heuristic evaluated that reference-frontier configuration. The frontier is not drawn for All operational contexts because combining contexts would make the comparison misleading.

## Show Search Path

Show Search Path connects evaluated heuristic configurations in the order selected by the heuristic for the active operational context. Reference-space configurations are not part of this path. If a decision has no result point for the current workload/fault filter, it is skipped safely instead of inventing coordinates.

## Operational Context

A configuration can produce scenario results across multiple operational contexts, such as workload users multiplied by fault rate. If only one workload and one fault rate exist, the UI shows a compact label. If multiple values exist, dropdown filters are shown for Workload and Fault Rate.

Changing these filters affects Result Space and context-specific result views. It does not change Search Progress, decision order, or Best Score So Far, because those are configuration-level heuristic values.

## Search Progress

Search Progress shows:

- Observed Score: the score obtained by evaluating the selected configuration
- Best Score So Far: the best score known after that evaluation

The chart labels phase ranges from the trace. Initial Sample decisions provide the first observations for adaptive search; RandomSampling is explicitly labeled as a random sample. The running best is a step line and observations are unconnected points.

## Decision Timeline

The compact navigation selects a decision by number and provides Previous, Next,
Play/Pause and Next improvement. The shared selection updates Result Space,
Search Progress and Decision Details. It does not change the final run summary.

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
