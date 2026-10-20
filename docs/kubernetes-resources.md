# Recursos Kubernetes

## Recursos customizados

Os schemas versionados do projeto estao em `crd/`:

- `Benchmark`: declara um experimento e a estrategia de selecao.
- `Workload`: descreve a carga que sera executada.
- `Scenario`: representa uma combinacao concreta de experimento.
- `Queue`: controla a execucao dos cenarios selecionados.
- `ResilientService`: descreve servicos alvo e configuracoes resilientes.

Os modelos correspondentes ficam em `resilience-bench/operator/src/main/java/io/resiliencebench/resources`. Os schemas tambem sao gerados pelo plugin Fabric8 durante a compilacao; confirme que a alteracao do modelo Java esta refletida no CRD versionado.

Use `mvn -B compile --file ./resilience-bench/operator/pom.xml` e inspecione os schemas em `resilience-bench/operator/target/classes/META-INF/fabric8/`. Compare o CRD gerado com o arquivo correspondente em `crd/` antes de atualizar o arquivo versionado. A compilacao nao instala schemas no cluster.

Java define o modelo e as anotacoes usadas pelo gerador; `crd/` e o contrato instalado pelos comandos de exemplo. Divergencia entre ambos precisa ser resolvida, pois o cluster pode validar um schema diferente daquele esperado pelo operador.

## Fluxo de mudanca em um recurso

Ao adicionar ou alterar um campo:

1. Atualize a spec Java e sua validacao/defaults, se aplicavel.
2. Atualize o CRD correspondente em `crd/` e confirme tipo, opcionalidade e descricao.
3. Revise os consumers do campo, incluindo factories, controller e execucao.
4. Atualize testes de modelo/serializacao e os samples que demonstram o campo.
5. Considere compatibilidade com manifests existentes; nao remova ou torne obrigatorio um campo sem migracao explicita.

## Aplicacao e exemplos

Para aplicar os schemas e o overlay de exemplo:

```bash
kubectl apply -f ./crd
kubectl apply -k ./samples/overlays/hipstershop
```

O overlay Hipster Shop e um exemplo portavel, nao uma configuracao universal de cluster. Leia [`samples/overlays/hipstershop/README.md`](../samples/overlays/hipstershop/README.md) antes de alterar imagem, agendamento ou infraestrutura.

O agendamento do overlay nao depende de labels de nodes especificos, mas seu armazenamento esta configurado para S3 e depende de bucket e Secret preparados pelo usuario. O caminho de saida tambem depende do script k6: alterar `STORAGE_TYPE` no operador nao altera o `handleSummary` do script. Consulte o README do overlay para o contrato entre Job, script e persistencia.

Recursos Istio sao usados quando o benchmark define padroes correspondentes; o cluster precisa ter as APIs e control plane Istio compativeis instalados para esses cenarios.

Ao mudar a execucao, confira selector do `ResilientService`, namespace e nomes de containers usados para atualizar Deployments. A descoberta de pods em `AbstractEnvironmentStep` usa o label `app` com o nome do Deployment. Preservar somente o selector do CR nao garante que a espera por prontidao encontre os pods.
