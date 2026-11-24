# Convenciones de tests

- JUnit 5 + AssertJ (`assertThat`, `assertThatExceptionOfType`). Un test por regla de negocio.
- Nombre del método en **inglés y camelCase**, describiendo el comportamiento (qué pasa, en qué situación y qué se espera), y `@DisplayName` con la frase completa **en español**, que es lo que muestran el IDE y los informes (#166, convención del curso de Clean Code):
  ```java
  @Test
  @DisplayName("Un cliente minorista no puede dejar la cuenta en descubierto")
  void aRetailCustomerCannotOverdrawTheAccount() { ... }
  ```
  El nombre no traduce la frase palabra por palabra ni repite la clase probada; dentro de un `@Nested`, continúa la frase de la clase anidada. Los términos del dominio salen de `docs/dominio/glosario.md`.
- Agrupa por regla o por caso de uso con `@Nested`, y usa `@ParameterizedTest` (`@CsvSource`) para casos límite de importes, divisas y descubiertos.
- Los tests del dominio son POJO puros: sin Spring, sin mocks. Construyen el agregado con sus métodos factoría.
- Mockito solo para simular **puertos** (fase 2) o colaboradores de otra capa. Nunca se simula una clase del dominio.
- Tests de web/REST con `@WebMvcTest` del controlador (sin levantar toda la aplicación). El test de contexto completo es solo `DddLabApplicationTests`.
- Los builders de datos de prueba (`@Builder` de Lombok permitido aquí) viven en el paquete de test `...support`.

## ArchUnit

- Las reglas viven en la raíz del paquete de test (`DomainIsolationTests`, `LayeredArchitectureTests` / `HexagonalArchitectureTests`) con `@AnalyzeClasses(importOptions = ImportOption.DoNotIncludeTests.class)`.
- Cada regla es un **método** `@ArchTest static void englishCamelCaseName(JavaClasses classes)` que construye la regla y llama a `check(classes)`, con el mismo convenio de nombres que el resto de tests (#166). Las definiciones compartidas son constantes privadas en `UPPER_SNAKE_CASE`.
- Cada regla nueva se valida **rompiéndola**: introduce temporalmente una dependencia prohibida, comprueba que `./mvnw test -Dtest=<Clase>` falla por esa regla y revierte el cambio. Indícalo en el commit o la PR.
