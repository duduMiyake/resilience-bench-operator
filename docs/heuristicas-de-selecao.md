# Heuristicas de selecao de cenarios

O ResilienceBench-Operator e uma ferramenta Kubernetes-native implementada como um operador/controller com CRDs.

Este documento explica, de forma pratica, como a ferramenta escolhe quais cenarios de um `Benchmark` entram na fila de execucao. A configuracao fica em `Benchmark.spec.strategy`. Quando ela nao e informada, a estrategia usada e `exhaustive`.

## Fluxo geral

O fluxo de selecao e execucao funciona assim:

1. O `BenchmarkController` recebe um `Benchmark`.
2. O `ScenarioFactory` expande todas as combinacoes possiveis declaradas no benchmark.
3. O `ScenarioSelectionStrategySelector` escolhe a estrategia definida em `spec.strategy.type`.
4. A estrategia seleciona os cenarios iniciais que entram na `Queue`.
5. O `DefaultQueueExecutor` executa os itens da fila.
6. Se a estrategia for adaptativa, como `knnAdaptive`, novos cenarios podem ser anexados a fila depois que resultados anteriores ja foram coletados.

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

`randomSampling` gera todos os cenarios possiveis, embaralha a lista com uma seed e escolhe apenas uma parte.

Na implementacao, o calculo e:

```text
sampleSize = ceil(totalScenarios * sampleRate)
```

Depois, se `maxScenarios` estiver definido, ele limita o resultado:

```text
sampleSize = min(sampleSize, maxScenarios)
```

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

- `neighbors`: quantidade de vizinhos avaliados usados na previsao. Default: `3`.
- `explorationWeight`: peso do bonus para regioes pouco exploradas. Default: `0.1`.

### Como o k-NN calcula distancia

No `knnAdaptive`, os vizinhos sao sempre cenarios ja avaliados. Para saber quais sao os mais proximos, a ferramenta compara os parametros do candidato com os parametros dos cenarios que ja rodaram.

Primeiro, cada cenario vira um vetor numerico com campos como:

```text
workload.users
fault.percentage
connector.retry-frontend-checkout.source.env.GRPC_MAX_ATTEMPTS
connector.retry-frontend-checkout.source.env.GRPC_INITIAL_BACKOFF
connector.retry-checkout-payment.source.env.GRPC_BACKOFF_MULTIPLIER
```

Depois, cada parametro e normalizado entre `0` e `1` dentro do espaco total de cenarios. Isso evita que um parametro com numero maior domine a comparacao so por causa da escala.

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

Quanto menor a distancia, mais parecido o cenario avaliado e com o candidato. A implementacao atual nao define pesos diferentes por parametro: todos os parametros normalizados entram com o mesmo peso.

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

A formula usada para escolher o proximo cenario e:

```text
score_de_escolha = score_previsto + explorationWeight * incerteza
```

Onde:

```text
score_previsto = media ponderada dos scores reais dos k vizinhos mais proximos
incerteza = distancia ate o cenario avaliado mais proximo
```

Se `explorationWeight` for `0`, a estrategia escolhe apenas pelo score previsto. Valores maiores fazem a estrategia dar mais chance para candidatos que estao longe do que ja foi testado.

Exemplo com `neighbors: 3`: depois de executar 20 cenarios iniciais, a ferramenta compara cada candidato restante com esses 20 resultados, pega os 3 mais parecidos, calcula o score previsto e guarda. Depois de fazer isso para todos os candidatos, executa o candidato com maior score de escolha. Quando esse teste termina, ele entra na lista de avaliados e o processo se repete com 21 resultados.

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
4 * 3 * 1 * 3 = 36 configuracoes
```

Como existem dois connectors combinados:

```text
36 * 36 = 1296 cenarios
```

Com `initialSamples: 20` e `maxEvaluations: 100`, a ferramenta executa primeiro 20 cenarios espalhados pelo espaco e depois escolhe mais 80, um por vez, usando os resultados ja coletados.

## Resumo

`exhaustive` executa tudo.

`randomSampling` escolhe uma amostra fixa e reprodutivel.

`knnAdaptive` escolhe uma amostra inicial espalhada e depois prioriza cenarios parecidos com os melhores resultados ja observados, com um bonus configuravel para exploracao.
