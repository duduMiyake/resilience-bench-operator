# Desenvolvimento

## Requisitos

- JDK 17 e Maven para o operador.
- Node.js e npm compativeis com a versao Angular declarada em `resilience-bench/visualizer/package.json` para o Visualizer.
- Docker para construir a imagem do operador.
- `kubectl` e um cluster Kubernetes para instalar CRDs e executar experimentos.

## Operador

Na raiz do repositorio:

```bash
mvn -B test --file ./resilience-bench/operator/pom.xml
mvn -B package --file ./resilience-bench/operator/pom.xml
```

O workflow de CI roda os testes Maven em JDK 17 e empacota a imagem; atualmente dispara em push/PR para `main`. Os testes unitarios ficam em `resilience-bench/operator/src/test/java`; o POM configura Surefire para excluir `*E2ETest` e Failsafe para inclui-los em `verify` quando existirem. Isso nao prepara automaticamente um cluster nem comprova cobertura E2E.

`package` tambem executa o Jib `dockerBuild`, configurado no POM, e precisa de Docker disponivel. Para apenas compilar Java e gerar schemas, use `mvn -B compile --file ./resilience-bench/operator/pom.xml`. Nao trate `package` como uma operacao que produz somente um JAR.

Para trabalhar em um comportamento por vez, por exemplo o score objetivo:

```bash
mvn -B -Dtest=ObjectiveScorerTest test --file ./resilience-bench/operator/pom.xml
```

Use os testes existentes como ponto de partida: `resources/selection` cobre estrategias e agregacao; `execution/resultcache` cobre chaves, paths, cache e traces; `DefaultQueueExecutorTest` e `DefaultScenarioExecutorTest` exercitam execucao com mocks. Passar testes com mocks nao comprova funcionamento num cluster real.

O `Makefile` em `resilience-bench/` tem efeitos de cluster: o alvo `test` aplica CRDs e `clean` apaga recursos em todos os namespaces. Prefira os comandos Maven diretos para trabalho local e leia o alvo antes de usa-lo.

O exemplo Hipster Shop atual usa S3 e um Secret `aws-credentials` no namespace `hipstershop`, com as chaves `access-key-id` e `secret-access-key`. Antes de iniciar o operador, configure bucket, regiao, prefixo e credenciais para o seu ambiente, conforme o [README do overlay](../samples/overlays/hipstershop/README.md). O `kubectl apply` abaixo instala os manifests, mas nao provisiona bucket nem cria esse Secret.

Para instalar recursos e o exemplo Hipster Shop num cluster com esses requisitos preparados:

```bash
kubectl apply -f ./crd
kubectl apply -k ./samples/overlays/hipstershop
```

Consulte [recursos Kubernetes](kubernetes-resources.md) e o [README do overlay](../samples/overlays/hipstershop/README.md) antes de adaptar o exemplo ao cluster.

## Visualizer

```bash
cd resilience-bench/visualizer
npm ci
npm start
```

Comandos `npm test` e `npm run build` tambem estao disponiveis. O [README do Visualizer](../resilience-bench/visualizer/README.md) detalha requisitos de Windows e WSL.

## Antes de abrir uma mudanca

- Rode os testes do modulo afetado.
- Ao alterar comportamento, atualize o documento funcional correspondente.
- Ao alterar specs de CRDs, confira o schema, exemplos e compatibilidade descritos em [recursos Kubernetes](kubernetes-resources.md).
- Nao aplique manifests ou execute testes de cluster sem confirmar o contexto ativo do `kubectl`.

Para o fluxo com agentes e a escolha de documentos por tarefa, consulte [desenvolvimento com LLM](development-with-llm.md). Para preparar e acompanhar rodadas no cluster, consulte [execucao de experimentos](running-experiments.md), incluindo as informacoes a preservar para reproducao de um estudo. Dados particulares de acesso e operacao ficam nas notas locais ignoradas pelo Git.
