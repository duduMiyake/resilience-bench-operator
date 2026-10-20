# Hipster Shop overlay

## Storage prerequisites

This overlay currently runs the operator with `STORAGE_TYPE: cloud`. Before starting it, replace `REPLACE_WITH_YOUR_S3_BUCKET` and set `AWS_REGION` and `AWS_S3_PREFIX` in an environment-specific overlay or patch. Provide a reachable S3 bucket with credentials authorized to read and write the configured prefix. Keep environment-specific values in ignored local configuration; the versioned bucket value is a placeholder and must be replaced before deployment.

Provide a Secret named `aws-credentials` in namespace `hipstershop` with keys `access-key-id` and `secret-access-key`. The operator Deployment references it; the generated k6 Jobs reference it in their own namespace too. The overlay does not create this Secret or provision a bucket. Keep credential values outside versioned manifests.

The storage flow is:

1. `K6JobFactory` passes `OUTPUT_PATH`, `VIRTUAL_USERS`, workload options and, in cloud mode, AWS settings and Secret references to the Job.
2. `k6.js` runs the load and its `handleSummary` writes raw metrics directly to S3, using `OUTPUT_PATH` and `AWS_S3_PREFIX` for the object key.
3. The operator's `ResultFileStep` reads that object through `S3FileProvider`, adds scenario metadata and writes item, aggregate, trace and cache artifacts as applicable. Both producers must use the same bucket and prefix.

Setting `STORAGE_TYPE` to local alone does not make this sample local: its script always uploads to S3. A local variant needs a script that returns the summary JSON at `OUTPUT_PATH` and the `test-results` PVC mounted at `/results` in both the operator and k6 Jobs. The Job factory uses that PVC name in local mode; this overlay currently configures the cloud path. This README describes the existing sample, not a ready-made local storage variant.

## Apply

See the [experiment guide](../../../docs/running-experiments.md) for the pause/cleanup sequence, result collection and information needed to reproduce a study.

After preparing these prerequisites, apply the CRDs and this overlay with:

```bash
kubectl apply -f ./crd
kubectl apply -k ./samples/overlays/hipstershop
```

The overlay intentionally does not set `nodeSelector`, `affinity`, or
`K6_NODE_SELECTOR`. Kubernetes can therefore schedule the application,
operator, and k6 jobs on any schedulable worker, including Vagrant clusters
whose workers have no custom labels.

The operator image defaults to
`eduardomiyake/resiliencebench-operator:scenario-selection`, which is pulled
from a registry. For a local image, create a separate local overlay or patch
the deployment image after loading it into the cluster.

To pin workloads to particular nodes, label those nodes and create a dedicated
overlay that adds compatible `nodeSelector` or affinity rules. Do not add
cluster-specific node names or labels back to this portable overlay.
