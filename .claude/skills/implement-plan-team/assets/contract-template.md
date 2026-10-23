# Contrato: <funcionalidad>

- **Issue:** [#N](<URL>) · **Plan:** `docs/plans/AAAA-MM-DD-<slug>.md`
- **Estado:** borrador | acordado | congelado en `<hash>`
- **Acordado por:** backend-dev, web-dev (rondas: N)

## Servicio de aplicación

Fase 1: clase concreta en `application`. Fase 2: puertos de entrada `*Commands` / `*Queries`.

```java
public class <Nombre>Service {
    <Vista> <operación>(<Command> command);   // qué hace, en una frase
    List<<Vista>> <consulta>();
}
```

## Commands (entrada)

| Command (`*Def`) | Campo | Tipo | Obligatorio | Reglas |
| ---------------- | ----- | ---- | ----------- | ------ |
| `<Command>Def` | `holderName` | `String` | sí | no vacío |

## Vistas (salida)

| Vista (`*Def`) | Campo | Tipo | Notas |
| -------------- | ----- | ---- | ----- |
| `<Vista>Def` | `accountNumber` | `String` | IBAN normalizado |

Las vistas no exponen agregados ni entidades del dominio.

## Errores

| Excepción | Cuándo | Web (qué ve el usuario) | REST (código + `ProblemDetail.title`) |
| --------- | ------ | ----------------------- | ------------------------------------- |
| `InsufficientInitialDepositException` | depósito < mínimo | vuelve al formulario con el mensaje | 422 |
| `AccountNotFoundException` | id inexistente | página 404 propia | 404 |

## REST (fragmento de `docs/api/openapi.yaml`)

| Método y ruta | Petición | Respuesta |
| ------------- | -------- | --------- |
| `POST /api/accounts` | `OpenAccountRequest` | 201 + `AccountSummary` |

## Pantallas (web)

| Ruta | Usa | Muestra |
| ---- | --- | ------- |
| `GET /` | `<consulta>()` | tabla de cuentas |

## Cambios

<!-- Cada renegociación: fecha, quién la pidió, qué cambió y por qué. -->

- —
