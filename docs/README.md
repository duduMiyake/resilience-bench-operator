# Documentacao do projeto

Esta pasta concentra a documentacao funcional e tecnica do ResilienceBench-Operator.

## Leitura recomendada

1. [AGENTS.md](../AGENTS.md): regras de implementacao, arquitetura esperada e checklist para alteracoes.
2. [LOCAL_RUN.md](../LOCAL_RUN.md): como criar o cluster local, compilar a imagem e aplicar os manifests.
3. [heuristicas-de-selecao.md](heuristicas-de-selecao.md): como `exhaustive`, `randomSampling` e `knnAdaptive` selecionam cenarios.
4. [resultados-e-cache.md](resultados-e-cache.md): como os resultados sao gravados hoje e como deve funcionar o cache/replay por cenario.
5. [../POSSIBILIDADES_HEURISTICAS.md](../POSSIBILIDADES_HEURISTICAS.md): ideias e backlog para novas heuristicas.

## Organizacao

- Documentos em `docs/` descrevem comportamento atual e uso esperado.
- Documentos em `adr/` registram decisoes tecnicas ou notas de arquitetura.
- Arquivos em `samples/` mostram manifests aplicaveis com `kubectl`.

Ao alterar comportamento de selecao, execucao ou resultados, atualize o documento funcional correspondente em `docs/`.