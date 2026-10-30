#!/usr/bin/env node
// Lanzador portable de Graphify (ADR 0010). Lo usan el agente buscador-referencias y las skills para
// no depender de que el ejecutable `graphify` esté en el PATH.
//
// Uso (mismos argumentos que `graphify`):
//   node scripts/graphify.mjs update .                                  # regenera el grafo (sin LLM)
//   node scripts/graphify.mjs affected "MoneyTransferService"           # quién depende de una clase
//   node scripts/graphify.mjs path "TransferController" "CheckingAccount"
//
// Busca `graphify` en el PATH y, si no está, el módulo con `py -m graphify` (Windows) o
// `python3 -m graphify`. Si Graphify no está instalado, sale con 1 y un aviso: quien lo llama sigue
// con Grep. Fuerza GRAPHIFY_BACKEND=none si no está definido: el grafo nunca debe gastar tokens.
import { spawnSync } from 'node:child_process';

const args = process.argv.slice(2);
const env = { ...process.env, GRAPHIFY_BACKEND: process.env.GRAPHIFY_BACKEND ?? 'none' };
const python = process.platform === 'win32' ? 'py' : 'python3';

function run(cmd, cmdArgs) {
  return spawnSync(cmd, cmdArgs, { stdio: 'inherit', env, shell: false });
}

let result = run('graphify', args);
if (result.error?.code === 'ENOENT') {
  const probe = spawnSync(python, ['-c', 'import graphify'], { stdio: 'ignore' });
  if (probe.status === 0) {
    result = run(python, ['-m', 'graphify', ...args]);
  } else {
    process.stderr.write(
      'Graphify no está instalado: `py -m pip install --user graphifyy` (Windows) o ' +
      '`pip install graphifyy`. Mientras tanto, busca con Grep/Glob (ver docs/adr/0010).\n');
    process.exit(1);
  }
}
process.exit(result.status ?? 1);
