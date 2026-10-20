# Desenvolvimento com LLM

## Contexto por tarefa

Comece por `AGENTS.md`, [proposito e escopo](purpose-and-scope.md) e [modelo de dominio](domain-model.md). Depois carregue os documentos e fontes pertinentes a tarefa, usando a tabela abaixo. A documentacao descreve comportamento verificado no codigo; se uma mudanca revelar divergencia, confira a implementacao e registre a diferenca antes de assumir uma regra.

| Tarefa | Contexto e fonte a consultar | Alcance da verificacao |
| --- | --- | --- |
| Adicionar estrategia | `heuristicas-de-selecao.md`, selector, interfaces de estrategia e `resources/selection` | Orcamento por configuracao, seed, agregacao de contextos e testes de estrategias anteriores |
| Mudar score | `heuristicas-de-selecao.md`, `ObjectiveScorer`, `ConfigurationResultAggregator` | Normalizacao, direcao das metricas, pesos e interpretacao do trace/Visualizer |
| Mudar execucao | `architecture.md`, controller, executores e `StepRegistry` | Ordem de passos, estados, eventos duplicados, cache e isolamento por namespace |
| Adicionar campo em CRD | `kubernetes-resources.md`, modelo Java e CRD correspondente | Defaults, validacao, serializacao, schemas gerados, samples e consumers |
| Mudar resultados/cache | `resultados-e-cache.md`, `execution/resultcache`, `ResultFileStep` | Hash estavel, origem da medicao, formatos existentes e leitura pelo Visualizer |
| Mudar Visualizer | `visualizer.md`, README do modulo, `core/services` e componente afetado | Normalizacao dos formatos atuais/legados, significado dos scores e testes `.spec.ts` |
| Preparar ou reproduzir um experimento | `running-experiments.md`, README do overlay, `startup-hipstershop`, manifests e imagem do operador | Contexto do cluster, revisao efetivamente executada, encerramento da rodada anterior, politica de cache e registro metodologico |

## Skills disponiveis no repositorio

As skills ficam em `.agents/skills/` e acompanham o checkout. Na proxima interacao do agente, use os nomes abaixo quando quiser solicitar esse fluxo explicitamente:

- `$grill-with-docs`: esclarecer uma mudanca e registrar vocabulario/decisoes. Usa as skills `grilling` e `domain-modeling`.
- `$tdd`: implementar comportamento test-first, em ciclos pequenos. A versao instalada pede acordo sobre as interfaces que serao testadas antes de escrever testes; `codebase-design` e uma referencia auxiliar quando essas interfaces ainda estao em discussao.
- `$resiliencebench-architecture`: rastrear impacto entre controller, dominio, selecao, execucao e persistencia.
- `$kubernetes-crd-change`: coordenar mudancas em modelos, CRDs, consumidores e manifests.

Exemplo: "Use $tdd para corrigir o calculo de score. Leia o guia de heuristicas e proponha a interface a testar antes de implementar."

As skills do Matt foram copiadas de [mattpocock/skills](https://github.com/mattpocock/skills), referencia consultada `5beb35b040e3de97e54fa9e386d7e42d15c295c2`. A [licenca original](../.agents/skills/MATT-POCOCK-LICENSE) acompanha a copia. `tdd` e `grill-with-docs` vieram de `skills/engineering/`; suas dependencias sao `skills/productivity/grilling` e `skills/engineering/{domain-modeling,codebase-design}`.

Adaptacoes locais: abrir as skills irmas sem depender de uma ferramenta chamada `Skill`, usar `docs/domain-model.md` como vocabulario e `adr/` como destino de decisoes. O documento de dominio inclui relacoes e mapeamento para o codigo; as definicoes de termos devem continuar curtas e separadas dessas explicacoes. Atualizacoes futuras das skills precisam preservar essas adaptacoes. Essas duas skills locais do ResilienceBench ainda sao uma primeira versao, a ser refinada com tarefas reais.

## Onde registrar o resultado de uma conversa

- Definicoes e relacoes do dominio: `domain-model.md`.
- Comportamento implementado: guia funcional correspondente.
- Decisao duravel com alternativas e consequencias: `../adr/`. As notas existentes sao historicas; nao presumir que todo exemplo nelas corresponde ao codigo atual.
- Ideias ainda nao implementadas: `../POSSIBILIDADES_HEURISTICAS.md`.

Ao concluir uma alteracao, informe arquivos afetados, comportamento resultante, verificacao executada e limites ainda nao verificados. Compilar ou passar testes unitarios nao demonstra, por si so, que um experimento executa no servidor/cluster.
