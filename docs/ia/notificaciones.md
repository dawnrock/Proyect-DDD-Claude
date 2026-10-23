# Notificaciones push por Telegram

Sustituyen a los avisos de Slack de curso-claude. Llegan al móvil cuando:

| Evento | Quién avisa |
| ------ | ----------- |
| Plan publicado en la issue | skill `plan-feature` (`scripts/notify.mjs`) |
| PR lista para revisar | skill `implement-plan` (`scripts/notify.mjs`) |
| Claude espera un permiso | hook `Notification` |
| Claude termina de responder (opcional) | hook `Stop`, si `TELEGRAM_NOTIFY_ON_STOP=1` |
| Falla la CI | `.github/workflows/ci.yml` |

Sin configuración no pasa nada: el script avisa de que no está configurado y las skills continúan.

## Configuración (una vez)

1. En Telegram, abre **@BotFather**, envía `/newbot` y guarda el **token** que te da.
2. Escribe cualquier mensaje a tu bot nuevo.
3. Obtén tu **chat id** (en PowerShell, sustituyendo el token):
   ```powershell
   (Invoke-RestMethod "https://api.telegram.org/bot<TOKEN>/getUpdates").result[-1].message.chat.id
   ```
4. Copia `.claude/settings.local.example.json` a `.claude/settings.local.json` (ignorado por git) y rellena `TELEGRAM_BOT_TOKEN` y `TELEGRAM_CHAT_ID`.
5. Prueba:
   ```bash
   TELEGRAM_BOT_TOKEN=... TELEGRAM_CHAT_ID=... node scripts/notify.mjs "Hola desde Proyect-DDD-Claude"
   ```
6. Para la CI, añade los mismos valores como secretos del repositorio:
   ```bash
   gh secret set TELEGRAM_BOT_TOKEN -R dawnrock/Proyect-DDD-Claude
   gh secret set TELEGRAM_CHAT_ID -R dawnrock/Proyect-DDD-Claude
   ```

El token da control sobre el bot: no lo pegues en el chat con Claude ni lo commitees.
