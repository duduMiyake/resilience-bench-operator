# Modelo de dominio

Os conceitos abaixo correspondem aos recursos e objetos usados pelo operador. A declaracao concreta fica nos manifests Kubernetes; os modelos Java estao em `resilience-bench/operator/src/main/java/io/resiliencebench/resources`.

| Conceito | Papel |
| --- | --- |
| `Benchmark` | Define o experimento: workload, espaco de configuracao, falhas e estrategia de selecao. |
| `Workload` | Define a carga executada, incluindo configuracao do script e parametros de execucao. |
| `Scenario` | Uma execucao concreta que combina workload/carga, falha e configuracoes aplicadas aos servicos/conectores. |
| `ExecutionQueue` (`Queue`) | Mantem a ordem e o estado dos cenarios selecionados para um benchmark. |
| `ResilientService` | Identifica o Deployment alvo por selector e declara variaveis de ambiente do servico. |
| `Connector` | Relaciona servicos no contexto do cenario e carrega configuracoes/padroes aplicaveis. |
| `Fault` | Descreve a falha injetada, como atraso ou aborto de requisicoes. |
| `IstioPattern` | Descreve uma configuracao de resiliencia aplicada via Istio, como retry, timeout ou circuit breaker. |
| `ResilienceConfiguration` | Chave normalizada usada pela selecao para agrupar configuracoes equivalentes, sem incluir carga ou taxa de falha. |
| `HeuristicDecision` | Registro de uma escolha da estrategia para avaliar uma configuracao de resiliencia. |

## Relacoes e ciclo

Um `Benchmark` referencia um `Workload` e declara templates que o `ScenarioFactory` expande em `Scenario`s. A estrategia pode selecionar cenarios diretamente ou selecionar configuracoes de resiliencia, que entao correspondem aos cenarios concretos obrigatorios. Os cenarios selecionados entram numa `ExecutionQueue`; a execucao produz resultados por cenario e, para estrategias adaptativas, resultados agregados por configuracao.

Carga e taxa de falha definem o contexto experimental. As estrategias atuais mantem esses contextos fixos e priorizam quais configuracoes de resiliencia testar dentro deles.

Os schemas autoritativos dos recursos estao em [`crd/`](../crd/); exemplos completos estao em [`samples/`](../samples/).

## Nomes conceituais e nomes no codigo

Somente `Benchmark`, `Workload`, `Scenario`, `Queue` e `ResilientService` sao CRDs do projeto. O Kind `Queue` corresponde a classe Java `ExecutionQueue`; `ResilienceConfiguration` e `HeuristicDecision` sao conceitos do processo de selecao/trace, nao recursos que se aplicam com `kubectl`. No codigo, procure `ResilienceConfigurationKey` e `ConfigurationSelectionDecision`.

Cada item de fila usa os valores serializados `pending`, `running` e `finished`. Os identificadores Java `PENDING`, `RUNNING` e `FINISHED` representam essas strings em minusculas. Um item finalizado por cache nao representa uma nova medicao.

Por exemplo, duas configuracoes de retry avaliadas em duas cargas e tres taxas de falha geram doze cenarios concretos. O orcamento de duas configuracoes corresponde a doze execucoes ou replays; nao a dois cenarios. Consulte [heuristicas de selecao](heuristicas-de-selecao.md) para agregacao e parametros de orcamento.
