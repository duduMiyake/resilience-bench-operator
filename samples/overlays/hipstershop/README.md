# Hipster Shop overlay

Apply the CRDs and this overlay with:

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