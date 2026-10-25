package com.pronoide.ddd.application;

import org.immutables.value.Value;

/**
 * Quién pide un caso de uso: su usuario y si es administrador. Lo construye la presentación a partir de la
 * autenticación; la aplicación lo usa para decidir a qué cuentas puede acceder. Contrato:
 * {@code docs/contracts/login-roles.md}.
 *
 * <p>Un administrador accede a todas las cuentas y es el único que abre cuentas; un usuario (cliente) solo a las
 * suyas. La identidad no es lenguaje del dominio de cuentas: no llega a {@code domain}.
 */
@ApplicationValue
@Value.Immutable
public interface CallerDef {

    String username();

    boolean admin();

    /** Administrador interno para procesos sin usuario (por ejemplo, los datos de demostración). */
    static Caller system() {
        return Caller.builder().username("system").admin(true).build();
    }
}
