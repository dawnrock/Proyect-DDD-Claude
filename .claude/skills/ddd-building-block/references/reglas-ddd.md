# Reglas de los bloques tácticos de DDD

Resumen operativo del curso de DDD (capítulos 4-10 y labs 22-27) aplicado a este proyecto.

## Objeto de valor (Value Object)

- Se define por sus atributos, **no tiene identidad**: dos VO con los mismos valores son iguales.
- **Inmutable**: las operaciones devuelven instancias nuevas (`MonetaryAmount.add` devuelve otro importe).
- Se valida a sí mismo al construirse: no puede existir un VO inválido.
- En Java: `record` con constructor compacto que valida y normaliza. Ejemplos: `MonetaryAmount`, `AccountNumber`.

## Entidad

- Tiene **identidad** que se mantiene aunque cambien sus atributos (`AccountHolder` sigue siendo el mismo si se renombra).
- `equals`/`hashCode` solo por el id.
- Constructor privado + método factoría con nombre del lenguaje ubicuo (`AccountHolder.register`).
- Cambia de estado mediante métodos de negocio (`rename`, `promoteToHighNetWorth`), nunca con setters.

## Agregado

- Grupo de entidades y VO con una **raíz** que es el único punto de entrada (`CheckingAccount`).
- Es el **límite de consistencia**: todas sus invariantes se cumplen al terminar cada operación (saldo, límite de descubierto, divisa única).
- **Protege su estado**: no expone colecciones mutables (`transactions()` devuelve `List.copyOf`). Los hijos (`Transaction`) se crean y resuelven solo desde la raíz (métodos de paquete).
- Otros agregados se referencian por id, no por objeto.
- Un repositorio por agregado, nunca por entidad hija.

## Evento de dominio

- Algo relevante que **ya ha ocurrido**, en pasado: `MoneyDeposited`, `WithdrawalRejected`, `CheckingAccountOpened`.
- Inmutable (`record`), con id del agregado, datos mínimos e instante (`occurredOn`).
- Lo registra el agregado al cambiar de estado; se publica fuera del dominio (puerto `DomainEventPublisher` en la fase 2).

## Factoría

- Encapsula una creación compleja que no cabe en un método factoría: reglas que necesitan colaboradores (numeración de cuentas, verificación de ingresos).
- Reparto: la factoría valida lo que necesita el exterior; el agregado sigue protegiendo sus propias invariantes.

## Servicio de dominio

- Lógica de negocio que **no pertenece a un solo agregado** (`MoneyTransferService` coordina dos cuentas).
- Sin estado, nombrado con un verbo del lenguaje ubicuo, solo depende del modelo.

## Servicio de aplicación

- Orquesta un caso de uso: carga agregados del repositorio, invoca el dominio, guarda y publica. **No contiene reglas de negocio.**
- Recibe commands/queries (Immutables) y devuelve vistas, no agregados, a la presentación.

## Antipatrones a evitar

- **Modelo anémico**: entidades con getters/setters y la lógica en servicios.
- **Layer cake**: capas que solo reenvían la llamada sin aportar nada.
- **Anemic translation**: DTOs casi idénticos en cada capa con nombres distintos para lo mismo.
