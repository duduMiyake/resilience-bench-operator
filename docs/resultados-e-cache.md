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

O cache/replay nao coloca S3 dentro das heuristicas. A heuristica escolhe o cenario; depois o executor tenta buscar o resultado daquele cenario no cache.

## Comportamento readWrite

Com `mode: readWrite`:

1. a estrategia escolhe o proximo cenario;
2. o executor calcula o hash da configuracao do cenario;
3. tenta ler `/results/cache/<benchmarkName>/<scenarioHash>.json`;
4. se encontrar, agrega o resultado na rodada atual e marca o item como finalizado;
5. se nao encontrar, executa o cenario normalmente;
6. ao final da execucao real, grava o resultado no agregado da rodada e no cache.

Assim, a avaliacao continua honesta: a heuristica so aprende com resultados dos cenarios que ela ja escolheu.

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

Com cache habilitado, cada rodada tambem pode gerar um `trace.json` no diretorio da run. Ele registra o caminho escolhido pela estrategia:

```json
{
  "benchmark": "onlineboutique",
  "strategy": "knnAdaptive",
  "runId": "2026-07-20-10-30-00",
  "resultFile": "/results/runs/heuristics/onlineboutique/knnAdaptive/2026-07-20-10-30-00/results.json",
  "steps": [
    {
      "step": 1,
      "phase": "initialSample",
      "scenario": "scenario-1",
      "scenarioHash": "...",
      "source": "cacheHit",
      "resultScore": 0.82
    }
  ]
}
```

Esse arquivo deve servir como base para uma visualizacao futura em grafo, onde cada step representa um cenario escolhido e a ordem dos steps representa o caminho percorrido pela heuristica.

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

