---
name: ddd-building-block
description: Crea o modifica un bloque táctico de DDD en el dominio Java (objeto de valor, entidad, agregado, evento de dominio, factoría o servicio de dominio) siguiendo las reglas del curso y del proyecto, con su test JUnit. Úsala cuando se pida crear, añadir, modelar o cambiar un value object, una entidad, un agregado, un evento de dominio o una regla de negocio en domain.model o domain.service.
argument-hint: <tipo de bloque> <nombre> [descripción de la regla de negocio]
allowed-tools: Read, Grep, Glob, Edit, Write, Bash(./mvnw:*)
---

Crea o modifica el bloque de dominio pedido: $ARGUMENTS

## Procedimiento

1. **Clasifica el bloque.** Antes de escribir nada, lee `references/reglas-ddd.md` y decide si es un objeto de valor, una entidad, un agregado, un evento, una factoría o un servicio de dominio. Si la petición no encaja claramente (por ejemplo, algo con identidad que se pide como VO), explícalo y propón la clasificación correcta.
2. **Lenguaje ubicuo.** Busca el término en `docs/dominio/glosario.md`. Usa ese nombre en inglés en el código y añade el término al glosario si es nuevo.
3. **Lombok y record.** En el dominio no se usa Lombok (ADR 0009): accesores, `equals` y `hashCode` se escriben a mano, y los objetos de valor y los eventos son `record` (ADR 0028). Detalles en `references/lombok-records.md`.
4. **Genera el código** partiendo de la plantilla que corresponda en `templates/`:
   - Objeto de valor → `templates/ValueObject.java.template` (un `record` con validación en el constructor compacto).
   - Entidad o raíz de agregado → `templates/Entity.java.template` (constructor privado + factoría estática, igualdad por id).
   - Test → `templates/BuildingBlockTests.java.template`.
   Sustituye los marcadores `{{Name}}`, `{{package}}`, `{{field}}`… y elimina los que no apliquen.
5. **TDD.** Escribe primero el test (rojo), luego el código (verde), siguiendo `src/test/CLAUDE.md`. Una regla de negocio = un test.
6. **Verificación.** Ejecuta:
   ```bash
   ./mvnw verify
   ```
   ArchUnit (`HexagonalArchitectureTests`, `DomainIsolationTests`) comprueba que el dominio solo depende del JDK (ni Spring, ni `jakarta`, ni Lombok, por `@lombok.Generated`), que su estado es privado y que no tiene setters públicos (ADR 0026). **No des la tarea por terminada si falla.**
