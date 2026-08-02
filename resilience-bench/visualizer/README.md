# ResilienceBench Visualizer

Aplicacao Angular executada inteiramente no navegador para inspecionar arquivos
`trace.json` e `results.json` produzidos pelo ResilienceBench. O Visualizer nao
depende do Operator, do kind ou de um cluster Kubernetes para funcionar.

## Requisitos

- Node.js 22 ou outra versao aceita pelo Angular definido em `package.json`.
- npm.

## Executar no WSL

Use uma instalacao do Node nativa do Linux. Antes de instalar as dependencias,
confirme o ambiente:

```bash
which node
which npm
node -p "process.platform"
```

Os caminhos de `node` e `npm` nao devem apontar para `/mnt/c` ou terminar em
`.exe`, e o ultimo comando deve imprimir `linux`.

Em seguida:

```bash
cd ~/projects/resilienceBench/resilience-bench-operator/resilience-bench/visualizer
rm -rf node_modules
npm ci
npm start
```

Abra `http://localhost:4200` no navegador do Windows. Se o encaminhamento de
`localhost` do WSL nao estiver disponivel, inicie o servidor com:

```bash
npm start -- --host 0.0.0.0
```

`npm start` executa o script local `ng serve`. Tambem e possivel usar
`npx ng serve`; isso evita depender de uma instalacao global do Angular CLI.

### Instalar Node no WSL com NVM

Caso `node` e `npm` nao estejam instalados nativamente no WSL, instale o NVM
seguindo as instrucoes oficiais e execute:

```bash
source ~/.bashrc
nvm install 22
nvm use 22
nvm alias default 22
```

## Executar no Windows

O Visualizer tambem pode ser executado somente no Windows, mas nesse caso o
checkout deve estar em um caminho do Windows, por exemplo
`C:\Users\USUARIO\projects\resilience-bench-operator`. No PowerShell:

```powershell
cd C:\Users\USUARIO\projects\resilience-bench-operator\resilience-bench\visualizer
npm ci
npm start
```

Nao execute o Node do Windows em um checkout acessado por
`\\wsl.localhost\...`. Tambem nao compartilhe o mesmo `node_modules` entre
Windows e WSL, pois dependencias como Rollup e esbuild possuem binarios
especificos para cada sistema operacional.

## Comandos

```bash
npm start
npm test
npm run build
```

## Solucao de problemas

- Referencias a `cmd.exe`, `C:\Users\...` ou `\\wsl.localhost` durante um
  comando iniciado no WSL indicam que o Node do Windows esta sendo usado.
- Erros relacionados a `@rollup/rollup-win32-*`, `esbuild`, caminhos UNC ou
  `EPERM` normalmente indicam mistura entre Windows e WSL. Remova
  `node_modules` e execute `npm ci` novamente no ambiente escolhido.
- Avisos de pacote `deprecated` nao interrompem a instalacao. Verifique a linha
  `npm error` para identificar uma falha real.

## Arquivos aceitos

- `trace.json` obrigatorio: trace heuristico v2 ou trace exhaustive legado.
- `results.json` opcional: resultados da rodada ou conjunto exhaustive usado
  como referencia.

Todo o processamento ocorre localmente no navegador; os arquivos nao sao
enviados para um servidor.
