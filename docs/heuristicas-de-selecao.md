# Heuristicas de selecao de cenarios

O ResilienceBench-Operator e uma ferramenta Kubernetes-native implementada como um operador/controller com CRDs.

Este documento explica, de forma pratica, como a ferramenta escolhe quais cenarios de um `Benchmark` entram na fila de execucao. A configuracao fica em `Benchmark.spec.strategy`. Quando ela nao e informada, a estrategia usada e `exhaustive`.

## Fluxo geral

O fluxo de selecao e execucao funciona assim:

1. O `BenchmarkController` recebe um `Benchmark`.
2. O `ScenarioFactory` expande todas as combinacoes possiveis declaradas no benchmark.
3. Os cenarios sao agrupados por `ResilienceConfigurationKey`, que representa apenas a configuracao de resiliencia.
4. O `ScenarioSelectionStrategySelector` escolhe a estrategia definida em `spec.strategy.type`.
5. A estrategia seleciona configuracoes de resiliencia.
6. Cada configuracao selecionada e expandida para todos os cenarios concretos de carga x falha associados.
7. O `DefaultQueueExecutor` executa os itens da fila, ainda como `Scenario`.
8. Se a estrategia for adaptativa, como `knnAdaptive`, novas configuracoes podem ser anexadas a fila depois que todos os cenarios da configuracao anterior terminarem e forem agregados.

`Scenario` continua sendo a unidade de execucao. A unidade de decisao das heuristicas passa a ser a configuracao de resiliencia. Isso impede que uma heuristica escolha ou descarte apenas uma combinacao especifica de carga ou taxa de falha.

As estrategias implementadas hoje sao:

- `exhaustive`
- `randomSampling`
- `knnAdaptive`

## Exhaustive

`exhaustive` executa todos os cenarios gerados pelo `ScenarioFactory`.

Na implementacao, ela simplesmente retorna a lista completa `allScenarios`. Portanto, se o benchmark gerar 100 cenarios, os 100 entram na `Queue`.

Use quando:

- o espaco de busca e pequeno;
- voce quer cobertura completa;
- voce quer reproduzir o comportamento padrao da ferramenta.

## Random sampling

`randomSampling` gera todos os cenarios possiveis, agrupa por configuracao de resiliencia, embaralha as configuracoes com uma seed e escolhe apenas uma parte.

Na implementacao, o calculo e:

```text
sampleSize = ceil(totalConfigurations * sampleRate)
```

Depois, se `maxConfigurations` estiver definido, ele limita o resultado:

```text
sampleSize = min(sampleSize, maxConfigurations)
```

`maxScenarios` ainda funciona como fallback temporario para YAMLs antigos, mas a semantica recomendada para heuristicas e usar `maxConfigurations`.

Defaults:

- `seed`: `42`
- `sampleRate`: `0.5`, quando `sampleRate` e `maxScenarios` nao sao informados

Exemplo: se existem 100 cenarios e `sampleRate: 0.25`, entram 25 cenarios na fila. Se tambem existir `maxScenarios: 10`, entram 10.

A escolha nao muda entre execucoes iguais, porque o embaralhamento usa uma seed deterministica.

## k-NN adaptive

`knnAdaptive` e uma estrategia adaptativa baseada nos k vizinhos mais proximos. Ela usa a ideia de que cenarios com configuracoes parecidas tendem a ter resultados parecidos.

O fluxo e:

1. escolhe `initialSamples` iniciais usando amostragem espalhada pelo espaco de configuracoes;
2. executa esses cenarios e coleta os scores reais;
3. para cada cenario ainda nao executado, calcula a distancia ate os cenarios ja avaliados;
4. pega os `neighbors` avaliados mais proximos;
5. estima o score do candidato pela media ponderada dos scores reais desses vizinhos;
6. soma um bonus de exploracao;
7. escolhe o candidato com maior score de escolha.

Parametros:

- `maxConfigurations`: quantidade maxima de configuracoes avaliadas. Se ausente, `maxEvaluations` ou `maxScenarios` sao usados como fallback temporario.
- `neighbors`: quantidade de vizinhos avaliados usados na previsao. Default: `3`.
- `explorationWeight`: peso do bonus para regioes pouco exploradas. Default: `0.1`.

### Como o k-NN calcula distancia

No `knnAdaptive`, os vizinhos sao sempre configuracoes ja avaliadas. Para saber quais sao as mais proximas, a ferramenta compara os parametros da configuracao candidata com os parametros das configuracoes que ja foram avaliadas por completo.

Primeiro, cada configuracao vira um vetor numerico. Carga e taxa de falha nao entram nesse vetor. Exemplos de campos:

```text
workload.users
fault.percentage
connector.retry-frontend-checkout.source.env.GRPC_MAX_ATTEMPTS
connector.retry-frontend-checkout.source.env.GRPC_INITIAL_BACKOFF
connector.retry-checkout-payment.source.env.GRPC_BACKOFF_MULTIPLIER
```

Depois, cada parametro e normalizado entre `0` e `1` dentro do espaco total de configuracoes. Isso evita que um parametro com numero maior domine a comparacao so por causa da escala.

Exemplo:

```text
GRPC_MAX_ATTEMPTS: valores 2, 3, 4, 5

2 vira 0.00
3 vira 0.33
4 vira 0.66
5 vira 1.00
```

Com os vetores normalizados, a distancia usada e a distancia euclidiana:

```text
distancia = sqrt(soma((valor_candidato - valor_avaliado)^2))
```

Exemplo simples:

```text
Candidato:
  attempts = 0.00
  backoff = 0.50

Cenario ja avaliado:
  attempts = 1.00
  backoff = 0.50

distancia = sqrt((0.00 - 1.00)^2 + (0.50 - 0.50)^2)
distancia = 1.00
```

Quanto menor a distancia, mais parecida a configuracao avaliada e com a candidata. A implementacao atual nao define pesos diferentes por parametro: todos os parametros normalizados entram com o mesmo peso.

### Como o k-NN calcula o score

O k-NN usa dois tipos de score:

- `score real`: score de um cenario que ja foi executado;
- `score previsto`: estimativa de score para um candidato que ainda nao foi executado.

O `score real` vem das metricas do resultado e segue o `objective` do benchmark. Por exemplo:

```yaml
objective:
  maximize:
    - successRate
  minimize:
    - p95Latency
```

Nesse caso:

```text
score_real = successRate - p95Latency
```

Para um candidato ainda nao executado, o k-NN:

1. calcula a distancia ate todos os cenarios ja avaliados;
2. pega os `neighbors` mais proximos;
3. calcula o peso de cada vizinho;
4. faz uma media ponderada dos scores reais desses vizinhos.

O peso e maior para vizinhos mais proximos:

```text
peso = 1 / (distancia + 0.000001)
```

O score previsto e:

```text
score_previsto = soma(peso * score_real_do_vizinho) / soma(peso)
```

Exemplo com `neighbors: 3`:

```text
Vizinho A:
  distancia = 0.10
  score_real = 0.80
  peso aproximado = 10

Vizinho B:
  distancia = 0.50
  score_real = 0.60
  peso aproximado = 2

Vizinho C:
  distancia = 1.00
  score_real = 0.20
  peso aproximado = 1

score_previsto = (10 * 0.80 + 2 * 0.60 + 1 * 0.20) / (10 + 2 + 1)
score_previsto = 0.72
```

Como o vizinho A e o mais parecido, ele influencia mais a previsao.

A formula usada para escolher a proxima configuracao e:

```text
score_de_escolha = score_previsto + explorationWeight * incerteza
```

Onde:

```text
score_previsto = media ponderada dos scores reais dos k vizinhos mais proximos
incerteza = distancia ate a configuracao avaliada mais proxima
```

Se `explorationWeight` for `0`, a estrategia escolhe apenas pelo score previsto. Valores maiores fazem a estrategia dar mais chance para candidatos que estao longe do que ja foi testado.

Exemplo com `neighbors: 3`: depois de avaliar 20 configuracoes iniciais, a ferramenta compara cada candidato restante com esses 20 resultados, pega os 3 mais parecidos, calcula o score previsto e guarda. Depois de fazer isso para todas as configuracoes candidatas, escolhe a configuracao com maior score de escolha. O executor adiciona todos os cenarios carga x falha dessa configuracao. So quando todos terminam, o resultado agregado da configuracao entra na lista de avaliados e o processo escolhe a proxima configuracao.

## Exemplo com o benchmark Hipster Shop

No arquivo `samples/overlays/hipstershop/benchmark.yaml`, o benchmark atual usa:

- workload `k6-loadtest` com `users: [300]`;
- falha Envoy com `percentages: [50]`;
- dois connectors com parametros de retry:
  - `retry-frontend-checkout`;
  - `retry-checkout-payment`.

Cada connector varia:

- `GRPC_MAX_ATTEMPTS`: 4 valores (`2`, `3`, `4`, `5`);
- `GRPC_INITIAL_BACKOFF`: 3 valores (`0.5s`, `1s`, `1.5s`);
- `GRPC_MAX_BACKOFF`: 1 valor (`15s`);
- `GRPC_BACKOFF_MULTIPLIER`: 3 valores (`1`, `1.5`, `2.0`).

Cada connector gera:

```text
4 * 3 * 1 * 3 = 36 configuracoes de retry
```

Quando `includeBaseline: true` esta habilitado no connector, o `ScenarioFactory` adiciona mais uma opcao sem configuracao de resiliencia para esse connector. Essa opcao representa o baseline daquele caminho, por exemplo `frontendservice -> checkoutservice` sem retry.

Com dois connectors e baseline habilitado nos dois:

```text
(36 + 1) * (36 + 1) = 1369 cenarios
```

Assim, o espaco de busca inclui tanto combinacoes com retry quanto casos em que um ou ambos os connectors ficam sem retry. Isso permite que estrategias como `exhaustive` e `knnAdaptive` comparem configuracoes de retry contra o baseline dentro da mesma rodada.

Com `initialSamples: 20` e `maxEvaluations: 100`, a ferramenta executa primeiro 20 cenarios espalhados pelo espaco e depois escolhe mais 80, um por vez, usando os resultados ja coletados.

## Cache/replay e trace

As estrategias continuam escolhendo cenarios da mesma forma, mesmo quando `spec.resultCache` esta habilitado. O cache entra apenas depois da escolha:

1. a estrategia escolhe o cenario;
2. o executor procura o resultado no cache estavel por `scenarioHash`;
3. em caso de cache hit, o resultado e agregado na rodada atual sem executar k6/fault injection;
4. em caso de cache miss, o cenario e executado normalmente e o resultado passa a popular o cache.

Isso preserva a avaliacao honesta das heuristicas: elas nao consultam resultados de cenarios ainda nao escolhidos.

Com cache habilitado, a rodada tambem registra um `trace.json` no diretorio da run. Esse trace guarda a ordem dos cenarios escolhidos, o hash do cenario, a origem do resultado (`cacheHit` ou `executed`) e o score calculado a partir do resultado. Ele deve ser usado futuramente para visualizacoes em grafo do caminho percorrido pela heuristica.

Exemplo de configuracao:

```yaml
spec:
  strategy:
    type: knnAdaptive
    initialSamples: 20
    maxEvaluations: 100
  resultCache:
    enabled: true
    mode: readWrite
    cachePrefix: /results/cache
    runsPrefix: /results/runs
```
## Resumo

`exhaustive` executa tudo.

`randomSampling` escolhe uma amostra fixa e reprodutivel.

`knnAdaptive` escolhe uma amostra inicial espalhada e depois prioriza cenarios parecidos com os melhores resultados ja observados, com um bonus configuravel para exploracao.


