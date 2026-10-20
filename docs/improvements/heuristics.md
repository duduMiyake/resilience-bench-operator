# Catalogo proposto de heuristicas

Ponto de partida para discussao e desenvolvimento futuros, aprovado para registro pelo responsavel. O objetivo e comparar como diferentes algoritmos encontram uma configuracao de maior score com menos avaliacoes, tendo o exhaustive como referencia. Registrar este caminho nao aprova automaticamente todos os detalhes ou inicia a implementacao.

Os estados e datas ficam no [registro de melhorias](README.md). O comportamento disponivel esta em [heuristicas de selecao](../heuristicas-de-selecao.md). Os nomes de trabalho propostos abaixo ainda nao sao tipos aceitos pelo CRD.

## Problema comum da comparacao

Usar o mesmo espaco finito de configuracoes de resiliencia, os mesmos contextos de carga/falha e o mesmo objetivo escalar estruturado. Cada configuracao selecionada deve avaliar todos os seus cenarios obrigatorios; o score e calculado depois da agregacao das metricas. Baseline e combinacoes de connectors fazem parte do espaco valido.

O controller expande os cenarios antes da selecao. Este catalogo busca reduzir avaliacoes, sem pressupor geracao do espaco sob demanda. O desenho de novas estrategias deve permitir reconstruir ou persistir o estado de busca, sem depender apenas da memoria do processo do operador.

## Catalogo inicial em discussao

| Papel | Algoritmo | Disponibilidade / proposta | Abordagem |
| --- | --- | --- | --- |
| Referencia | Exhaustive | Existente | Avalia todas as configuracoes declaradas; fornece o melhor score observado da campanha. |
| Concorrente | Random Sampling | Existente | Amostragem aleatoria como comparador simples. |
| Concorrente | KNN Adaptive | Existente | Previsao por proximidade e bonus de exploracao. |
| Concorrente | Pattern Search | MEL-006 | Busca direta com direcoes e ajuste do passo, adaptada a grade discreta. |
| Concorrente | Simulated Annealing | MEL-007 | Busca por vizinhanca com aceitacao probabilistica de pioras. |
| Concorrente | Algoritmo Genetico (GA) | MEL-008 | Busca populacional por selecao, cruzamento e mutacao usando score escalar. |
| Concorrente | TPE | MEL-005 | Busca sequencial orientada por distribuicoes dos parametros observados. |

O [avaliador offline MEL-001](evaluation.md#mel-001-avaliador-offline) e infraestrutura para comparar essas estrategias. Ele nao pertence ao catalogo de heuristicas.

## Caminho sugerido para o desenvolvimento

1. Acordar o protocolo de comparacao em [evaluation.md](evaluation.md), usando primeiro os algoritmos existentes para conferir coerencia dos resultados. O avaliador offline pode apoiar essa etapa; ele nao substitui a validacao de execucao no cluster.
2. Detalhar Pattern Search e Simulated Annealing: representacao, vizinhanca, estado, parametros e criterios de aceite. Sao as primeiras adicoes sugeridas.
3. Discutir GA e TPE depois, para ampliar a comparacao com busca populacional e outra abordagem baseada em modelo. O custo da populacao/inicializacao deve caber no mesmo orcamento de avaliacoes.
4. Para cada implementacao escolhida, vincular ID, commit/PR, testes, evidencia experimental e guia funcional. Nao inferir um vencedor antes das campanhas: registrar qualidade, custo e variabilidade nas condicoes estudadas.

Datas de entrega, parametros finais, seeds e orcamentos nao foram definidos. A ordem acima organiza a discussao e pode ser revista com justificativa registrada.

## MEL-006: Pattern Search

**Nome de trabalho:** `patternSearch`. **Tipo:** busca direta para o score escalar atual.

**Hipotese:** explorar direcoes nos parametros e ajustar o passo pode encontrar boas configuracoes sem treinar um modelo preditivo.

**Mecanismo a definir:** ponto inicial, conjunto de direcoes de exploracao, criterio de movimento e politica de ajuste do passo. A adaptacao deve operar sobre valores admissiveis da grade declarada, tratando categorias e baseline explicitamente. Uma projeção em candidatos validos pode gerar repeticoes e precisa de uma politica definida.

**Integracao proposta:** selecao adaptativa por configuracao, com passo, origem e direcao registrados na metadata e estado recuperavel. Nao renomear uma busca local generica como Pattern Search: documentar a variante adotada e sua relacao com a referencia de Torczon. Resultados teoricos de espacos continuos nao se transferem automaticamente para esta adaptacao discreta.

**Aceite a acordar:** nenhuma configuracao invalida; reproducibilidade; tratamento de limites, categorias e baseline; ajuste do passo, esgotamento e parada; reaproveitamento de observacoes conhecidas sem contabilizar uma nova medicao. Confirmar expansao dos contextos, orcamento e trace.

## MEL-007: Simulated Annealing

**Nome de trabalho:** `simulatedAnnealing`. **Tipo:** busca por trajetoria com temperatura.

**Hipotese:** aceitar algumas pioras permite sair de regioes em que uma busca puramente de melhoria ficaria presa.

**Mecanismo proposto para o score maximizado:** melhorias sao aceitas; uma piora de diferenca `delta = score_novo - score_atual` pode ser aceita com probabilidade `exp(delta / T)`, para `T > 0`. Manter separadamente configuracao atual e melhor configuracao observada. Definir temperatura inicial, resfriamento, vizinhanca e condicao de parada antes de implementar.

**Integracao e aceite a acordar:** candidatos validos, seed e desempates definidos; estado/temperatura recuperaveis; metadata de movimento e aceitacao; testes de melhora, piora e limites de temperatura. Revisitar uma configuracao conhecida nao e uma nova avaliacao independente. Contar avaliacoes distintas e propostas separadamente e definir a evolucao da temperatura diante de revisitas.

**Limite:** resultado depende da vizinhanca e do resfriamento. A possibilidade de escapar de um otimo local nao garante encontrar o otimo global no orcamento disponivel.

## MEL-008: Algoritmo Genetico (GA)

**Nome de trabalho:** `geneticAlgorithm`. **Tipo:** busca populacional com objetivo escalar.

**Hipotese:** cruzar configuracoes e mutar parametros pode explorar interacoes entre connectors e regioes distintas do espaco.

**Mecanismo a definir:** representacao da configuracao, populacao inicial, selecao de pais, cruzamento, mutacao, elitismo e substituicao. A representacao deve preservar combinacoes validas e parametros inativos no baseline. O algoritmo seleciona por score escalar; nao e NSGA-II com outro nome.

**Integracao proposta:** formar/atualizar populacoes a partir de avaliacoes completas, emitindo candidatos para a fila sem exigir execucao paralela. O estado de populacao/geracao deve ser persistido ou reconstruivel; decidir como encerrar uma geracao incompleta ao atingir o orcamento. Populacao inicial conta no orcamento total.

**Aceite a acordar:** operadores nao emitem configuracoes invalidas; duplicatas reutilizam apenas resultados conhecidos; reproducibilidade; limites de populacao, geracoes e propostas sem progresso; contabilizacao de avaliacoes e trace. Com orcamento curto, poucas geracoes podem limitar o beneficio da abordagem.

## MEL-005: Otimizacao sequencial com TPE

**Nome de trabalho:** `tpeAdaptive`. **Tipo:** otimizacao sequencial baseada em modelo.

**Hipotese:** um modelo de distribuicoes de parametros das configuracoes com melhor/pior score pode priorizar regioes promissoras sem depender da distancia euclidiana do KNN.

**Mecanismo a estudar:** apos uma amostra inicial, separar observacoes completas segundo um quantil do score, ajustar densidades para os grupos e priorizar candidatos pela razao entre essas densidades. O objetivo do projeto e maximizado, portanto o grupo favoravel corresponde aos scores maiores. Somente configuracoes validas e ainda nao selecionadas podem ser emitidas. TPE e uma abordagem de otimizacao bayesiana descrita nas referencias abaixo; a adaptacao concreta ainda requer desenho e validacao.

**Pontos que impedem trata-la como uma adicao pequena:** categorias, parametros condicionais/inativos quando o connector esta em baseline, suavizacao das densidades com poucas observacoes, quantil e inicializacao, e custo/dependencias de uma implementacao Java ou integracao externa. Decidir o limite da integracao antes de adicionar dependencias ou um servico Python ao operador.

**Aceite proposto:** scores nao escolhidos permanecem inacessiveis; nenhuma configuracao fora do indice, repetida ou acima do orcamento; seed/reinicio definidos; testes com categorias e baseline; comparacao offline com as estrategias atuais e custo de selecao registrado. O trace deve explicar a prioridade TPE sem reutilizar nomes de metadata de KNN com significado diferente.

**Limite:** com um orcamento pequeno, o modelo pode ter poucas observacoes para aprender. A literatura de otimizacao de hiperparametros motiva a candidatura, mas nao comprova vantagem em experimentos de resiliencia. Definir um criterio de viabilidade antes de planejar sua implementacao.
## Propostas anteriores fora do catalogo inicial

MEL-002, MEL-003 e MEL-004 foram preservadas para rastrear a discussao anterior. Foram adiadas no realinhamento para um catalogo de algoritmos reconhecidos, mantendo possiveis comparadores/variantes para outro estudo. Isso nao significa que sejam tecnicamente invalidas. Retomar apenas se houver uma pergunta experimental especifica que justifique inclui-las. MEL-003 nao foi convertida em MEL-006: busca local generica e Pattern Search exigem definicoes distintas.

## MEL-002: Selecao por diversidade

**Nome de trabalho:** `spaceFilling`. **Tipo:** selecao fixa, sem feedback de resultados.

**Hipotese:** uma amostra que cobre regioes distintas do espaco pode encontrar configuracoes competitivas com poucos testes. Comparada ao KNN, permite investigar quanto do resultado vem da cobertura inicial e quanto vem da adaptacao aos scores.

**Mecanismo minimo:** escolher a primeira configuracao usando seed; depois escolher, entre as restantes, aquela cuja distancia a configuracao selecionada mais proxima seja maxima. Repetir ate `maxConfigurations` ou esgotar o espaco. Usar o mesmo criterio de distancia/normalizacao para permitir uma comparacao controlada com a inicializacao do KNN.

**Reuso:** `ScenarioSelectionSupport.selectSpaceFillingConfigurations` ja realiza essa selecao para a inicializacao do KNN. A nova estrategia implementaria `ConfigurationSelectionStrategy`, reutilizando o helper em vez de criar outro algoritmo.

**Aceite proposto:** selecao sem consultar metricas, deterministica para a mesma seed/entrada, sem repeticoes, com orcamento e expansao de contextos corretos. Registrar no trace que a escolha foi por diversidade, sem inventar score previsto.

**Limites e discussao:** cobertura geometrica nao garante melhor score. A distancia depende da codificacao de parametros e do baseline; categorias e duracoes nao podem ganhar uma interpretacao numerica arbitraria. Inicialmente manter a representacao existente para isolar o efeito da adaptacao; qualquer mudanca nela e outra variavel experimental. Comparar com randomSampling no mesmo orcamento e com o KNN usando a mesma inicializacao quando aplicavel.

## MEL-003: Busca local com reinicios

**Nome de trabalho:** `localSearch`. **Tipo:** adaptativa por configuracao.

**Hipotese:** se configuracoes proximas tiverem desempenho relacionado, explorar a vizinhanca de uma boa configuracao pode aproveitar melhor um orcamento curto.

**Mecanismo minimo proposto:** iniciar em uma configuracao escolhida por seed; avaliar vizinhos ainda nao selecionados e mover para uma configuracao com melhoria de score. Ao esgotar a vizinhanca sem melhoria, reiniciar em uma configuracao ainda nao selecionada, com escolha reprodutivel. Parar no orcamento global ou no esgotamento de candidatos. Nao executar novamente uma configuracao apenas para reiniciar a busca.

**Vizinhanca a definir antes de implementar:** para parametros ordenados, mudar um valor para o adjacente da grade declarada; para categorias, trocar uma categoria; para baseline, alternar a opcao de um connector sem gerar uma combinacao invalida. Escolher candidatos entre as configuracoes existentes no indice. Definir se as transicoes de baseline sao tratadas como uma mudanca do connector inteiro.

**Integracao:** `AdaptiveConfigurationSelectionStrategy` e `ConfigurationSelectionDecision`. O estado da busca deve poder ser reconstruido dos recursos/resultados persistidos ou ser persistido explicitamente; nao depender apenas da memoria do processo. Registrar configuracao de origem, movimento/reinicio e criterio de desempate em `selection.metadata`.

**Aceite proposto:** candidatos validos, todos os contextos preservados, reproducibilidade, orcamento global e ausencia de repeticoes. Casos controlados com melhoria local, plato, reinicio e interacoes entre connectors. Definir a politica de vizinhos e recuperacao apos reinicio do operador antes de fixar os testes.

**Limite:** interacoes entre parametros podem produzir otimos locais. Reinicios nao garantem o otimo global; essa fragilidade e parte da comparacao. Resolver em discussao a ordem dos vizinhos e se a busca aceita a primeira melhoria ou avalia a vizinhanca antes de mover.

## MEL-004: KNN com exploracao epsilon-greedy

**Nome de trabalho:** `knnEpsilonGreedy`. **Tipo:** variante adaptativa para estudo do mecanismo de exploracao.

**Hipotese:** explorar aleatoriamente em parte das decisoes pode oferecer uma alternativa simples ao bonus por distancia usado pelo KNN atual.

**Mecanismo minimo proposto:** manter inicializacao e previsao KNN comparaveis as atuais. Em cada decisao seguinte, com probabilidade `epsilon`, selecionar uniformemente uma configuracao ainda nao escolhida; caso contrario, selecionar a de maior score previsto, sem somar o bonus de distancia. A previsao usa apenas configuracoes completamente avaliadas. `epsilon` seria fixo e validado em `[0,1]` na primeira versao, com seed e desempates definidos.

**Escopo:** trata-se de uma politica de exploracao sobre candidatos nao avaliados com um modelo KNN. Nao equivale a um bandit classico que reavalia cada braco para estimar sua media. Nao recomendar epsilon-greedy apenas sobre o melhor score observado: e preciso definir como escolher uma configuracao nova na etapa de aproveitamento.

**Integracao e aceite propostos:** reutilizar os calculos de previsao existentes com a menor mudanca compartilhada necessaria; nao duplicar a classe KNN inteira nem alterar sua estrategia atual por efeito colateral. Testar `epsilon=0`, `epsilon=1`, seed, orcamento e exclusao dos candidatos ja escolhidos. O caso `epsilon=1` continua com a inicializacao definida; nao e necessariamente identico a randomSampling desde a primeira selecao.

**Trace e discussao:** registrar se a decisao foi exploracao ou aproveitamento, epsilon e previsao quando usada. Uma escolha aleatoria nao deve ser apresentada como maximizacao de `selectionScore`. Definir a reproducibilidade do gerador apos reinicio do operador. Comparar com o KNN atual e com KNN sem bonus, sob a mesma inicializacao e orcamento.
## Possiveis frentes posteriores

- **Pareto Local Search e NSGA-II:** candidatos para investigar sucesso e latencia como objetivos separados, com arquivo de nao dominados ou populacao multiobjetivo. O fluxo atual usa score escalar; essa frente precisa definir objetivos vetoriais, metricas de comparacao de fronteiras e impacto em selecao/trace/Visualizer antes da implementacao. A fronteira Pareto mostrada no frontend nao implementa esses algoritmos no operador.
- **UCB Bandit:** definir primeiro os bracos e a natureza das observacoes. UCB1 classico admite medicoes repetidas e inicializa os bracos; tratar cada uma das muitas configuracoes como um braco pode consumir o orcamento antes da fase de aproveitamento. Repetir o mesmo resultado de cache nao fornece uma nova observacao independente. Outras formulacoes UCB exigem explicitar o modelo; nao equivalem ao bonus por distancia do KNN.
- **Successive Halving/Hyperband:** dependem da definicao de recursos/fidelidades por candidato. Reduzir contextos, carga ou duracao alteraria o contrato experimental atual.

Essas frentes nao foram aprovadas para implementacao nem pertencem ao primeiro catalogo. Abrir novos IDs quando houver uma proposta concreta, sem reutilizar IDs existentes.

## Referencias

As referencias identificam familias/algoritmos existentes; nao demonstram superioridade no ResilienceBench. Adaptacoes ao espaco discreto devem ser descritas e avaliadas explicitamente.

- Torczon, [Pattern Search Methods for Nonlinear Optimization](https://www.cs.wm.edu/~va/articles/viewsNnews.pdf). Referencia para discutir uma variante de Pattern Search.
- Kirkpatrick, Gelatt e Vecchi, [Optimization by Simulated Annealing](https://doi.org/10.1126/science.220.4598.671), 1983.
- Holland, [Adaptation in Natural and Artificial Systems](https://direct.mit.edu/books/monograph/2574/Adaptation-in-Natural-and-Artificial-SystemsAn). Base de algoritmos geneticos.
- Bergstra et al., [Algorithms for Hyper-Parameter Optimization](https://proceedings.neurips.cc/paper/2011/hash/86e8f7ab32cfd12577bc2619bc635690-Abstract.html), 2011. Referencia para TPE.
- Watanabe, [Tree-Structured Parzen Estimator: Understanding Its Algorithm Components and Their Roles for Better Empirical Performance](https://arxiv.org/abs/2304.11127), 2023, com revisoes posteriores.
- Bergstra e Bengio, [Random Search for Hyper-Parameter Optimization](https://jmlr.org/papers/v13/bergstra12a.html), 2012. Referencia para o comparador aleatorio.
- Deb et al., [A Fast and Elitist Multiobjective Genetic Algorithm: NSGA-II](https://doi.org/10.1109/4235.996017), 2002.
- Auer, Cesa-Bianchi e Fischer, [Finite-time Analysis of the Multiarmed Bandit Problem](https://people.eecs.berkeley.edu/~russell/classes/cs294/s11/readings/Auer%2Bal:2002.pdf), 2002.

## Registro de discussao

- **2026-10-19 - MEL-001 a MEL-005:** propostas iniciais a partir das interfaces e do helper existentes; sem implementacao ou comparacao experimental.
- **2026-10-20 - realinhamento do catalogo:** o responsavel esclareceu que a pergunta central e comparar algoritmos na busca da melhor configuracao contra exhaustive. Aprovou registrar como inicio da discussao Random Sampling e KNN existentes, mais Pattern Search (MEL-006), Simulated Annealing (MEL-007), GA (MEL-008) e TPE (MEL-005). MEL-001 foi separado como infraestrutura; MEL-002 a MEL-004 foram adiadas fora da prioridade inicial. Essa aprovacao e do caminho de discussao, nao dos detalhes de implementacao. O proximo passo e acordar protocolo e detalhar as primeiras adicoes; nenhuma nova heuristica foi implementada ou validada.
