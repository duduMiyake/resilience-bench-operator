# Infraestrutura e protocolo de avaliacao

Este documento trata de como comparar as heuristicas, nao de uma estrategia concorrente. O objetivo da discussao e encontrar configuracoes de maior score com menos avaliacoes, usando exhaustive como referencia. O estado de MEL-001 fica no [registro de melhorias](README.md); o catalogo concorrente esta em [heuristics.md](heuristics.md).

## MEL-001: Avaliador offline

**Problema:** comparar novas estrategias diretamente no cluster pode consumir muitas execucoes antes de identificar erros na selecao ou diferencas de qualidade da busca.

**Proposta minima:** usar um dataset exhaustive compativel como oraculo de resultados. O avaliador oferece as configuracoes e contextos declarados, chama as interfaces de selecao existentes e revela somente os resultados da configuracao escolhida. Agrega com `ConfigurationResultAggregator` e calcula o objetivo com `ObjectiveScorer`. Nao duplicar formulas em outro simulador.

**Limite de informacao:** a estrategia pode conhecer o espaco de parametros, mas nao os scores dos candidatos nao escolhidos. O avaliador pode calcular o otimo da referencia separadamente para medir o gap; esse valor nao pode orientar a proxima escolha. Uma configuracao so se torna avaliada depois de revelar todos os seus contextos esperados.

**Aceite proposto:** mesma seed e entradas reproduzem a ordem; nenhum candidato invalido; configuracoes distintas nao sao contabilizadas duas vezes; orcamento respeitado; resultados so sao fornecidos apos selecao; cobertura incompleta do exhaustive e detectada. Revisitas podem recuperar observacoes ja conhecidas, conforme a politica do algoritmo, sem revelar candidatos novos. Comparar as estrategias atuais antes de usar o avaliador para novas propostas.

**Limite:** replay de um dataset fixo mede o comportamento de busca nesse dataset. Variar seeds de selecao nao cria novas medicoes do sistema nem estima sozinho a variabilidade do cluster. A validacao de execucao real continua separada.

**A discutir:** formato de entrada/saida, conjunto de datasets, seeds e orcamentos de comparacao; qual parcela do trace pode ser reutilizada sem acoplar o avaliador ao Kubernetes.

## Protocolo comum de comparacao a acordar

- Mesmo indice de configuracoes, contextos, objetivo estruturado, normalizacao e orcamento por configuracao. Calcular o objetivo depois da agregacao das metricas, como no operador.
- Comparar com randomSampling e knnAdaptive; usar exhaustive compativel como referencia. Registrar seed e custo de inicializacao dentro do orcamento de cada algoritmo.
- Reportar melhor score por numero de configuracoes avaliadas, gap absoluto para o melhor exhaustive, avaliacoes ate um limiar definido previamente, custo computacional de selecao e quantidade de cenarios reais/cache. Usar gap relativo apenas quando o denominador for adequado, conforme o Visualizer.
- Usar varias seeds e apresentar dispersao e resultados individuais. Fixar seeds, orcamentos e criterios de analise antes da comparacao final. Ajustar hiperparametros em dados de desenvolvimento, separando os datasets usados para sustentar as conclusoes finais.
- Separar desempenho de busca em replay de tempo total no cluster. Nao usar tempo de leitura do cache como substituto do custo de executar carga/falhas. Repeticoes independentes de medicao exigem novas medicoes.
- Preservar testes e comportamento das estrategias existentes. Nas novas, conferir selector, validacoes, CRD, samples e trace/Visualizer. Nenhuma proposta deve mudar silenciosamente a unidade de decisao ou descartar contextos de carga/falha.

Seeds, orcamentos e limiares definitivos ainda nao foram acordados. Esta secao e um ponto de partida metodologico, nao um protocolo experimental validado.
## Contagem e interpretacao dos resultados

O exhaustive fornece o melhor score observado no dataset e nas condicoes da campanha, nao um otimo universal independente do ruido do sistema. Preserve os contextos e a ordem de agregacao das metricas seguida do calculo de score.

O orcamento principal proposto e o numero de configuracoes distintas avaliadas, incluindo inicializacao, populacao inicial e reinicios. Pattern Search, Simulated Annealing e GA podem revisitar estados ou gerar candidatos repetidos: reutilizar um resultado ja conhecido nao deve ser contado como uma nova avaliacao independente. Registre separadamente propostas/revisitas, custo de selecao e execucoes reais/cache. A estrategia nao pode conhecer o resultado de um candidato antes de seleciona-lo.

Defina tambem uma condicao de parada para a busca interna quando repeticoes nao produzirem configuracoes novas. Uma politica sem limite de propostas pode consumir tempo indefinidamente sem gastar o orcamento de avaliacoes. Esses detalhes precisam ser acordados para cada adaptacao antes da implementacao.

Para comparacoes, use orcamentos iguais com inicializacao incluida, varias seeds e datasets compativeis; registre configuracoes finais e curvas de melhor score por avaliacao. Regret/gap absoluto e calculado contra a referencia daquele dataset. Se houver medicoes repetidas do cluster, identifique-as separadamente dos replays do mesmo conjunto fixo de resultados.

## Discussao registrada

- **2026-10-20 - MEL-001:** classificado explicitamente como infraestrutura de avaliacao, fora da lista de heuristicas concorrentes. A ferramenta ainda nao esta implementada. Formato de entrada/saida, seeds, datasets e orcamentos continuam abertos.
