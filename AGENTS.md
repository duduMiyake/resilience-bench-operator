# AGENTS.md

Guia para LLMs e agentes que forem modificar este projeto. O objetivo e manter novas implementacoes alinhadas com os padroes atuais do ResilienceBench-Operator.

## Visao geral do projeto

Este projeto e um operador Kubernetes em Java 17. Ele usa CRDs para declarar experimentos de resiliencia e controllers/executors para gerar e executar cenarios em um cluster.

Consulte [docs/README.md](docs/README.md) para a documentacao por funcao, [docs/architecture.md](docs/architecture.md) para o fluxo e [docs/domain-model.md](docs/domain-model.md) para os termos do dominio. [docs/development-with-llm.md](docs/development-with-llm.md) indica contexto por tarefa e skills disponiveis em `.agents/skills/`.

O vocabulario canonico fica em `docs/domain-model.md`; decisoes ficam em `adr/`. As skills do Matt instaladas neste repositorio foram ajustadas para esses caminhos; nao crie um segundo glossario ou diretorio de ADRs. Antes de mudar a execucao, leia as limitacoes de namespace e reutilizacao de fila em `docs/architecture.md`.

Para preparar ou reproduzir experimentos, leia [docs/running-experiments.md](docs/running-experiments.md). Nao presuma que o commit do checkout corresponde a imagem do operador implantada. Quando a tarefa envolver o ambiente particular do responsavel, consulte o roteiro local em `.local-docs/remote-experiments.md` e os parametros de `.local-docs/remote-environment.md`, se disponiveis; esses arquivos nao acompanham o clone publico.

Este repositorio e publico. Use placeholders para destinos SSH e identificadores de infraestrutura nos arquivos versionados. Detalhes particulares de operacao ficam em `.local-docs/`, ignorado pelo Git; nunca adicione esse diretorio a um commit. Nao copie senhas, chaves privadas, kubeconfigs ou valores de credenciais para docs, manifests ou logs.

Termos principais:

- `Benchmark`: define o experimento e o espaco de busca.
- `Workload`: define a carga usada nos testes.
- `Scenario`: representa uma combinacao concreta gerada a partir do benchmark.
- `Queue`: controla a fila de execucao dos cenarios.
- `ResilientService`: representa servicos alvo no cluster.

Evite tratar a ferramenta como uma aplicacao web generica. Ela e um operador/controller Kubernetes com recursos customizados.

## Estrutura importante

- `resilience-bench/operator/src/main/java/io/resiliencebench`: codigo principal do operador.
- `resilience-bench/operator/src/main/java/io/resiliencebench/resources`: modelos e fabricas de recursos.
- `resilience-bench/operator/src/main/java/io/resiliencebench/resources/selection`: estrategias de selecao de cenarios.
- `resilience-bench/operator/src/main/java/io/resiliencebench/execution`: execucao de filas e cenarios.
- `resilience-bench/operator/src/main/java/io/resiliencebench/execution/steps`: passos executados para cada cenario.
- `crd`: CRDs gerados/usados pelo projeto.
- `samples`: exemplos aplicaveis com `kubectl`.
- `docs`: documentacao de funcionamento e decisoes.
- `adr`: notas tecnicas e decisoes arquiteturais.

## Padroes de implementacao

Siga os padroes ja existentes antes de introduzir novos estilos.

- Use Java 17.
- Mantenha classes no pacote `io.resiliencebench`.
- Use Spring `@Component` ou `@Service` para classes injetadas.
- Use `CustomResourceRepository<T>` para acesso a recursos customizados quando possivel.
- Use Fabric8 Kubernetes Client, como o restante do projeto.
- Prefira objetos de dominio simples com getters, construtores e comportamento pequeno.
- Evite dependencias novas sem necessidade clara.
- Evite criar frameworks internos ou abstracoes grandes para mudancas pequenas.
- Preserve o comportamento das estrategias existentes ao adicionar novas.

## Estrategias de selecao

Estrategias ficam em:

```text
resilience-bench/operator/src/main/java/io/resiliencebench/resources/selection
```

Padrao atual:

- `ScenarioSelectionStrategy` define a entrada `selectScenarios`; `exhaustive` implementa essa interface diretamente;
- estrategias que escolhem configuracoes implementam `ConfigurationSelectionStrategy`, que expande o retorno de `selectConfigurations` em cenarios;
- estrategias adaptativas atuais implementam `AdaptiveConfigurationSelectionStrategy`, como `knnAdaptive`;
- `ScenarioSelectionStrategySelector.select` escolhe a estrategia com base em `Benchmark.spec.strategy.type`; `selectAdaptive` disponibiliza a estrategia adaptativa ao executor.

`AdaptiveScenarioSelectionStrategy` e `selectNextScenario` ainda existem no codigo, mas nao sao o contrato usado pelo fluxo adaptativo atual. Nao use essa interface antiga para integrar uma nova heuristica ao executor.

Ideia central da geracao de cenarios:

- o contexto experimental formado por carga e taxa de falha deve ser tratado como fixo durante a escolha;
- conectores representam um conjunto fixo de experimentos/contextos a serem avaliados;
- as heuristicas nao devem procurar novos contextos de carga, taxa de falha ou conectores;
- o papel principal das heuristicas e escolher quais configuracoes de resiliencia devem ser testadas dentro desses contextos fixos;
- cada `Scenario` deve ser entendido como uma configuracao concreta de resiliencia aplicada a um contexto experimental ja definido.

Ao adicionar uma nova estrategia:

1. crie uma nova classe em `resources/selection`;
2. registre o novo tipo em `ScenarioSelectionStrategySpec`;
3. atualize `ScenarioSelectionStrategySelector`, incluindo `selectAdaptive` se a estrategia for adaptativa;
4. adicione validacoes dos novos parametros;
5. adicione testes unitarios;
6. atualize a documentacao em `docs`.

Para estrategias adaptativas, mantenha o fluxo existente:

```text
selectConfigurations(...)
  escolhe as configuracoes iniciais
  selectScenarios(...) expande essas configuracoes para a Queue

selectNextDecision(...)
  recebe configuracoes ja enfileiradas e avaliacoes completas
  retorna Optional<ConfigurationSelectionDecision> com a proxima configuracao
  o executor anexa todos os cenarios dessa configuracao a Queue
```

Nao altere `DefaultQueueExecutor` sem necessidade. Ele ja sabe chamar estrategias adaptativas e anexar novos itens na `Queue`.

## k-NN e heuristicas adaptativas

O `knnAdaptive` atual usa:

- vetorizacao de configuracoes de resiliencia;
- distancia entre configuracoes;
- score real calculado a partir das metricas;
- score previsto por media ponderada;
- bonus de exploracao baseado na distancia ate o avaliado mais proximo.

Para heuristicas adaptativas, preserve a interpretacao do espaco de busca: a carga, a taxa de falha e os conectores definem contextos fixos de experimento. A heuristica deve aprender a partir dos resultados observados para priorizar novas configuracoes de resiliencia nesses mesmos contextos, e nao redefinir quais contextos existem.

Ao mexer nessa area:

- mantenha nomes e formulas documentados;
- escreva testes pequenos com cenarios controlados;
- explique no codigo ou docs a diferenca entre `score real`, `score previsto` e `score de escolha`;
- preserve o comportamento das estrategias existentes ao adicionar outra heuristica;
- se houver logica compartilhada, extraia para uma classe de suporte pequena, por exemplo `ScenarioSelectionSupport`.

## CRDs e modelos

Ao adicionar campos em specs Java:

- atualize o CRD correspondente em `crd`, quando aplicavel;
- mantenha os campos opcionais para preservar compatibilidade;
- adicione `@JsonPropertyDescription` quando fizer sentido;
- atualize exemplos YAML em `samples` apenas se necessario;
- adicione testes de serializacao/deserializacao quando houver novo campo em CRD.

Nao remova campos existentes sem uma migracao explicita.

## Execucao e filas

O fluxo de execucao depende de `Queue` e `ExecutionQueueItem`.

Cuidados:

- preserve os estados `PENDING`, `RUNNING` e `FINISHED`;
- nao execute cenarios fora da fila;
- nao anexe cenarios repetidos em estrategias adaptativas;
- respeite o orcamento por configuracao: `maxConfigurations`, com fallback para `maxEvaluations` e `maxScenarios` na estrategia adaptativa atual;
- mantenha a escrita de resultados compatavel com `ResultFileStep`.

## Testes

Use JUnit 5. Testes existentes ficam em:

```text
resilience-bench/operator/src/test/java
```

Para mudancas em selecao de cenarios, adicione ou atualize testes em:

```text
resilience-bench/operator/src/test/java/io/resiliencebench/resources/selection
```

Comandos uteis:

```bash
cd resilience-bench/operator
mvn test
```

Para mudancas pequenas e locais, rode ao menos os testes relacionados. Para mudancas em CRDs, selecao ou execucao, prefira rodar a suite de testes do modulo.

## Documentacao

Documente mudancas de comportamento em `docs`.

Use linguagem direta e exemplos pequenos. Para heuristicas, explique:

- qual problema resolve;
- quais parametros usa;
- como escolhe cenarios iniciais;
- como escolhe proximos cenarios;
- como calcula score;
- quais limitacoes existem.

Mantenha `docs/heuristicas-de-selecao.md` atualizado quando alterar ou adicionar estrategias.

Ao discutir ou implementar uma melhoria, consulte [docs/improvements/README.md](docs/improvements/README.md) e a proposta vinculada ao ID. Confira o estado real no codigo antes de seguir a proposta. Na mesma alteracao, atualize estado, data de revisao, decisoes e evidencia (commit/PR, verificacoes e artefatos publicaveis). Referencie o ID na issue/PR ou no commit de implementacao. Nao marque como concluida uma melhoria com verificacoes acordadas pendentes e nao apresente nomes de estrategias propostas como tipos ja suportados pelo CRD. Propostas e guias de comportamento implementado tem funcoes diferentes; atualize ambos quando aplicavel.

## Samples

Arquivos em `samples` devem continuar aplicaveis com `kubectl`.

Cuidados:

- nao edite samples apenas para formatacao;
- preserve namespaces, labels e annotations existentes;
- ao adicionar strategy nova, prefira criar exemplo pequeno ou atualizar somente o sample relevante;
- mantenha valores coerentes com os CRDs.

## Estilo de alteracao

- Faca mudancas pequenas e focadas.
- Nao refatore areas nao relacionadas.
- Nao reverta alteracoes existentes sem pedido explicito.
- Prefira nomes claros a abreviacoes.
- Evite comentarios obvios; comente apenas formulas, heuristicas ou decisoes que nao sejam autoexplicativas.
- Ao adicionar comportamento novo, inclua teste e doc quando possivel.

## Checklist antes de finalizar

- A mudanca segue o fluxo atual de `Benchmark -> Scenario -> Queue -> Result`.
- Estrategias existentes continuam com o mesmo comportamento.
- Novos parametros foram validados.
- Testes relevantes foram adicionados ou atualizados.
- Docs foram atualizadas se o comportamento mudou.
- Samples so foram alterados quando necessario.
