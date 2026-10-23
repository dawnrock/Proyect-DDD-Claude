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
//
// Sin variables, termina con código 0 y un aviso: las notificaciones son opcionales.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

export async function sendTelegram(text) {
  const token = process.env.TELEGRAM_BOT_TOKEN;
  const chatId = process.env.TELEGRAM_CHAT_ID;
  if (!token || !chatId) {
    return { sent: false, reason: 'TELEGRAM_BOT_TOKEN o TELEGRAM_CHAT_ID no configurados' };
  }
  const res = await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ chat_id: chatId, text: text.slice(0, 4000), disable_web_page_preview: true }),
  });
  if (!res.ok) {
    throw new Error(`Telegram respondió ${res.status}: ${await res.text()}`);
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
