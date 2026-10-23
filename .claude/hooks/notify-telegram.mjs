// Hook de Notification y Stop: avisa por Telegram cuando Claude necesita permiso
// o termina de responder. Si faltan TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID no hace nada.
// El aviso de Stop solo se envía si TELEGRAM_NOTIFY_ON_STOP=1 (evita ruido en sesiones cortas).
import { readFileSync } from 'node:fs';
import { basename } from 'node:path';
import { sendTelegram } from '../../scripts/notify.mjs';

let input = {};
try { input = JSON.parse(readFileSync(0, 'utf8') || '{}'); } catch { process.exit(0); }

const project = basename(process.env.CLAUDE_PROJECT_DIR || input.cwd || 'proyecto');
let text;
if (input.hook_event_name === 'Notification') {
  text = `🔔 ${project}: ${input.message || 'Claude necesita tu atención'}`;
} else if (input.hook_event_name === 'Stop' && process.env.TELEGRAM_NOTIFY_ON_STOP === '1') {
  const last = (input.last_assistant_message || '').trim().slice(0, 300);
  text = `✅ ${project}: Claude ha terminado.${last ? `\n\n${last}` : ''}`;
}

if (text) {
  try { await sendTelegram(text); } catch { /* un aviso fallido nunca bloquea la sesión */ }
}
process.exit(0);
