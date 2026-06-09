# AGENTS.md

Guia para LLMs e agentes que forem modificar este projeto. O objetivo e manter novas implementacoes alinhadas com os padroes atuais do ResilienceBench-Operator.

## Visao geral do projeto

Este projeto e um operador Kubernetes em Java 17. Ele usa CRDs para declarar experimentos de resiliencia e controllers/executors para gerar e executar cenarios em um cluster.

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

- estrategias simples implementam `ScenarioSelectionStrategy`;
- estrategias adaptativas implementam `AdaptiveScenarioSelectionStrategy`;
- `ScenarioSelectionStrategySelector` escolhe a estrategia com base em `Benchmark.spec.strategy.type`.

Ao adicionar uma nova estrategia:

1. crie uma nova classe em `resources/selection`;
2. registre o novo tipo em `ScenarioSelectionStrategySpec`;
3. atualize `ScenarioSelectionStrategySelector`;
4. adicione validacoes dos novos parametros;
5. adicione testes unitarios;
6. atualize a documentacao em `docs`.

Para estrategias adaptativas, mantenha o fluxo existente:

```text
selectScenarios(...)
  escolhe os cenarios iniciais da Queue

selectNextScenario(...)
  escolhe o proximo cenario usando resultados ja avaliados
```

Nao altere `DefaultQueueExecutor` sem necessidade. Ele ja sabe chamar estrategias adaptativas e anexar novos itens na `Queue`.

## k-NN e heuristicas adaptativas

O `knnAdaptive` atual usa:

- vetorizacao de cenarios;
- distancia entre configuracoes;
- score real calculado a partir das metricas;
- score previsto por media ponderada;
- bonus de exploracao baseado na distancia ate o avaliado mais proximo.

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
- respeite `maxEvaluations` ou parametros equivalentes;
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
