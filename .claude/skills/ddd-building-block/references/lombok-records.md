# Lombok y record en este proyecto

## record: los objetos de datos

Los objetos de datos son `record` ([ADR 0028](../../../../docs/adr/0028-records-for-data-carriers.md)): objetos de valor y eventos del dominio, commands, queries y vistas de `application`, y peticiones y respuestas de los adaptadores. No hay Immutables.

- **Validación y valores por defecto en el constructor compacto.** Primero los obligatorios (`Objects.requireNonNull(x, "x")`), luego los valores por defecto (`x = Objects.requireNonNullElse(x, "")`), luego las reglas en orden (la primera que falla es la que se informa). Las listas se copian con `List.copyOf`.
- **Construcción: factorías estáticas y el constructor canónico.** Una factoría con nombre para cada caso común, que solo rellena los valores por defecto documentados; para el resto, el constructor canónico (un `null` toma el valor por defecto):
  ```java
  public record DepositCommand(UUID accountId, BigDecimal amount, String currency) {

      public DepositCommand {
          Objects.requireNonNull(accountId, "accountId");
          Objects.requireNonNull(amount, "amount");
          currency = Objects.requireNonNullElse(currency, CommandValidation.DEFAULT_CURRENCY);
          CommandValidation.requireValidMovement(amount, currency);
      }

      /** La orden en euros. */
      public static DepositCommand of(UUID accountId, BigDecimal amount) {
          return new DepositCommand(accountId, amount, null);
      }
  }
  ```
  Otros ejemplos: `Caller.customer(...)`/`administrator(...)`, `OpenAccountCommand.forHolder(...)`, `TransactionSummary.of(...)`.
- **Respuestas REST**: Jackson 3 serializa un `record` en el orden de sus componentes; las respuestas fijan con `@JsonPropertyOrder({...})` el orden alfabético que tenía la API (`RecordJsonTests`).
- ArchUnit comprueba que los commands, queries y vistas de `application` y las peticiones y respuestas REST son `record` (`HexagonalArchitectureTests.dataCarriersAreRecords`).

## Lombok

Configuración en `lombok.config` (raíz):

```properties
config.stopBubbling = true
lombok.addLombokGeneratedAnnotation = true
```

Lombok genera accesores JavaBean (`getX()`/`setX()`), los que necesitan los formularios web; ya no hay accesores fluidos ([ADR 0026](../../../../docs/adr/0026-domain-rules-in-archunit.md)). Los accesores al estilo record (`id()`) son los del dominio, escritos a mano, y los de los `record`.

**En el dominio (`domain..`) no se usa Lombok, en ninguna forma** ([ADR 0009](../../../../docs/adr/0009-domain-without-lombok.md)). Las entidades y los agregados escriben a mano sus accesores al estilo record (`id()`, `currentBalance()`) y su `equals`/`hashCode` por id (ver `templates/Entity.java.template`); los objetos de valor son `record`.

| Anotación | Fuera del dominio |
| --------- | ----------------- |
| `@RequiredArgsConstructor` | Sí: inyección por constructor en servicios, controladores y adaptadores |
| `@Slf4j` | Sí |
| `@Getter`, `@Setter` | En formularios web, que Spring MVC enlaza con getters y setters JavaBean |
| `@Builder` | Solo en builders de datos de test (por ejemplo `support/CommandBuilders`, sobre métodos que llaman al constructor del `record`) |
| `@Data`, `@Value`, `@AllArgsConstructor`, `@NoArgsConstructor` | Evitar; usar `record` |

Motivos: con `lombok.addLombokGeneratedAnnotation = true` cada método generado lleva `@lombok.Generated`, que queda en el bytecode y rompe la regla D1 de ArchUnit ("el dominio solo depende del JDK"), que es la comprobación automática (ADR 0026); y un `@Getter` sobre un agregado podría exponer colecciones internas.
