# Proposito e escopo

## Proposito

O ResilienceBench-Operator automatiza experimentos de resiliencia em aplicacoes de microsservicos executadas no Kubernetes. Ele transforma uma declaracao de benchmark em cenarios concretos, aplica configuracoes e falhas, executa carga e registra resultados para analise.

O projeto estende a abordagem do ResilienceBench original para execucao declarativa em clusters Kubernetes, usando Custom Resource Definitions (CRDs) e um operador.

## Problema que resolve

Comparar configuracoes de resiliencia manualmente exige preparar repetidamente a aplicacao, alterar parametros, aplicar falhas, executar carga e organizar resultados. O operador coordena esse ciclo para que experimentos declarados possam ser executados e comparados de forma reproduzivel.

## Escopo

- Declarar benchmarks, workloads, cenarios, filas e servicos resilientes como recursos Kubernetes.
- Expandir um benchmark em combinacoes de carga, falha e configuracao de resiliencia.
- Selecionar configuracoes para avaliacao com estrategias exhaustive, random sampling e k-NN adaptativa.
- Preparar o ambiente do cenario, aguardar prontidao, executar carga com k6 e coletar resultados.
- Armazenar artefatos localmente ou em S3, com suporte a cache/replay configurado no benchmark.
- Visualizar resultados e traces com a aplicacao Angular incluida no repositorio.

## Fora do escopo

- Hospedar ou provisionar o cluster Kubernetes do usuario.
- Gerenciar o ciclo de vida completo da aplicacao alvo ou substituir suas ferramentas de deploy.
- Fornecer uma interface web de controle do operador; o Visualizer e uma ferramenta local de leitura de artefatos.
- Garantir que resultados de execucoes distintas sejam comparaveis sem controle experimental equivalente. Cache/replay reutiliza uma medicao anterior, nao cria uma nova medicao.

## Usuarios esperados

Engenheiros e pesquisadores que precisam definir e executar experimentos de resiliencia em aplicacoes de microsservicos no Kubernetes e analisar os resultados obtidos.
