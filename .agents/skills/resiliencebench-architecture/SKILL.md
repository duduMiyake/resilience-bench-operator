---
name: resiliencebench-architecture
description: Use when planning or changing ResilienceBench-Operator architecture, execution flow, module boundaries, or domain behavior.
---

# ResilienceBench architecture

Use this skill for architecture-impacting changes in this repository. Read [the architecture guide](../../../docs/architecture.md), [the domain model](../../../docs/domain-model.md), and the relevant sections of `AGENTS.md` before proposing edits.

Trace behavior from the entry point through callers and persisted resources. Keep the current boundaries between reconciliation (`BenchmarkController`), scenario generation/domain (`resources`), selection (`resources/selection`), queue/scenario execution (`execution`), and result storage (`execution/resultcache` and `execution/io`). Prefer extending an existing seam over introducing a new layer.

Preserve these domain rules unless the task explicitly changes them:

- `Scenario` is the unit executed; adaptive selection chooses resilience configurations.
- Workload/failure contexts remain fixed while strategies prioritize resilience configurations within them.
- Queue state and result/trace formats are consumed by other project components; account for compatibility when changing them.

When behavior changes, update the functional document that owns that behavior. Record an ADR only for a durable decision with a meaningful trade-off. Do not turn this skill into a mandatory architecture review for local changes that do not cross a boundary.
