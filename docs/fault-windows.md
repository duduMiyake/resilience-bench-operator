# Injeção de falhas por janelas

O Operator mantém o comportamento por variável de ambiente (`FAULT_PERCENTAGE`) quando `windows` é omitido. Com `windows`, o Operator altera o filtro de falha do Envoy em runtime, sem reiniciar o serviço. O comportamento temporal segue o [ResilienceBench de referência](https://github.com/cmendesce/resilience-bench/pull/6).

Em `Benchmark.spec.scenarios[].fault`, por exemplo:

```yaml
fault:
  provider: envoy
  services: [paymentservice]
  percentages: [50, 100]
  type: abort
  grpcStatus: 14 # UNAVAILABLE
  windows:
    - start: 2000
      duration: 5000
  repeat: true
```

O exemplo gera cenários separados de 50% e 100% **dentro** da janela: 2 s sem injeção, 5 s com falha, e repete até o fim da carga. Fora de cada janela, a taxa de injeção é zero. Cada `start` é uma espera desde o final da janela anterior (ou desde o início do cenário na primeira). Sem `repeat`, a lista roda uma única vez. Intervalos são inteiros em milissegundos, com `start >= 0` e `duration > 0`. Para repetir, pelo menos um intervalo saudável deve ser positivo.

Para `abort` use `status` (HTTP) ou `grpcStatus` (gRPC); para `type: delay`, use o campo `duration` diretamente dentro de `fault` como o atraso de cada requisição atingida. Esse atraso é independente da duração da janela. Carga, taxas e conectores continuam sendo definidos pelo benchmark; as heurísticas continuam escolhendo apenas configurações.

## Pré-requisitos e limites

- Use uma aplicação de teste isolada, com uma réplica pronta por serviço alvo. Cada `ResilientService` precisa da anotação `resiliencebench.io/envoy-service` indicando um Service ClusterIP seletor no mesmo namespace, com uma porta administrativa nomeada `envoy` que alcance a réplica. Restrinja o acesso administrativo do Envoy à infraestrutura do benchmark; não exponha a API para fora do cluster.
- O Envoy do alvo precisa de `admin_layer` e do filtro HTTP de injeção configurado para o tipo de falha. O Operator envia as chaves de runtime padrão do Envoy; a API deve permitir POST em `/runtime_modify` e GET em `/runtime` para conferir o estado inicial. O YAML do Hipster Shop não comprova que as imagens atuais reconhecem essas chaves: confirme com tráfego de teste antes de coletar dados. O exemplo acima aponta apenas para o `paymentservice`; o `checkoutservice` do overlay não expõe porta administrativa.
- Use um job com k6 que suporte `run --paused` e a REST API local. O Operator usa `pods/portforward` para alcançar a API ligada a `127.0.0.1` no pod; nenhum Service do k6 é criado. A linha do tempo começa imediatamente antes de liberar o k6 (o tempo de rede e a inicialização da carga ainda podem deslocar a primeira chamada; se o script tiver `setup()`, esse tempo conta antes da primeira iteração). O Operator espera até 5 minutos pelo início do k6 e o Job temporal tem prazo de 6 horas, sem repetição automática. Configure carga suficiente para atravessar pelo menos uma lista completa e também medir a recuperação.
- O resultado e o trace registram `faultWindowsStarted`, `faultWindowsCompleted`, `scenarioDurationMs` e `faultTransitions` (tempo planejado e confirmado). Uma resposta 2xx da API administrativa confirma a operação, não o efeito real sobre o cliente. Se uma transição não for confirmada, a carga terminar cedo, o Job falhar ou não houver resultado, o cenário marca a fila como falha e não agrega nem reutiliza o resultado. Os perfis temporais têm uma chave de cache distinta do modelo antigo.
- A limpeza tenta zerar a falha e cancelar as janelas restantes. Se o reset não puder ser confirmado, confira o Envoy e restaure o estado antes de repetir. Um desligamento forçado do operador pode interromper a limpeza. Na limpeza, o Operator zera a injeção e remove os overrides usados pelo modo temporal, devolvendo o controle à configuração de base. Reserve essas chaves administrativas para o Operator durante os ensaios. Como o Operator reutiliza a fila existente, para aplicar um perfil novo siga o [procedimento de nova rodada](running-experiments.md).

## Validação no cluster de teste

1. Use um único `paymentservice`, sem retry, a 100%, com uma janela de abort e duração da carga que cubra a recuperação. Compare sucesso, `UNAVAILABLE` durante a janela, e sucesso novamente (sem reinício do pod). Verifique as transições confirmadas e o tráfego, não apenas o status da API.
2. Repita com duas janelas de início relativo e `repeat: true`; confira os intervalos saudáveis e o número de ocorrências. Teste também 50%, atraso e o cenário legado sem janelas.
3. Cancele um Job durante a janela e simule indisponibilidade da API administrativa no ambiente de teste. Confirme que o Operator não agrega nem grava no cache uma execução inválida.
