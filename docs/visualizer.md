# ResilienceBench Visualizer

O Visualizer e uma aplicacao Angular independente para inspecionar localmente os artefatos de uma rodada do ResilienceBench. Ele nao envia arquivos para um servidor e nao depende do build Maven do Operator.

## Requisitos

- Node.js `^22.22.3`, `^24.15.0` ou uma versao suportada pelo Angular configurado no projeto.
- npm 8 ou superior.

## Executar

```bash
cd resilience-bench/visualizer
npm ci
npm start
```

O servidor de desenvolvimento usa `http://localhost:4200` por padrao.

O Visualizer pode rodar tanto no WSL quanto no Windows. Nao use o Node do
Windows sobre um checkout em `\\wsl.localhost` e nao compartilhe
`node_modules` entre os dois ambientes, pois algumas dependencias possuem
binarios especificos para cada sistema operacional. As instrucoes completas de
execucao e solucao de problemas estao no
[`README` do Visualizer](../resilience-bench/visualizer/README.md).

Outros comandos:

```bash
npm test
npm run build
```

## Arquivos aceitos

- `trace.json` obrigatorio: trace heuristico com `schemaVersion: 2`, ou o formato exhaustive legado limitado a `{ "strategy": "exhaustive", "steps": [] }`.
- `results.json` opcional: objeto com um array `results`.

Todo o parsing ocorre no navegador. O trace legado nao recebe decisoes, eventos ou metadados sinteticos; a tela mostra apenas resumo, espaco de resultados e configuracoes presentes nos dados.

## Arquitetura

O projeto usa componentes standalone e esta organizado em:

- `core/models`: contrato normalizado independente dos JSONs brutos.
- `core/services/run-normalizer.service.ts`: validacao, adapters v2/legado, normalizacao de metricas e join.
- `core/services/explorer-state.service.ts`: signals compartilhados para rodada, decisao e filtros de contexto.
- `features/loader`: selecao e validacao local dos arquivos.
- `features/explorer`: resumo, scatter, progresso, timeline, detalhes e tabela legacy.

O join entre trace e resultados tenta `scenarioHash` primeiro. Quando o hash nao esta disponivel em uma execucao ou scenario esperado, usa o nome do scenario como fallback. A posicao nos arrays nunca participa do join.

Resultados associados a decisoes sao pontos visitados. Resultados nao associados permanecem no conjunto de referencia/exhaustive. O scatter preserva cada resultado individual e os filtros de `workload_users` e `fault_percentage` evitam combinar contextos operacionais diferentes.

## Limitacoes do MVP

- O papel de um `results.json` como resultado da run ou conjunto exhaustive de referencia nao e declarado pelo schema; o MVP infere referencia pelos itens nao associados a decisoes.
- O trace exhaustive legado nao possui o historico de escolha necessario para timeline, progresso ou detalhes heuristicas.
- A unidade de `iteration_duration_p95` nao possui metadado explicito no artefato; a tela segue o contrato atual e apresenta o valor como segundos sem alterar o valor original.
- A linha de melhor score, os vizinhos e metadados especificos so aparecem quando fornecidos pelo trace.
- Os eventos v2 sao normalizados e ordenados, mas playback e visualizacao de eventos ficam para uma proxima versao.
- PrimeNG 21 sinaliza o componente Chart para futura substituicao; ele foi mantido neste MVP por ser parte da stack definida.
