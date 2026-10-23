---
name: ddd-building-block
description: Crea o modifica un bloque táctico de DDD en el dominio Java (objeto de valor, entidad, agregado, evento de dominio, factoría o servicio de dominio) siguiendo las reglas del curso y del proyecto, con su test JUnit. Úsala cuando se pida crear, añadir, modelar o cambiar un value object, una entidad, un agregado, un evento de dominio o una regla de negocio en domain.model o domain.service.
argument-hint: <tipo de bloque> <nombre> [descripción de la regla de negocio]
allowed-tools: Read, Grep, Glob, Edit, Write, Bash(./mvnw:*), Bash(node .claude/skills/ddd-building-block/scripts/check-domain.mjs:*)
---

Crea o modifica el bloque de dominio pedido: $ARGUMENTS

## Procedimiento

1. **Clasifica el bloque.** Antes de escribir nada, lee `references/reglas-ddd.md` y decide si es un objeto de valor, una entidad, un agregado, un evento, una factoría o un servicio de dominio. Si la petición no encaja claramente (por ejemplo, algo con identidad que se pide como VO), explícalo y propón la clasificación correcta.
2. **Lenguaje ubicuo.** Busca el término en `docs/dominio/glosario.md`. Usa ese nombre en inglés en el código y añade el término al glosario si es nuevo.
3. **Lombok e Immutables.** Si vas a usar anotaciones, lee `references/lombok-immutables.md`: en el dominio solo se permiten `@Getter` y `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` (y ninguna anotación de Immutables).
4. **Genera el código** partiendo de la plantilla que corresponda en `templates/`:
   - Objeto de valor → `templates/ValueObject.java.template` (un `record` con validación en el constructor compacto).
   - Entidad o raíz de agregado → `templates/Entity.java.template` (constructor privado + factoría estática, igualdad por id).
   - Test → `templates/BuildingBlockTests.java.template`.
   Sustituye los marcadores `{{Name}}`, `{{package}}`, `{{field}}`… y elimina los que no apliquen.
5. **TDD.** Escribe primero el test (rojo), luego el código (verde), siguiendo `src/test/CLAUDE.md`. Una regla de negocio = un test.
6. **Verificación.** Ejecuta:
   ```bash
   node .claude/skills/ddd-building-block/scripts/check-domain.mjs
   ./mvnw verify
   ```
   El script comprueba que el dominio no importa Spring/`jakarta`/`org.immutables`, no usa anotaciones de Lombok prohibidas y no tiene setters públicos. **No des la tarea por terminada si alguno de los dos falla.**
