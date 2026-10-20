# Documentacao do ResilienceBench-Operator

## Caminhos de leitura

- **Entender o projeto:** [proposito e escopo](purpose-and-scope.md), [arquitetura](architecture.md) e [modelo de dominio](domain-model.md).
- **Comecar a desenvolver:** [ambiente e comandos](development-setup.md), [recursos Kubernetes](kubernetes-resources.md) e [AGENTS.md](../AGENTS.md).
- **Desenvolver com LLM:** [contexto por tarefa e skills](development-with-llm.md).
- **Mudar selecao ou resultados:** [heuristicas de selecao](heuristicas-de-selecao.md) e [resultados e cache](resultados-e-cache.md).
- **Trabalhar no frontend:** [Visualizer](visualizer.md) e [README do Visualizer](../resilience-bench/visualizer/README.md).
- **Usar os exemplos:** [overlay Hipster Shop](../samples/overlays/hipstershop/README.md).
- **Executar e reproduzir experimentos:** [preparacao, rodadas e artefatos](running-experiments.md).
- **Discutir novas implementacoes:** [registro e ciclo de melhorias](improvements/README.md), [catalogo proposto](improvements/heuristics.md) e [infraestrutura de avaliacao](improvements/evaluation.md).

## Documentos por funcao

- [Proposito e escopo](purpose-and-scope.md): problema, objetivos e limites.
- [Arquitetura](architecture.md): componentes e fluxo principal.
- [Modelo de dominio](domain-model.md): termos e relacoes entre recursos.
- [Desenvolvimento](development-setup.md): requisitos e comandos locais.
- [Recursos Kubernetes](kubernetes-resources.md): CRDs, manifests e compatibilidade.
- [Execucao de experimentos](running-experiments.md): requisitos de cluster, rodadas, cache, acompanhamento e dados para reproducao de estudos.
- [Selecao de cenarios](heuristicas-de-selecao.md): estrategias e comportamento adaptativo.
- [Resultados e cache](resultados-e-cache.md): formatos, armazenamento e replay.
- [Visualizer](visualizer.md): leitura visual de resultados e traces.

## Decisoes e material de pesquisa

`../adr/` contem notas e decisoes tecnicas; `../papers/` guarda materiais associados a publicacoes. Atualize a documentacao funcional quando o comportamento do produto mudar. Registre em ADR apenas decisoes com trade-offs relevantes que precisem ser entendidos no futuro.

## Melhorias e discussao

O [registro de melhorias](improvements/README.md) e a fonte de estado das propostas, com IDs estaveis, revisao e evidencia de implementacao/validacao. O [catalogo proposto](improvements/heuristics.md) registra Pattern Search, Simulated Annealing, GA e TPE como candidatas para comparar com Random Sampling e KNN, usando exhaustive como referencia. A [infraestrutura de avaliacao](improvements/evaluation.md) fica separada das heuristicas. Ao alterar uma melhoria, atualize seu registro na mesma alteracao; uma proposta concluida deve apontar para codigo, verificacoes e documentacao funcional. `../POSSIBILIDADES_HEURISTICAS.md` permanece apenas como entrada de compatibilidade.
