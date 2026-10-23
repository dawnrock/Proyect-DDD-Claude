# Lombok e Immutables en este proyecto

## Lombok

Configuración en `lombok.config` (raíz):

```properties
config.stopBubbling = true
lombok.accessors.fluent = true
lombok.addLombokGeneratedAnnotation = true
lombok.anyConstructor.addConstructorProperties = false
```

`accessors.fluent = true` genera `id()` en lugar de `getId()`, igual que los accesores escritos a mano y los `record`.

| Anotación | Dominio (`domain..`) | Resto |
| --------- | -------------------- | ----- |
| `@Getter` | Sí, sobre campos concretos | Sí |
| `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` + `@EqualsAndHashCode.Include` en el id | Sí, en entidades | Sí |
| `@ToString` | Sí, excluyendo colecciones | Sí |
| `@RequiredArgsConstructor` | No (los agregados usan factorías) | Sí: inyección por constructor en servicios, controladores y adaptadores |
| `@Slf4j` | No (el dominio no registra logs) | Sí |
| `@Builder` | **No** | Solo en builders de datos de test |
| `@Data`, `@Setter`, `@Value`, `@AllArgsConstructor`, `@NoArgsConstructor` | **No** | Evitar; usar Immutables o `record` |

Motivo: un agregado solo cambia de estado mediante sus métodos de negocio; setters o constructores públicos permitirían saltarse las invariantes. ArchUnit no detecta estas anotaciones (retención `SOURCE`), por eso existe `scripts/check-domain.mjs`.

## Immutables

- Para **commands, queries y vistas** de la capa de aplicación y **DTOs** de los adaptadores.
- Estilo común del proyecto (anotación `@ApplicationValue` o paquete `package-info.java`):
  ```java
  @Value.Style(
      typeImmutable = "*",            // OpenAccountCommandDef → OpenAccountCommand
      typeAbstract = "*Def",
      visibility = Value.Style.ImplementationVisibility.PUBLIC,
      defaults = @Value.Immutable(copy = false))
  ```
- Ejemplo:
  ```java
  @Value.Immutable
  interface OpenAccountCommandDef {
      String holderName();
      CustomerSegment segment();
      BigDecimal initialDeposit();
  }
  // uso: OpenAccountCommand.builder().holderName("Ada").segment(RETAIL).initialDeposit(...).build()
  ```
- **Nunca en `domain..`**: el código generado lleva anotaciones `org.immutables` con retención de clase y rompería `DomainIsolationTests`.
- No mezcles Lombok e Immutables en la misma clase.
- Jackson 3 (Spring Boot 4): verifica la serialización antes de usar Immutables en DTOs REST; si da problemas, usa `record` en los DTOs (ver ADR 0003).
