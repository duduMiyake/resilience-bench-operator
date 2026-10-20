# Executar e reproduzir experimentos

## Objetivo e requisitos

Este guia descreve a preparacao, execucao e coleta de artefatos do exemplo Hipster Shop em um cluster Kubernetes acessivel por `kubectl`. Ele foi elaborado a partir do codigo e dos manifests do repositorio. Os parametros de amostragem sao exemplos de uso; para reproduzir um artigo, use as configuracoes, versoes e datasets correspondentes ao estudo.

Prepare um cluster Kubernetes, a aplicacao alvo, a imagem do operador e o armazenamento S3 exigido pelo overlay. As APIs/integracoes de falhas e resiliencia usadas no benchmark precisam estar disponiveis. O [README do overlay](../samples/overlays/hipstershop/README.md) detalha bucket, Secret e integracao com k6. Provisionamento do cluster e acesso administrativo dependem do ambiente escolhido.

Execute os comandos abaixo na raiz do checkout que contem os manifests desejados, com `kubectl` configurado para o cluster do experimento. JDK/Maven e acesso a um processo de build/publicacao sao necessarios se voce for construir uma imagem propria; veja [desenvolvimento](development-setup.md). Mantenha valores de credenciais fora do repositorio.

## 1. Identificar a revisao e o cluster

```bash
pwd
git status --short --branch
git rev-parse HEAD
kubectl config current-context
kubectl cluster-info
kubectl get nodes -o wide
```

Selecione a revisao que deseja avaliar e preserve suas alteracoes locais. Registre o commit e quaisquer diferencas de configuracao antes de executar. Em uma instalacao existente, inspecione tambem `kubectl get deployment,pod,benchmark,queue,job -n hipstershop` para identificar a rodada em andamento.

Use um namespace `hipstershop` dedicado aos experimentos. O controller e a limpeza abaixo operam em cenarios/filas do namespace; nao ha isolamento suficiente para benchmarks concorrentes nele.

## 2. Confirmar codigo, imagem e requisitos

Para uma instalacao existente, confira a imagem declarada e os identificadores das imagens nos pods:

```bash
kubectl get deployment resiliencebench-operator -n hipstershop \
  -o jsonpath='{.spec.template.spec.containers[?(@.name=="resiliencebench-operator")].image}{"\n"}'
kubectl get pods -n hipstershop -l app=resiliencebench-operator \
  -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.containerStatuses[*].imageID}{"\n"}{end}'
kubectl get secret aws-credentials -n hipstershop
```

O overlay aponta atualmente para `eduardomiyake/resiliencebench-operator:scenario-selection`. Atualizar o checkout ou reiniciar o Deployment nao compila o Java. Essa tag pode apontar para uma imagem diferente do commit local. Configure `operator.yaml` para uma imagem da revisao desejada, preferencialmente com tag imutavel ou digest, e use o processo de publicacao/importacao compativel com seu cluster. Antes da primeira instalacao, prepare a imagem e os requisitos abaixo; depois do deploy, confira os identificadores nos pods.

O cluster precisa dos CRDs atuais, workloads alvo e APIs/integracoes usadas pelo experimento. Para S3, confirme bucket, regiao, prefixo e Secret `aws-credentials`, com chaves `access-key-id` e `secret-access-key`, no namespace `hipstershop`. Ver o Secret com `kubectl get secret` confirma existencia, mas nao valida permissao no bucket. O [README do overlay](../samples/overlays/hipstershop/README.md) descreve a integracao entre Job k6, script e operador. Nao copie valores de credenciais para os docs ou logs.

## 3. Escolher campanha, estrategia e politica de cache

Prepare a configuracao do operador e do benchmark:

```bash
nano samples/overlays/hipstershop/operator.yaml
nano samples/overlays/hipstershop/benchmark.yaml
```

Use `AWS_S3_PREFIX` como raiz de uma campanha, por exemplo `hipstershop/campaign-01`. Mantenha essa raiz entre exhaustive, randomSampling e knnAdaptive quando quiser reutilizar medicoes compativeis. O operador ja separa as pastas de resultados por estrategia; nao e necessario alterar essa variavel a cada troca de estrategia.

Em `Benchmark.spec.strategy`, altere os campos correspondentes sem apagar `objective`. Preserve o mesmo objetivo estruturado, pesos e normalizacao nas rodadas comparadas.

| Estrategia | Campos para configurar | Significado |
| --- | --- | --- |
| `exhaustive` | `type: exhaustive`; remova parametros exclusivos de amostragem/adaptacao | Avalia todo o espaco; nao e limitado por `maxConfigurations`. |
| `randomSampling` | `type: randomSampling`, `seed: 42`, `maxConfigurations: 20`; remova `initialSamples`, `neighbors`, `explorationWeight` e, para este exemplo, `sampleRate` | Seleciona ate 20 configuracoes, com amostra reprodutivel. |
| `knnAdaptive` | `type: knnAdaptive`, `seed: 42`, `initialSamples: 5`, `maxConfigurations: 20`, `neighbors: 3`, `explorationWeight: 0.1` | Comeca com 5 configuracoes e seleciona outras ate o orcamento de 20. |

O sample atual tem 1369 configuracoes, uma carga e uma taxa de falha. Aumentar cargas/taxas aumenta a quantidade de cenarios por configuracao. Consulte [heuristicas de selecao](heuristicas-de-selecao.md).

Para manter os caminhos estruturados de resultados, configure o cache explicitamente:

```yaml
# Dentro de spec do Benchmark
resultCache:
  enabled: true
  mode: readWrite
  cachePrefix: /results/cache
  runsPrefix: /results/runs
```

| Intencao da rodada | Politica |
| --- | --- |
| Coletar uma nova medicao | Use um `cachePrefix` novo e vazio para essa rodada, mantendo `readWrite` para produzir e salvar medicoes. |
| Comparar buscas por replay de um exhaustive | Use a mesma raiz S3 e o mesmo `cachePrefix` da referencia compativel. As heuristicas consultam apenas resultados que selecionaram. |
| Mudar script, carga/configuracao de workload, aplicacao ou infraestrutura | Separe a campanha/cache; o hash nao detecta todas essas mudancas. |

Exemplo: um exhaustive com cache inicialmente vazio em `/results/cache/reference-01` pode alimentar randomSampling e knnAdaptive usando esse mesmo prefixo. Uma repeticao para medir novamente deve usar outro prefixo vazio. Zero cenarios executados com cache hits e valido para replay, mas nao demonstra uma nova execucao da aplicacao.

Desabilitar o cache tambem evita replay, porem os arquivos de resultados voltam ao caminho legado com timestamp. Backfill de dados antigos e opcional e deve usar dados experimentalmente compativeis; mantenha `RESULT_CACHE_BACKFILL_ENABLED` desativado quando nao estiver importando. Veja [validade do cache e formatos](resultados-e-cache.md).

## 4. Encerrar a rodada anterior e aplicar a nova

Antes de limpar, registre os caminhos e preserve os artefatos da rodada anterior. Os comandos a seguir encerram qualquer execucao do namespace. Use-os somente quando a rodada puder ser encerrada e nao houver outros experimentos ou Jobs que precisem continuar em `hipstershop`.

Se o operador ja esta instalado, pause-o e espere seus pods terminarem:

```bash
kubectl scale deployment/resiliencebench-operator -n hipstershop --replicas=0
kubectl get pods -n hipstershop -l app=resiliencebench-operator
# Se houver pods do operador, aguarde a exclusao antes de continuar:
kubectl wait --for=delete pod -n hipstershop \
  -l app=resiliencebench-operator --timeout=5m
```

Se ele ja estiver sem pods, pule o `wait`. Na primeira instalacao, pule esta pausa. Em ambos os casos, so avance com o operador parado ou ainda inexistente. Uma falha/timeout nessa etapa exige verificar os pods antes da limpeza.

Limpe a declaracao anterior e os recursos de execucao do namespace. A pausa impede que o operador reutilize a fila ou recrie recursos durante a limpeza:

```bash
kubectl delete benchmark hipstershop -n hipstershop --ignore-not-found=true --wait=true
kubectl delete job --all -n hipstershop --ignore-not-found=true --wait=true
kubectl delete scenario --all -n hipstershop --ignore-not-found=true --wait=true
kubectl delete queue --all -n hipstershop --ignore-not-found=true --wait=true
kubectl get pods -n hipstershop -l app=k6
```

Espere tambem os pods k6 terminarem antes de reaplicar o ambiente. Se as APIs de recursos customizados ainda nao existirem na primeira instalacao, instale os CRDs antes de usar comandos de limpeza. Essas exclusoes nao apagam objetos S3 nem o conteudo do cache.

Confira o YAML renderizado e aplique a configuracao preparada:

```bash
kubectl kustomize samples/overlays/hipstershop
kubectl apply -f crd
kubectl apply -k samples/overlays/hipstershop
kubectl rollout status deployment/resiliencebench-operator -n hipstershop --timeout=5m
```

O overlay declara `replicas: 1`, portanto a aplicacao reativa o operador. Quando ele inicia, o Benchmark aplicado pode comecar a executar imediatamente. Nao execute essa etapa apenas para inspecionar manifests. `rollout status` confirma o Deployment do operador, nao a conclusao do experimento nem a prontidao de toda a aplicacao alvo.

Um `rollout restart` adicional normalmente nao e necessario depois dessa pausa/aplicacao. Aplicar o Benchmark com o operador ativo antes de limpar os recursos pode retomar a fila anterior. Apenas editar o Benchmark e reiniciar o operador tambem nao recria a fila existente.

## 5. Acompanhar e preservar resultados

```bash
kubectl get benchmark,queue,job -n hipstershop
kubectl get pods -n hipstershop -o wide
kubectl get queue hipstershop -n hipstershop -o yaml
kubectl logs deployment/resiliencebench-operator -n hipstershop --tail=100 -f
```

`Ctrl+C` encerra o acompanhamento de logs, nao o experimento. Confira erros de Job, leitura/escrita S3 e metricas ausentes se a fila parar de avancar. Um item `finished` sozinho nao comprova que o arquivo de resultado foi persistido; confirme os artefatos.

O caminho agregado da rodada e persistido na fila:

```bash
kubectl get queue hipstershop -n hipstershop -o jsonpath='{.spec.resultFile}{"\n"}'
```

Com `AWS_S3_PREFIX=hipstershop/campaign-01`, `cachePrefix=/results/cache` e `runsPrefix=/results/runs`, as object keys sao:

```text
hipstershop/campaign-01/results/runs/exhaustive/hipstershop/<runId>/results.json
hipstershop/campaign-01/results/runs/heuristics/hipstershop/randomSampling/<runId>/results.json
hipstershop/campaign-01/results/runs/heuristics/hipstershop/randomSampling/<runId>/trace.json
hipstershop/campaign-01/results/runs/heuristics/hipstershop/knnAdaptive/<runId>/results.json
hipstershop/campaign-01/results/runs/heuristics/hipstershop/knnAdaptive/<runId>/trace.json
hipstershop/campaign-01/results/cache/hipstershop/<scenarioHash>.json
```

`runId` e criado no processo do operador (pod), usando a hora local desse processo com precisao de segundos. Use o caminho registrado na Queue em vez de deduzir o timestamp pelo relogio de outra maquina. Preserve o YAML da fila antes de iniciar a proxima limpeza.

Baixe `trace.json` e `results.json` da heuristica e `results.json` do exhaustive de referencia usando um cliente S3 autenticado no seu ambiente. Para heuristicas, confirme `RUN_COMPLETED` e `finishedAt` no trace, resultados esperados e contadores de execucao/cache; nao infira conclusao apenas pelo rollout do Deployment. Abra os arquivos no [Visualizer](visualizer.md).

## Script auxiliar

O script [samples/startup-hipstershop](../samples/startup-hipstershop) oferece `deploy`, `restart-benchmark` e `status`, mas todos passam por `ensure_kubectl_access`, que pode trocar o contexto para `kind-resiliencebench` se esse cluster kind existir. `start` cria/prepara kind; use esse modo somente quando esse for o ambiente pretendido.

`restart-benchmark` apaga o Benchmark `hipstershop` e todos os cenarios, filas e Jobs do namespace, reaplica o overlay inteiro e nao pausa previamente o operador. Ele nao apaga o S3. Para controlar a pausa e a limpeza, use a sequencia explicita acima e confira o contexto antes de cada etapa de mutacao.

## Primeira validacao no cluster

Primeiro confirme revisao, contexto/nodes, imagem/digest e requisitos S3. Em seguida, prepare uma rodada pequena com uma unica configuracao selecionada (`randomSampling`, `maxConfigurations: 1`, seed e objetivo fixos), cache inicialmente vazio e a carga/duracao escolhidas para validar o ambiente. Remova `initialSamples` herdado de KNN: o validador compara esse campo com `maxConfigurations` mesmo para randomSampling, e o valor atual de 5 impediria esse teste de uma configuracao. So aplique depois de confirmar o alvo e poder encerrar a rodada anterior.

Confirme o ciclo completo: Job criado, resultado individual, agregado, trace concluido e leitura no Visualizer. Depois repita a mesma selecao com o mesmo cache para verificar replay. Essa segunda rodada deve ser identificada como reutilizacao de resultados. A correspondencia entre imagem e codigo, o comportamento de rollout/prontidao e o encerramento de pods sao pontos de verificacao antes de uma campanha exhaustive completa.

## Informacoes para reproducao de um estudo

Associe os artefatos de cada rodada a estas informacoes:

- Commit do operador e imagens/digests efetivamente executados, incluindo aplicacao alvo e k6.
- Versoes de Kubernetes e das integracoes usadas, quantidade de nodes, CPU/RAM, limites de recursos e regras de agendamento relevantes.
- Manifests do benchmark/workload, script de carga, estrategia, seed, orcamento, objetivo e normalizacao usados naquela rodada.
- Procedimento de preparacao/restauracao da aplicacao, condicoes de carga e falha e quantidade de repeticoes/seeds avaliadas.
- `results.json`, `trace.json` quando disponivel, identificacao da referencia exhaustive e origem de cada resultado: execucao real ou replay.

Publique configuracoes e datasets que permitam interpretar os resultados, usando placeholders para credenciais e identificadores particulares de infraestrutura. Especifique quais medicoes foram reutilizadas entre buscas; uma rodada em replay nao e uma repeticao independente da medicao. A interpretacao e os limites de comparacao estao nos guias de [heuristicas](heuristicas-de-selecao.md), [resultados/cache](resultados-e-cache.md) e [Visualizer](visualizer.md).
