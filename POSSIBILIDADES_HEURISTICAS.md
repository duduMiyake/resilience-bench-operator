# Possibilidades para heuristicas

Este arquivo funciona como backlog de ideias. A documentacao do comportamento atual fica em [docs/heuristicas-de-selecao.md](docs/heuristicas-de-selecao.md).

## Implementadas

- `exhaustive`: executa todos os cenarios gerados.
- `randomSampling`: escolhe uma amostra fixa e reprodutivel.
- `knnAdaptive`: escolhe amostras iniciais espalhadas e depois prioriza novos cenarios com k-NN, score objetivo e bonus de exploracao.
- Cache/replay por cenario: `Benchmark.spec.resultCache`, modo `readWrite`, busca por hash e gravacao apos execucao real.
- Backfill de cache a partir de resultados antigos, com metadados suficientes. Configuracao e limites em [resultados e cache](docs/resultados-e-cache.md).

## Proximas melhorias

- Avaliador offline para comparar heuristicas:
  - usar um dataset exhaustive como oraculo;
  - simular a ordem de escolha das heuristicas;
  - medir melhor score encontrado por numero de avaliacoes;
  - comparar curvas de convergencia.

- Novas heuristicas adaptativas:
  - epsilon-greedy sobre configuracoes de resiliencia;
  - surrogate model simples baseado em regressao;
  - Bayesian optimization, se o espaco e as metricas justificarem;
  - multi-objective ranking quando houver trade-off claro entre sucesso e latencia.

## Cuidados metodologicos

- A heuristica nao deve consultar resultados de cenarios que ainda nao escolheu.
- Carga, taxa de falha e conectores devem continuar sendo tratados como contextos fixos do experimento.
- O papel principal da heuristica e priorizar configuracoes de resiliencia dentro desses contextos.
- Resultados reutilizados de S3 devem ser tratados como replay de um experimento anterior, nao como nova medicao.
