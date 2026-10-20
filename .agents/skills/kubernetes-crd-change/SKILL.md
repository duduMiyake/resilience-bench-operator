---
name: kubernetes-crd-change
description: Use when changing Kubernetes custom resource specs, CRD schemas, reconciliation, status, generated manifests, or sample resources in ResilienceBench-Operator.
---

# Kubernetes CRD changes

Read [the resource guide](../../../docs/kubernetes-resources.md), [the architecture guide](../../../docs/architecture.md), and the relevant instructions in `AGENTS.md`.

For a resource change, trace the field through its Java model, validation/defaulting, controller or executor consumers, generated/versioned CRD, and YAML samples. Keep existing fields optional and compatible unless the requested change includes a migration. Preserve status semantics and queue lifecycle when modifying reconciliation or execution.

Use the Fabric8 models and `CustomResourceRepository` patterns already present. Add or update focused unit/serialization tests for model changes; test controller/execution behavior at the existing seams. Update `docs/kubernetes-resources.md` or the relevant functional guide when the user-visible contract changes.

Applying manifests or running cluster tests can mutate cluster state. Inspect the active `kubectl` context and namespace before any such operation; proceed only when the task authorizes that target.
