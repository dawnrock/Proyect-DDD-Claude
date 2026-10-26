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

**En el dominio (`domain..`) no se usa Lombok, en ninguna forma** ([ADR 0009](../../../../docs/adr/0009-domain-without-lombok.md)). Las entidades y los agregados escriben a mano sus accesores al estilo record (`id()`, `currentBalance()`) y su `equals`/`hashCode` por id (ver `templates/Entity.java.template`); los objetos de valor son `record`.

| Anotación | Fuera del dominio |
| --------- | ----------------- |
| `@RequiredArgsConstructor` | Sí: inyección por constructor en servicios, controladores y adaptadores |
| `@Slf4j` | Sí |
| `@Getter`, `@Setter` + `@Accessors(fluent = false)` | En formularios web, que Spring MVC enlaza con getters y setters JavaBean |
| `@Builder` | Solo en builders de datos de test |
| `@Data`, `@Value`, `@AllArgsConstructor`, `@NoArgsConstructor` | Evitar; usar Immutables o `record` |

Motivos: con `lombok.addLombokGeneratedAnnotation = true` cada método generado lleva `@lombok.Generated`, que queda en el bytecode y rompe la regla D1 de ArchUnit ("el dominio solo depende del JDK"); y un `@Getter` sobre un agregado podría exponer colecciones internas. `scripts/check-domain.mjs` rechaza cualquier `import lombok…` o `@lombok.…` en el dominio.

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
- **Nunca en `domain..`**: el código generado lleva anotaciones `org.immutables` con retención de clase y rompería la regla D1 de `LayeredArchitectureTests` ("el dominio solo depende del JDK").
- No mezcles Lombok e Immutables en la misma clase.
- Jackson 3 (Spring Boot 4): verifica la serialización antes de usar Immutables en DTOs REST; si da problemas, usa `record` en los DTOs (ver ADR 0003).
