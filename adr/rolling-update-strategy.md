# Troca de variaveis de ambiente: nota historica de rolling update

**Status: nota historica.** A abordagem discutida abaixo nao descreve a verificacao executada atualmente e nao deve ser usada como garantia de rollout completo. A intencao original era priorizar a troca de configuracao em experimentos, aceitando downtime; ela nao confirma que todas as medidas propostas tenham sido implementadas.

## Comportamento atual verificado no codigo

`AbstractEnvironmentStep.updateVariablesDeployment` atualiza as variaveis do container selecionado no Deployment. Sua espera `waitUntilReady` usa um timeout de dois minutos e procura pods no namespace pelo label `app` igual ao nome do Deployment. Para cada pod observado, rejeita `deletionTimestamp` preenchido e verifica a condicao `Ready=True`.

Esse caminho nao verifica `observedGeneration`, `readyReplicas` ou a revisao dos pods, nem configura `maxSurge`/`maxUnavailable`. Portanto, nao garante que todos os pods observados pertencam a nova revisao do Deployment. A politica de rollout efetiva depende do manifesto do Deployment.

Fontes: [AbstractEnvironmentStep](../resilience-bench/operator/src/main/java/io/resiliencebench/execution/steps/AbstractEnvironmentStep.java) e [ordem de execucao](../docs/architecture.md). Uma mudanca para aguardar uma geracao/revisao especifica exige implementacao e verificacao proprias.

## Abordagem discutida originalmente (historico)

Na abordagem discutida, optou-se por remover os pods antigos o mais rapidamente possível e aguardar a criação dos novos pods, uma vez que o tempo mínimo de downtime não é um requisito crítico no cenário de teste. Esse cenário envolve a atualização de variáveis de ambiente em um Deployment Kubernetes e, em seguida, a execução de testes que dependem dessas novas configurações.

### Atributos discutidos para o rolling update

- `maxSurge: 0:` Esse atributo controla o número de pods adicionais que podem ser criados além do número de réplicas desejadas durante a atualização. Definir esse valor como 0 garante que nenhum pod extra será criado antes da remoção dos pods antigos, forçando a troca completa.
- `maxUnavailable:` Esse atributo define quantos pods podem estar indisponíveis durante a atualização. Um valor mais alto, como 2, permite a remoção simultânea de múltiplos pods, o que acelera o processo de atualização.

Essa configuração garante que os pods antigos serão removidos rapidamente e que o sistema aguardará pela criação dos novos pods antes de continuar o processo.

### Motivacao para esperar pelos novos pods

A remoção rápida dos pods antigos é essencial neste caso, pois o cenário envolve testes que dependem da nova configuração. Após a remoção dos antigos, o código deve esperar até que os novos pods estejam completamente prontos para garantir que a configuração alterada tenha sido aplicada corretamente.

Esperar pelos novos pods evita condições de corrida em que pods antigos poderiam ser erroneamente considerados como prontos, comprometendo a validação dos novos parâmetros configurados.

### Espera proposta por geracao do Deployment

A proposta original usava `waitUntilCondition` do Fabric8 para aguardar `observedGeneration >= newGeneration` e `readyReplicas` igual ao numero de replicas desejadas. Esses criterios aparecem no exemplo historico abaixo, mas nao no predicado atual do operador.

### Exemplo de codigo da proposta historica

```java
Deployment deployment = client.apps().deployments().inNamespace("default").withName("my-deployment").edit()
    .editSpec()
        .editTemplate()
            .editSpec()
                .editFirstContainer()
                    .addToEnv(new EnvVarBuilder().withName("MY_ENV_VAR").withValue("new-value").build())
                .endContainer()
            .endSpec()
        .endTemplate()
    .endSpec()
    .done();

Long newGeneration = deployment.getMetadata().getGeneration();

client.apps().deployments().inNamespace("default").withName("my-deployment")
    .waitUntilCondition(d -> d.getStatus().getObservedGeneration() >= newGeneration
        && d.getStatus().getReadyReplicas() != null 
        && d.getStatus().getReadyReplicas().equals(d.getSpec().getReplicas()), 10, TimeUnit.MINUTES);
```

O exemplo acima pertence a proposta historica; nao e o predicado usado pelo operador atual.

### Trade-off discutido originalmente

A decisão de maximizar a remoção dos pods antigos e aguardar pelos novos leva a um tempo de downtime mais longo, mas, como o objetivo é testar novos parâmetros, o tempo de indisponibilidade é aceitável e não prejudica o cenário. A principal prioridade é garantir que a atualização seja aplicada corretamente, permitindo que o teste ocorra com os parâmetros atualizados e sem riscos de executar com configurações antigas.
