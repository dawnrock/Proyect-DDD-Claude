#!/usr/bin/env node
// Envía un mensaje a Telegram con la Bot API.
//
// Uso:
//   node scripts/notify.mjs "texto del mensaje"
//   echo "texto" | node scripts/notify.mjs
//
// Variables de entorno (nunca en el repositorio; ver docs/ia/notificaciones.md):
//   TELEGRAM_BOT_TOKEN  token que da @BotFather
//   TELEGRAM_CHAT_ID    id del chat al que se envía
//   TELEGRAM_DISABLED   si vale 1, no envía nada (también silencia el hook notify-telegram.mjs)
//
// Sin variables, termina con código 0 y un aviso: las notificaciones son opcionales.
import { readFileSync } from 'node:fs';
import { request } from 'node:https';
import { fileURLToPath } from 'node:url';

export async function sendTelegram(text) {
  if (process.env.TELEGRAM_DISABLED === '1') {
    return { sent: false, reason: 'desactivado con TELEGRAM_DISABLED=1' };
  }
  const token = process.env.TELEGRAM_BOT_TOKEN;
  const chatId = process.env.TELEGRAM_CHAT_ID;
  if (!token || !chatId) {
    return { sent: false, reason: 'TELEGRAM_BOT_TOKEN o TELEGRAM_CHAT_ID no configurados' };
  }
  const body = JSON.stringify({ chat_id: chatId, text: text.slice(0, 4000), disable_web_page_preview: true });
  // node:https en lugar de fetch: fetch solo existe a partir de Node 18
  const { status, response } = await new Promise((resolve, reject) => {
    const req = request(`https://api.telegram.org/bot${token}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) },
      timeout: 8000,
    }, (res) => {
      let data = '';
      res.setEncoding('utf8');
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => resolve({ status: res.statusCode, response: data }));
    });
    req.on('timeout', () => req.destroy(new Error('tiempo de espera agotado')));
    req.on('error', reject);
    req.end(body);
  });
  if (status < 200 || status >= 300) {
    throw new Error(`Telegram respondió ${status}: ${response}`);
  }
  return { sent: true };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  let text = process.argv.slice(2).join(' ').trim();
  if (!text && !process.stdin.isTTY) text = readFileSync(0, 'utf8').trim();
  if (!text) {
    console.error('Uso: node scripts/notify.mjs "mensaje"');
    process.exit(1);
  }
  try {
    const r = await sendTelegram(text);
    console.log(r.sent ? 'Notificación enviada.' : `Notificación no enviada: ${r.reason}.`);
  } catch (e) {
    console.error(`Error al notificar: ${e.message}`);
    process.exit(1);
  }
}
