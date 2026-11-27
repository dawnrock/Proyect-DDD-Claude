---
name: advance-issues
description: Avanza de forma autónoma por las issues abiertas de la fase activa, una tras otra y en el orden más coherente (bloqueos y dependencias primero), sin pedir confirmación entre pasos. Para cada issue planifica con plan-feature, implementa con implement-plan o implement-plan-team y fusiona la PR con los checks en verde. Solo se detiene a preguntar cuando hace falta una decisión concreta del usuario, con AskUserQuestion y opciones. Úsala cuando se pida avanzar, seguir o continuar con las issues, o trabajar en ellas sin esperar confirmación.
argument-hint: "[números de issue, opcional: limita y ordena el lote]"
---

Avanza por las issues sin esperar la confirmación del usuario entre pasos. Acuerdo con el usuario del 2026-11-17: **él no aprueba cada plan ni cada paso; solo se le pregunta cuando hace falta una decisión suya**. Todo lo demás de `plan-feature`, `implement-plan` e `implement-plan-team` (TDD, un commit por tarea, `revisor-ddd`, estado del proyecto, fusión con los checks en verde) aplica igual.

## 1. Elegir el lote y el orden

1. Lee `docs/ia/estado-del-proyecto.md` (su "Next step" ya propone un orden) y lista las issues abiertas de la fase activa (`fase:2-hexagonal` mientras `Hexagonal` sea la rama activa). Si el usuario pasó números, el lote son solo esas issues.
2. Ordénalas por coherencia, en este orden de criterios:
   1. Lo que **bloquea** a otras issues (lo dice su cuerpo, el de la bloqueada o `docs/ia/pendientes.md`).
   2. Las dependencias declaradas ("después de #n", "requiere #n") y el orden del "Next step".
   3. Los arreglos (`fix`) antes que las funcionalidades nuevas; lo pequeño antes que lo grande, a igualdad de lo anterior.
3. Anuncia en una línea el orden elegido y por qué. No esperes respuesta: empieza por la primera.

## 2. Para cada issue

1. **Planifica** siguiendo `plan-feature` completo (plan en `docs/plans/`, comentario en la issue). En su paso 8 **no te detengas** a pedir confirmación: resume el plan en dos líneas y sigue.
2. **Decisiones:** si el plan tiene preguntas abiertas que cambian lo que se implementa, pregúntalas con `AskUserQuestion` (2-4 opciones, la recomendada primero y marcada "(Recomendado)", cada una autoexplicativa: el usuario puede verlas desde el móvil sin el resto de la conversación). Agrupa en una sola llamada todas las de esa issue. Lo que tenga una opción claramente mejor y reversible no se pregunta: decídelo, y anótalo en el plan y en el informe.
3. **Implementa** con `implement-plan`, o con `implement-plan-team` si la issue tiene la etiqueta `equipo` o el plan tiene `T0 · Contract`. Las PRs que se abren aquí cuentan como **pedidas por el usuario**: se fusionan con los checks en verde (paso 7 bis de `implement-plan`).
4. Espera a la fusión (sin `sleep`: suscripción a la PR) antes de pasar a la siguiente issue, que debe partir de la rama base ya actualizada. Si el entorno impone una rama de desarrollo (sesiones en la nube), todas las issues del lote usan esa rama, una tras otra: tras cada fusión se reinicia desde la base actualizada (`implement-plan` paso 2 bis) y cada issue tiene su propia PR.
5. Si durante la issue aparece un problema nuevo fuera de su alcance, no lo metas en ella: abre una issue nueva con su evidencia, decide si va antes en el orden (por ejemplo, si bloquea a otra) y sigue.

## 3. Cuándo parar y preguntar

Pregunta con `AskUserQuestion` (no avances por tu cuenta) cuando la siguiente acción:

- toque `CLAUDE.md` o `.claude/` (necesita la aprobación expresa del usuario; ofrécele aplicarlo o dejarlo como propuesta en la issue);
- añada una dependencia nueva al `pom.xml` (además del ADR que exigen las restricciones);
- toque `Monolithic` (congelada salvo excepción autorizada) o vaya contra `master` (nunca);
- vaya a commitear un diagrama (se enseñan antes al usuario);
- cambie una decisión ya registrada en `docs/ia/decisiones.md` o un contrato congelado;
- elija entre alternativas de diseño con consecuencias distintas para el usuario o la API (como las preguntas de #108).

**Detente** (sin pasar a la siguiente issue) y explica el motivo y lo que necesitas si:

- un check de la PR falla y no encuentras la causa tras arreglar lo que es de la PR;
- un criterio de aceptación no se puede cumplir sin salirse del plan (paso 5 de `implement-plan`);
- el usuario dice que pares o que deje una PR abierta.

## 4. Al terminar el lote

Cuando no queden issues en el lote (o te detengas), informa en español: issues cerradas con su PR, issues nuevas abiertas, decisiones que tomaste tú (y por qué) y las que tomó el usuario, y el siguiente paso.
