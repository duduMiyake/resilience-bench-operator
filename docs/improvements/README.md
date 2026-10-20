# Melhorias e rastreabilidade

Este e o registro de propostas e evolucao do projeto. O comportamento em funcionamento fica nos [guias funcionais](../README.md). Uma proposta neste registro nao significa funcionalidade disponivel nem decisao de implementacao aprovada.

Revisao de codigo de referencia para esta discussao: `8773e83`. MEL-001 a MEL-005 surgiram na discussao inicial; MEL-006 a MEL-008 foram adicionadas no realinhamento do catalogo. Ao retomar o trabalho, confira as mudancas posteriores; essa referencia registra a origem da proposta, nao congela seu desenho.

## Registro de melhorias

O estado e a data de revisao ficam somente nesta tabela; as descricoes e discussoes ficam no documento vinculado. Os IDs sao permanentes e nao devem ser renumerados ou reutilizados.

### Heuristicas do catalogo inicial em discussao

Random Sampling e KNN Adaptive ja existem e sao concorrentes na comparacao; exhaustive e a referencia. As novas candidatas abaixo sao propostas, com prioridade inicial para Pattern Search e Simulated Annealing. O responsavel aprovou registrar esse caminho para discussao futura, sem iniciar implementacoes.

| ID | Melhoria | Estado | Revisado em | Evidencia atual |
| --- | --- | --- | --- | --- |
| MEL-006 | [Pattern Search](heuristics.md#mel-006-pattern-search) | proposta | 2026-10-20 | Primeira etapa sugerida; variante discreta, direcoes e passo ainda a definir. |
| MEL-007 | [Simulated Annealing](heuristics.md#mel-007-simulated-annealing) | proposta | 2026-10-20 | Primeira etapa sugerida; vizinhanca, temperatura e resfriamento ainda a definir. |
| MEL-008 | [Algoritmo Genetico (GA)](heuristics.md#mel-008-algoritmo-genetico-ga) | proposta | 2026-10-20 | Etapa posterior sugerida; representacao, populacao e operadores ainda a definir. |
| MEL-005 | [Otimizacao sequencial com TPE](heuristics.md#mel-005-otimizacao-sequencial-com-tpe) | proposta | 2026-10-20 | Mantida no catalogo proposto; representacao e integracao ainda nao decididas. |

### Infraestrutura de avaliacao (nao e heuristica)

| ID | Melhoria | Estado | Revisado em | Evidencia atual |
| --- | --- | --- | --- | --- |
| MEL-001 | [Avaliador offline e protocolo de comparacao](evaluation.md#mel-001-avaliador-offline) | proposta | 2026-10-20 | Separado dos concorrentes; sem implementacao ou campanha de validacao. |

### Propostas anteriores adiadas

Preservadas por rastreabilidade, fora da prioridade inicial apos o realinhamento. Retomar se uma pergunta experimental justificar um comparador ou variante adicional; nao sao funcionalidades disponiveis.

| ID | Melhoria | Estado | Revisado em | Motivo / condicao de retomada |
| --- | --- | --- | --- | --- |
| MEL-002 | [Selecao por diversidade: space-filling](heuristics.md#mel-002-selecao-por-diversidade) | adiada | 2026-10-20 | Possivel comparador da inicializacao do KNN, fora do catalogo inicial escolhido. |
| MEL-003 | [Busca local com reinicios](heuristics.md#mel-003-busca-local-com-reinicios) | adiada | 2026-10-20 | Proposta generica anterior; priorizar discutir a variante definida de Pattern Search. IDs distintos preservam a diferenca. |
| MEL-004 | [KNN com exploracao epsilon-greedy](heuristics.md#mel-004-knn-com-exploracao-epsilon-greedy) | adiada | 2026-10-20 | Retomar se o estudo incluir analise especifica do mecanismo de exploracao do KNN. |

## Como discutir e executar uma melhoria

1. Registre uma nova ideia com o proximo ID, problema/hipotese, proposta minima, limites e criterio de verificacao. Pode comecar como uma secao em um documento existente; crie um arquivo proprio apenas se a discussao precisar desse espaco.
2. Antes de implementar, registre o escopo acordado no pedido ou na discussao com o responsavel: comportamento esperado, parametros, criterio de aceite e questoes pendentes. Esclareca decisoes que bloqueiem o trabalho; nao exija nova confirmacao do que ja foi autorizado. Registre a decisao na secao da proposta e atualize o estado. Uma sugestao isolada nao equivale a um compromisso de implementacao.
3. Referencie o ID na issue/PR, quando houver, ou na mensagem do commit de implementacao. Registre os links/hashes resultantes na proposta para fechar a ligacao entre motivacao, codigo e evidencia. Uma issue externa nao e obrigatoria.
4. Na mesma alteracao que implementar, modificar, adiar ou descartar a melhoria, atualize a linha do registro, a data de revisao e a secao afetada. Registre a razao de mudancas de escopo e os itens pendentes. Mude tambem a documentacao funcional quando o comportamento implementado mudar.
5. Ao retomar uma proposta, confira a revisao atual do codigo antes de seguir o texto. Atualize a data somente depois dessa revisao ou de uma mudanca real. Se a solucao ja existir, ligue a evidencia e ajuste o estado em vez de implementa-la novamente.

## Estados

- `proposta`: ideia em discussao; escopo ou implementacao ainda nao aprovados.
- `planejada`: escopo e criterios de aceite acordados, ainda sem implementacao iniciada.
- `em-andamento`: implementacao iniciada; registre issue/branch/commit quando disponivel.
- `em-validacao`: comportamento implementado, mas ainda ha verificacoes acordadas pendentes.
- `concluida`: escopo acordado entregue, com evidencia das verificacoes e guias funcionais atualizados.
- `adiada` ou `descartada`: registre o motivo e, para adiamento, a condicao para retomar. Preserve a proposta e sua historia.

`Concluida` nao significa superioridade cientifica sobre outra estrategia. O resultado pode ser neutro ou desfavoravel; registre o que os dados sustentam, sem alterar retroativamente a hipotese para esconder um resultado negativo.

## Evidencia e atualizacao

Ao evoluir uma proposta, acrescente uma entrada curta de discussao: data, ID, decisao/observacao, justificativa e proximo passo. Corrija o resumo vigente, preservando o motivo das decisoes anteriores quando ele ainda for relevante. O historico detalhado de edicoes permanece no Git.

Para uma implementacao, registre os arquivos/interfaces afetados, commit/PR e verificacoes executadas. Para um experimento, registre a revisao, parametros, seeds, datasets e artefatos publicaveis, distinguindo execucao real de replay; veja [execucao de experimentos](../running-experiments.md). Nao inclua destinos SSH, credenciais ou identificadores particulares de infraestrutura.

Um ADR so e necessario quando houver uma decisao arquitetural duravel com trade-offs; nao crie um ADR para cada melhoria. Antes de encerrar o trabalho, confira se a proposta descreve o estado real e se os campos pendentes continuam explicitos. Nao marque como validado um resultado que nao foi observado.
