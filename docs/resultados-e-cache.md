# Resultados e cache de cenarios

Este documento descreve como os resultados sao gravados e como funciona o cache/replay de resultados por cenario.

## Fluxo legado

Quando `spec.resultCache` esta ausente ou `enabled: false`, cada `ExecutionQueue` recebe um arquivo agregado com timestamp:

```text
/results/<timestamp>-results.json
```

Cada item da fila recebe um arquivo individual derivado desse mesmo timestamp:

```text
/results/<timestamp>-<scenarioName>.json
```

Esse comportamento foi preservado para compatibilidade.

## Fluxo com resultCache readWrite

Quando o benchmark habilita cache:

```yaml
spec:
  resultCache:
    enabled: true
    mode: readWrite
    cachePrefix: /results/cache
    runsPrefix: /results/runs
```

os artefatos de execucao passam a ser separados por tipo de rodada.

Exhaustive:

```text
/results/runs/exhaustive/<benchmarkName>/<runId>/results.json
/results/runs/exhaustive/<benchmarkName>/<runId>/items/<scenarioName>.json
```

Heuristicas:

```text
/results/runs/heuristics/<benchmarkName>/<strategyType>/<runId>/results.json
/results/runs/heuristics/<benchmarkName>/<strategyType>/<runId>/trace.json
/results/runs/heuristics/<benchmarkName>/<strategyType>/<runId>/items/<scenarioName>.json
```

Cache estavel por cenario:

```text
/results/cache/<benchmarkName>/<scenarioHash>.json
```

No S3 esses caminhos sao object keys. A separacao evita misturar uma rodada exhaustive, uma rodada de heuristica e o cache reutilizavel.

## S3

O projeto usa a abstracao `FileProvider` para gravar e ler arquivos. Quando a configuracao seleciona S3, `S3FileProvider` transforma caminhos como `/results/...` em objetos no bucket configurado.

O cache/replay nao coloca S3 dentro das heuristicas. A heuristica escolhe uma configuracao; depois o executor tenta buscar no cache o resultado de cada scenario associado aquela configuracao.

## Comportamento readWrite

Com `mode: readWrite`:

1. a estrategia escolhe uma configuracao de resiliencia;
2. o executor adiciona todos os scenarios dessa configuracao a fila;
3. para cada scenario, calcula o `scenarioHash`;
4. tenta ler `/results/cache/<benchmarkName>/<scenarioHash>.json`;
5. se encontrar, agrega o resultado na rodada atual e marca o item como finalizado;
6. se nao encontrar, executa o scenario normalmente;
7. ao final da execucao real, grava o resultado no agregado da rodada e no cache.

Assim, a avaliacao continua honesta: a heuristica so aprende com resultados das configuracoes que ela ja selecionou.

## Chave estavel do cache

O cache nao depende de timestamp nem apenas de `scenarioName`.

A chave e um SHA-256 da configuracao normalizada do `Scenario`, incluindo:

- nome do workload;
- quantidade de usuarios;
- fault, quando existir;
- connectors;
- parametros dos patterns/envs.

O resultado salvo inclui metadados como:

- `benchmark`
- `scenario`
- `scenarioHash`
- `resultSource`, como `cacheHit` ou `executed`
- `workload_name`
- `workload_users`
- campos de fault
- `connectors`
- metricas coletadas pelo teste

## Trace da heuristica

Cada rodada de uma estrategia heuristica gera um `trace.json`, independentemente de o cache estar habilitado. O formato atual e versionado com `schemaVersion: 2` e usa a configuracao de resiliencia como unidade principal de decisao.

A diferenca entre os conceitos e:

- `Scenario`: unidade concreta de execucao, com workload/carga, fault rate e configuracao aplicada.
- `ResilienceConfiguration`: conjunto normalizado dos connectors e parametros de resiliencia. Carga e taxa de falha nao fazem parte da chave dessa configuracao.
- `HeuristicDecision`: escolha feita por uma estrategia para avaliar uma configuracao.

O trace diferencia os seguintes eventos:

- `RUN_STARTED`: o controller iniciou a run e registrou seu espaco de configuracoes.
- `CONFIGURATION_SELECTED`: a heuristica selecionou uma configuracao.
- `SCENARIO_COMPLETED`: um scenario daquela configuracao terminou ou foi recuperado do cache.
- `CONFIGURATION_EVALUATED`: todos os scenarios esperados daquela configuracao terminaram e o resultado agregado ficou disponivel.
- `RUN_COMPLETED`: o executor confirmou que nao ha item pendente nem proxima configuracao adaptativa.
- `TRACE_INCONSISTENCY`: um evento observacional chegou sem o antecedente esperado. Por exemplo, `SCENARIO_COMPLETED_WITHOUT_SELECTION`.

Todos os eventos possuem `sequence` e `timestamp`. `finishedAt` so e definido junto com `RUN_COMPLETED`; a conclusao nunca e inferida apenas porque todas as decisoes registradas ate aquele instante foram avaliadas.

`candidateCount` e a quantidade de configuracoes disponiveis imediatamente antes da selecao. `remainingConfigurations` e a quantidade ainda nao selecionada imediatamente depois dela, portanto nao inclui a configuracao recem-selecionada. No batch inicial, as decisoes usam `selectionMode: INITIAL_BATCH` e `batch: 1`; a ordem interna serve para serializacao das contagens, nao representa chamadas adaptativas separadas.

Os totais no topo seguem estas definicoes:

- `totalConfigurationSpaceSize`: configuracoes distintas possiveis na run.
- `totalConfigurationsSelected`: configuracoes que entraram no processo de avaliacao.
- `totalConfigurationsEvaluated`: configuracoes completamente agregadas.
- `totalScenariosCompleted`: completions associados a decisoes, incluindo cache e execucao real.
- `totalScenariosExecuted`: scenarios realmente executados.
- `totalCacheHits`: scenarios recuperados do cache.
- `totalTraceInconsistencies`: inconsistencias observacionais registradas.

Exemplo simplificado:
Os arrays abaixo mostram apenas a decisao adaptativa 21 e seus eventos; os totais representam a run resumida.

```json
{
  "schemaVersion": 2,
  "benchmark": "hipstershop",
  "heuristic": "knnAdaptive",
  "runId": "2026-08-01-18-20-27",
  "resultFile": "/results/runs/heuristics/hipstershop/knnAdaptive/2026-08-01-18-20-27/results.json",
  "startedAt": "2026-08-01T18:20:27Z",
  "finishedAt": "2026-08-01T18:50:00Z",
  "initialSamples": 20,
  "maxEvaluations": 50,
  "totalConfigurationSpaceSize": 1369,
  "decisions": [
    {
      "decision": 21,
      "phase": "adaptiveSelection",
      "selectionMode": "SEQUENTIAL",
      "selectedAt": "2026-08-01T18:35:10Z",
      "evaluatedConfigurations": 20,
      "candidateCount": 1349,
      "remainingConfigurations": 1348,
      "configuration": {
        "hash": "...",
        "summary": "retry-frontend-checkout:frontendservice->checkoutservice=CONFIGURED",
        "normalized": { "connectors": [] }
      },
      "selection": {
        "heuristic": "knnAdaptive",
        "metadata": {
          "predictedScore": 0.72,
          "uncertainty": 0.15,
          "explorationBonus": 0.015,
          "selectionScore": 0.735,
          "nearestNeighbors": [
            {
              "configurationHash": "...",
              "configurationSummary": "...",
              "distance": 0.1,
              "realScore": 0.8
            }
          ]
        }
      },
      "expectedScenarios": [
        {
          "scenario": "retry-checkout-300vu-50f-00001",
          "workload": { "name": "k6-loadtest", "users": 300 },
          "fault": { "provider": "envoy", "percentage": 50, "services": ["paymentservice"] }
        }
      ],
      "executions": [
        {
          "scenario": "retry-checkout-300vu-50f-00001",
          "source": "cacheHit",
          "resultScore": 0.61,
          "completedAt": "2026-08-01T18:35:11Z"
        }
      ],
      "aggregatedResult": {
        "score": 0.61,
        "bestScoreSoFar": 0.72,
        "improvedBest": false,
        "metrics": {}
      }
    }
  ],
  "events": [
    { "sequence": 1, "type": "RUN_STARTED", "timestamp": "2026-08-01T18:20:27Z" },
    { "sequence": 2, "type": "CONFIGURATION_SELECTED", "decision": 21, "timestamp": "2026-08-01T18:35:10Z" },
    { "sequence": 3, "type": "SCENARIO_COMPLETED", "decision": 21, "timestamp": "2026-08-01T18:35:11Z" },
    { "sequence": 4, "type": "CONFIGURATION_EVALUATED", "decision": 21, "timestamp": "2026-08-01T18:35:11Z" },
    { "sequence": 5, "type": "RUN_COMPLETED", "timestamp": "2026-08-01T18:50:00Z" }
  ],
  "totalConfigurationsSelected": 21,
  "totalConfigurationsEvaluated": 21,
  "totalScenariosCompleted": 21,
  "totalScenariosExecuted": 10,
  "totalCacheHits": 11,
  "totalTraceInconsistencies": 0
}
```

A metadata especifica de cada heuristica fica dentro de `selection.metadata`. Para `knnAdaptive`, os valores de `predictedScore`, `uncertainty`, `explorationBonus`, `selectionScore` e `nearestNeighbors` sao produzidos pela propria estrategia no momento da escolha. O writer apenas serializa esses dados.

`aggregatedResult` pode conter `bestScoreSoFar`, `bestConfigurationSoFar` e `improvedBest` para as heuristicas escalares atuais. Esses campos sao opcionais no schema para permitir futuras estrategias multiobjetivo.

## Importacao de resultados antigos

O operador pode rodar um backfill de cache na inicializacao quando estas variaveis de ambiente estiverem configuradas:

```yaml
- name: RESULT_CACHE_BACKFILL_ENABLED
  value: "true"
- name: RESULT_CACHE_BACKFILL_BENCHMARK
  value: "hipstershop"
- name: RESULT_CACHE_BACKFILL_FILE
  value: "/exhaustive/results/2026-07-02-18-01-49-results.json"
- name: RESULT_CACHE_BACKFILL_CACHE_PREFIX
  value: "/results/cache"
- name: RESULT_CACHE_BACKFILL_RUNS_PREFIX
  value: "/results/runs"
```

Com `AWS_S3_PREFIX=hipstershop`, o arquivo acima e lido em:

```text
hipstershop/exhaustive/results/2026-07-02-18-01-49-results.json
```

Cada item do array `results` e gravado no cache em:

```text
hipstershop/results/cache/hipstershop/<scenarioHash>.json
```

Depois que o log indicar que o backfill terminou, remova ou desative `RESULT_CACHE_BACKFILL_ENABLED` para o operador nao tentar importar o mesmo arquivo a cada reinicio.

Resultados antigos precisam conter metadados suficientes (`workload_name`, `workload_users`, `fault` quando existir e `connectors`) para gerar uma chave equivalente aos cenarios novos.
