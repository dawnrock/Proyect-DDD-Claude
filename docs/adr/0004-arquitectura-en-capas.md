# 0004 · Arquitectura en capas de la fase 1

- **Estado:** aceptada · 2026-10-23

## Contexto

La fase 1 debe convertir el modelo de dominio del Lab 22 (solo `domain.model`) en una aplicación web mínima, y quedar como la primera de dos "fotografías" comparables: monolito en capas (`Monolithic`) frente a hexagonal (`Hexagonal`, ADR 0002).

El curso de DDD presenta la arquitectura en capas (**capítulo 14**) como la más común: presentación, aplicación, dominio y persistencia, con dependencias en un solo sentido, de arriba abajo, y cada capa accediendo idealmente solo a la inmediatamente inferior. También advierte de dos antipatrones:

- **Layer cake (capítulo 17):** capas que solo reenvían la llamada a la de abajo (por ejemplo, `findById` repetido en cada capa) sin aportar nada.
- **Anemic translation (capítulo 18):** cada capa define su propio tipo casi idéntico para el mismo concepto y obliga a traducir entre ellos en cada frontera.

Y propone, con cuidado, el **bypass entre capas (capítulo 18.1)**: permitir de forma deliberada que una capa se salte otra, vigilándolo con ArchUnit.

## Decisión

- Cuatro capas: `presentation` (`web` y `rest`) → `application` → `domain` (`model`, `service`) y `persistence`. Detalle en [fase-1-capas.md](../arquitectura/fase-1-capas.md).
- **El dominio está en el fondo y no depende de nadie.** `persistence` conoce el dominio (guarda agregados), y `application` usa las dos. Es un *bypass* deliberado (cap. 18.1): la aplicación carga y guarda directamente con la persistencia, en lugar de que el dominio la llame.
- **Clases concretas, sin interfaces de puerto.** La aplicación usa `InMemoryCheckingAccountRepository` directamente. Es el rasgo que diferencia esta fotografía de la hexagonal, donde el dominio declarará el puerto (`CheckingAccountRepository`) y la dependencia se invertirá.
- **Contra el anemic translation:** las vistas y commands de `application` (Immutables) son el único tipo intermedio. La presentación no define otro modelo del mismo concepto salvo los DTOs REST cuando el formato JSON lo exija, y todos usan los nombres del [glosario](../dominio/glosario.md).
- **Contra el layer cake:** no se crea una capa o método que solo reenvíe; las consultas simples no se duplican en cada capa por simetría.
- `presentation` solo conoce del dominio las excepciones (`DomainException` y subclases), para traducirlas a HTTP o a mensajes de pantalla; nunca agregados, entidades ni objetos de valor (regla D4).
- Las reglas de dependencia D1-D5 de `fase-1-capas.md` se verifican con ArchUnit (`LayeredArchitectureTests`, issue #12).

## Alternativas

- **Capas estrictas** (`application → domain → persistence`, sin saltos): obligaría al dominio a conocer la persistencia, rompiendo su aislamiento (regla D1 de `LayeredArchitectureTests`), o a añadir en el dominio métodos que solo reenvían al repositorio (layer cake). Descartada.
- **Hexagonal desde el principio:** es el objetivo de la fase 2; empezar por ella eliminaría la comparación entre las dos fotografías. Descartada para esta fase.
- **Interfaces de repositorio ya en la fase 1:** acercaría la fase 1 a la hexagonal y diluiría la diferencia que se quiere mostrar. Se introducen en la fase 2 (#15).

## Consecuencias

- La aplicación depende de la implementación concreta de la persistencia: sustituirla (por JPA, por ejemplo) obliga a tocar `application`. Es el coste que la fase 2 elimina con los puertos.
- Los tests de aplicación usan el repositorio en memoria real, no simulaciones.
- El bypass `application → persistence` es una excepción consciente: ArchUnit la permite explícitamente y prohíbe las demás (por ejemplo, `presentation → persistence`).
- El riesgo de anemic translation se vigila en la revisión (`revisor-ddd`) y con el glosario como fuente de los nombres.
