#!/usr/bin/env node
// Mide cuántos tokens gasta el agente buscador-referencias con y sin el grafo de Graphify (ADR 0010, #71).
//
// Uso:
//   node scripts/measure-graphify.mjs <A|B|C> [ejecuciones=2] [pregunta=1-5]
//     A · sin grafo: aparta graphify-out/ y lo restaura al terminar.
//     B · regla actual: el agente tal cual (grafo solo para dependencias).
//     C · grafo primero: variante del agente pasada con --agents; no toca .claude/.
//
// Cada pregunta corre en una sesión nueva sin interfaz (claude -p, Haiku), como en la #57. Tokens = input +
// cache creation + cache read + output del `usage` final. Imprime una línea por ejecución y la media por
// pregunta, y guarda las respuestas en measure-graphify-<config>.json, en MEASURE_OUT_DIR o en el temporal.
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

export const QUESTIONS = [
  'Where is CheckingAccount.withdraw used?',
  'Which classes depend on MoneyTransferService?',
  'Which input adapters use CheckingAccountCommands?',
  'Which class implements DomainEventPublisher, and who calls it?',
  'Where is rule R20 (high net worth threshold) defined, tested and documented?',
];

const AGENT_FILE = '.claude/agents/buscador-referencias.md';
const GRAPH_DIR = 'graphify-out';
const GRAPH_ASIDE = 'graphify-out.measure-aside';
const GRAPH_TOOLS = ['Bash(node scripts/graphify.mjs affected:*)', 'Bash(node scripts/graphify.mjs path:*)'];

/** Variante C: el mismo agente, pero con el grafo como primera fuente para cualquier búsqueda de código. */
export function graphFirstAgent(markdown) {
  const [, front, body] = markdown.match(/^---\n([\s\S]*?)\n---\n([\s\S]*)$/);
  const field = (name) => front.match(new RegExp(`^${name}: (.*)$`, 'm'))[1];
  const prompt = body.replace(
    /\*\*Preguntas de dependencias[\s\S]*?Para todo lo demás[^\n]*\n/,
    '**Búsquedas en el código Java: primero el grafo, siempre.** Si existe `graphify-out/graph.json`, tu primera ' +
      'llamada para cualquier pregunta sobre el código es al grafo (`node scripts/graphify.mjs query "<pregunta>"`, ' +
      '`explain "<X>"`, `affected "<X>"` o `path "<A>" "<B>"`), desde la raíz y un comando por llamada. Usa Grep ' +
      'solo para lo que el grafo no responda y para `docs/`, `.claude/` y `pom.xml`.\n',
  );
  return {
    'buscador-referencias-grafo': {
      description: field('description'),
      tools: ['Read', 'Grep', 'Glob', 'Bash(node scripts/graphify.mjs:*)'],
      model: field('model'),
      prompt,
    },
  };
}

/** Suma los tokens procesados de un `usage` de Claude Code. */
export function totalTokens(usage) {
  return ['input_tokens', 'cache_creation_input_tokens', 'cache_read_input_tokens', 'output_tokens']
    .reduce((sum, key) => sum + (usage?.[key] ?? 0), 0);
}

/** Lee la salida stream-json: el resultado final y los comandos de Graphify que lanzó el agente. */
export function parseStream(text) {
  const events = text.split('\n').filter((l) => l.startsWith('{')).map((l) => JSON.parse(l));
  const result = events.findLast((e) => e.type === 'result');
  const graphCalls = events
    .filter((e) => e.type === 'assistant')
    .flatMap((e) => e.message?.content ?? [])
    .filter((c) => c.type === 'tool_use' && c.name === 'Bash' && /graphify\.mjs/.test(c.input?.command ?? ''))
    .map((c) => c.input.command);
  return { result, graphCalls };
}

function runQuestion(config, question) {
  const args = ['-p', '--model', 'haiku', '--output-format', 'stream-json', '--verbose'];
  if (config === 'C') {
    args.push('--agents', JSON.stringify(graphFirstAgent(readFileSync(AGENT_FILE, 'utf8'))));
    args.push('--agent', 'buscador-referencias-grafo', '--allowedTools', 'Bash(node scripts/graphify.mjs:*)');
  } else {
    args.push('--agent', 'buscador-referencias', '--allowedTools', ...GRAPH_TOOLS);
  }
  args.push('--', question);
  const run = spawnSync('claude', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 600_000 });
  const { result, graphCalls } = parseStream(run.stdout ?? '');
  if (!result) throw new Error(`Sin resultado para "${question}": ${run.stderr?.slice(0, 500)}`);
  return {
    question,
    tokens: totalTokens(result.usage),
    costUsd: result.total_cost_usd,
    turns: result.num_turns,
    graphCalls,
    denied: (result.permission_denials ?? []).length,
    answer: result.result,
  };
}

function main() {
  const [config, runsArg = '2', only] = process.argv.slice(2);
  if (!['A', 'B', 'C'].includes(config)) {
    console.error('Uso: node scripts/measure-graphify.mjs <A|B|C> [ejecuciones=2] [pregunta=1-5]');
    process.exit(2);
  }
  const runs = Number(runsArg);
  const questions = only ? [QUESTIONS[Number(only) - 1]] : QUESTIONS;
  const asideGraph = config === 'A' && existsSync(GRAPH_DIR);
  if (config !== 'A' && !existsSync(`${GRAPH_DIR}/graph.json`)) {
    console.error('Falta el grafo: node scripts/graphify.mjs update .');
    process.exit(1);
  }
  if (asideGraph) renameSync(GRAPH_DIR, GRAPH_ASIDE);
  const records = [];
  try {
    for (const question of questions) {
      const tokens = [];
      for (let i = 1; i <= runs; i++) {
        const r = runQuestion(config, question);
        records.push({ config, run: i, ...r });
        tokens.push(r.tokens);
        console.log(`${config} · run ${i} · ${r.tokens} tokens · ${r.turns} turns · USD ${r.costUsd?.toFixed(4)} · ` +
          `graph calls ${r.graphCalls.length} · denied ${r.denied} · ${question}`);
      }
      const mean = Math.round(tokens.reduce((a, b) => a + b, 0) / tokens.length);
      console.log(`${config} · mean ${mean} tokens · ${question}`);
    }
  } finally {
    if (asideGraph) renameSync(GRAPH_ASIDE, GRAPH_DIR);
    const out = join(process.env.MEASURE_OUT_DIR ?? tmpdir(), `measure-graphify-${config}.json`);
    writeFileSync(out, JSON.stringify(records, null, 2));
    console.log(`Respuestas: ${out}`);
  }
}

if (process.argv[1] && import.meta.url.endsWith(process.argv[1].replace(/\\/g, '/').split('/').pop())) {
  main();
}
