# Telegram push notifications

They replace the Slack notices of curso-claude. They reach the phone when:

| Event | Who sends it |
| ----- | ------------ |
| Plan published on the issue | `plan-feature` skill (`scripts/notify.mjs`) |
| PR ready for review | `implement-plan` skill (`scripts/notify.mjs`) |
| Claude is waiting for a permission | `Notification` hook |
| Claude finishes answering (optional) | `Stop` hook, if `TELEGRAM_NOTIFY_ON_STOP=1` |
| CI fails | `.github/workflows/ci.yml` |

Without configuration nothing happens: the script says it is not configured and the skills carry on.

## Configuration (once)

1. In Telegram, open **@BotFather**, send `/newbot` and keep the **token** it gives you.
2. Send any message to your new bot.
3. Get your **chat id** (in PowerShell, replacing the token):
   ```powershell
   (Invoke-RestMethod "https://api.telegram.org/bot<TOKEN>/getUpdates").result[-1].message.chat.id
   ```
4. Copy `.claude/settings.local.example.json` to `.claude/settings.local.json` (ignored by git) and fill in `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID`.
5. Test it:
   ```bash
   TELEGRAM_BOT_TOKEN=... TELEGRAM_CHAT_ID=... node scripts/notify.mjs "Hola desde Proyect-DDD-Claude"
   ```
6. For CI, add the same values as repository secrets:
   ```bash
   gh secret set TELEGRAM_BOT_TOKEN -R dawnrock/Proyect-DDD-Claude
   gh secret set TELEGRAM_CHAT_ID -R dawnrock/Proyect-DDD-Claude
   ```

The token gives control over the bot: do not paste it in the chat with Claude or commit it.

## Turning the notices off without deleting the configuration

With `TELEGRAM_DISABLED=1`, `scripts/notify.mjs` sends nothing (it answers "Notificación no enviada: desactivado…") and neither do the `Notification` and `Stop` hooks, because they use the same script. It is set in the `env` block of `.claude/settings.local.json` or `~/.claude/settings.json`, and applies when a new Claude Code session starts. To turn the notices back on, remove the variable. It does not affect CI, which does not set it.
