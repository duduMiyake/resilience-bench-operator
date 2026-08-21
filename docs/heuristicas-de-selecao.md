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

## Conceitos principais

`Scenario` e a unidade concreta executada pelo operador. Ele combina workload/carga, taxa de falha e a configuracao aplicada aos connectors.

`ResilienceConfigurationKey` e a unidade de decisao das estrategias. Ela normaliza apenas os connectors e suas configuracoes de resiliencia, como envs de retry, Istio retry/timeout/circuit breaker ou a ausencia de configuracao no baseline.

`HeuristicDecision` e o registro de uma escolha feita pela estrategia. Uma decisao seleciona uma configuracao; essa configuracao pode gerar varios scenarios quando existem multiplas cargas ou taxas de falha. A configuracao so e considerada avaliada quando todos esses scenarios esperados terminam ou sao recuperados do cache.
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

Exemplo: se existem 100 configuracoes e `sampleRate: 0.25`, entram 25 configuracoes na selecao. Cada uma gera todos os seus scenarios operacionais obrigatorios. Se tambem existir `maxConfigurations: 10`, entram 10 configuracoes.

A escolha nao muda entre execucoes iguais, porque o embaralhamento usa uma seed deterministica.

## k-NN adaptive

`knnAdaptive` e uma estrategia adaptativa baseada nos k vizinhos mais proximos. Ela usa a ideia de que configuracoes de resiliencia parecidas tendem a ter resultados agregados parecidos.

O fluxo e:

1. escolhe `initialSamples` configuracoes iniciais usando amostragem espalhada pelo espaco de configuracoes;
2. executa todos os scenarios operacionais dessas configuracoes e agrega seus scores reais;
3. para cada configuracao ainda nao avaliada, calcula a distancia ate as configuracoes ja avaliadas;
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

- `score real`: score agregado de uma configuracao cujos scenarios operacionais foram concluidos;
- `score previsto`: estimativa de score para uma configuracao candidata ainda nao avaliada.

O `score real` vem das metricas do resultado e segue o `objective` do benchmark. Para novos
experimentos, use o formato estruturado e normalize metricas com unidades ou magnitudes
diferentes antes de combina-las:

```yaml
objective:
  metrics:
    - name: checkout_success_rate
      direction: maximize
      weight: 0.5
      normalization:
        type: minMax
        min: 0
        max: 1
    - name: iteration_duration_p(95)
      direction: minimize
      weight: 0.5
      normalization:
        type: reciprocal
        scale: 22450
```

Os pesos sao positivos e normalizados internamente pela soma. `minMax` transforma um
valor entre limites fixos em qualidade entre `0` e `1` (invertendo a direcao quando
necessario e limitando valores fora dos limites). `reciprocal`, disponivel inicialmente
para metricas minimizadas, calcula:

```text
qualidade = scale / (scale + valor)
score_real = soma(peso_efetivo * qualidade_normalizada)
```

Assim, o score de configuracao permanece em `[0, 1]` e uma latencia em milissegundos nao
domina uma taxa de sucesso. No HipsterShop, `22450 ms` e uma calibracao fixa para os
experimentos; nao e derivada dinamicamente durante a execucao nem convertida para segundos.

O formato antigo continua aceito para compatibilidade:

```yaml
objective:
  maximize: [successRate]
  minimize: [p95Latency]
```

Ele e explicitamente legado, emite um aviso e conserva a soma assinada bruta
(`soma(maximize) - soma(minimize)`). Nao misture `metrics` com `maximize`/`minimize`.
Metricas configuradas ausentes falham explicitamente; nao sao substituidas por `0` nem por
outro alias.

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

O `score_previsto` usa os mesmos scores normalizados dos vizinhos. O `score_de_escolha` e
uma prioridade de aquisicao: o bonus de exploracao usa a distancia do espaco de configuracao
e pode fazer esse valor ultrapassar `1`. Ele nao e um score de desempenho e nao deve ser
limitado ao intervalo `[0, 1]`.

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

Com `initialSamples: 20` e `maxEvaluations: 100`, a ferramenta seleciona primeiro 20 configuracoes espalhadas pelo espaco. Cada configuracao selecionada gera todos os seus scenarios obrigatorios de carga x falha. Depois disso, a estrategia escolhe novas configuracoes usando os resultados agregados ja coletados.

## Cache/replay e trace

As estrategias continuam escolhendo configuracoes da mesma forma, mesmo quando `spec.resultCache` esta habilitado. O cache entra apenas depois da escolha:

1. a estrategia escolhe uma configuracao;
2. o executor adiciona todos os scenarios dessa configuracao a fila;
3. para cada scenario, o executor procura o resultado no cache estavel por `scenarioHash`;
4. em caso de cache hit, o resultado e agregado na rodada atual sem executar k6/fault injection;
5. em caso de cache miss, o scenario e executado normalmente e o resultado passa a popular o cache.

Isso preserva a avaliacao honesta das heuristicas: elas nao consultam resultados de configuracoes ainda nao escolhidas.

Toda rodada de estrategia heuristica registra um `trace.json`, mesmo quando o cache esta desabilitado. O trace usa `schemaVersion: 2` e registra decisoes por configuracao, eventos de conclusao de scenarios, resultados agregados por configuracao e metadata especifica da heuristica. Para `knnAdaptive`, essa metadata inclui `predictedScore`, `uncertainty`, `explorationBonus`, `selectionScore` e `nearestNeighbors`, calculados pela estrategia no momento real da selecao.

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

`knnAdaptive` escolhe uma amostra inicial espalhada e depois prioriza configuracoes com melhor score previsto, com um bonus configuravel para exploracao.
