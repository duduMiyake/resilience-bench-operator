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

After preparing storage and the three images described below, apply the CRDs
and the overlay. For a pilot, render and reduce the Benchmark budget **before**
applying it; the sample is not limited to the few checks listed below.

```bash
kubectl apply -f ./crd
kubectl apply -k ./samples/overlays/hipstershop
```

The overlay intentionally does not set `nodeSelector`, `affinity`, or
`K6_NODE_SELECTOR`. Kubernetes can therefore schedule the application,
operator, and k6 jobs on any schedulable worker, including Vagrant clusters
whose workers have no custom labels.

Use the paired development images or verified registry digests, as described below.

To pin workloads to particular nodes, label those nodes and create a dedicated
overlay that adds compatible `nodeSelector` or affinity rules. Do not add
cluster-specific node names or labels back to this portable overlay.

## Piloto com Shipping

Este overlay substitui a configuracao da campanha, sem outro modo de execucao.
Os tres conectores variaveis fornecem 25 × 25 × 9 configuracoes nominais.
Frontend → Shipping fica fixo, inicialmente sem uma politica explicita de retry;
ajuste esse valor apenas antes da campanha. O objetivo segue
`checkout_success_rate` e `iteration_duration_p(95)`; os contadores da jornada
completa e `journey_success_rate` sao diagnosticos. Se houve jornadas, mas
nenhuma chegou ao checkout, o score recebe taxa zero e `checkout_attempted: 0`
distingue esse caso de falhas em checkouts realizados. Resultados antigos nao
sao intercambiaveis com o novo perfil de carga e falha.

As tags abaixo sao locais, nao imagens publicadas. Para reproduzir a
adaptacao, use o [patch versionado](online-boutique-destination-retry.patch)
sobre o commit exato da Online Boutique. A partir da raiz do Operator:

```bash
repository_root="$PWD"
git clone https://github.com/cmendesce/microservices-demo.git ../resiliencebench-boutique
git -C ../resiliencebench-boutique switch --detach 6f409a642c44a6cb3a1a16cfeca61e65b88f2bd7
git -C ../resiliencebench-boutique apply --check "$repository_root/samples/overlays/hipstershop/online-boutique-destination-retry.patch"
git -C ../resiliencebench-boutique apply "$repository_root/samples/overlays/hipstershop/online-boutique-destination-retry.patch"
docker build -t resiliencebench/frontend:destination-retry-v1 ../resiliencebench-boutique/src/frontend
docker build -t resiliencebench/checkoutservice:destination-retry-v1 ../resiliencebench-boutique/src/checkoutservice
mvn -f resilience-bench/operator/pom.xml -Dimage=resiliencebench/operator:destination-retry-v1 package
```

Carregue as tres imagens nos workers do cluster de teste ou substitua seus
nomes por digests de imagens verificadas em seu registry. Nao execute com as
imagens antigas: elas nao leem as novas variaveis.

Em namespace dedicado, com fila e prefixo de cache novos, execute primeiro
um recorte pequeno: verifique Payment com duas tentativas e Shipping com
cinco, depois Payment sem retry e Shipping com cinco. Valide as janelas em
cada alvo separadamente antes de combinar os alvos, e confirme que o
Envoy aplica e restaura a falha observada pelo cliente. Os tempos no manifesto
sao valores iniciais de piloto. No caso de falha apos o pagamento, conte chamadas
de `PlaceOrder` e `Charge` para detectar uma eventual repeticao do pagamento;
nao deduza essa atribuicao a partir de um erro HTTP no k6.
