package com.dawnrock.ddd.application;

import java.util.Objects;

/**
 * Quién pide un caso de uso: su usuario y si es administrador. Lo construye la presentación a partir de la
 * autenticación; la aplicación lo usa para decidir a qué cuentas puede acceder. Contrato:
 * {@code docs/contracts/login-roles.md}.
 *
 * <p>Un administrador accede a todas las cuentas y es el único que abre cuentas; un usuario (cliente) solo a las
 * suyas. La identidad no es lenguaje del dominio de cuentas: no llega a {@code domain}.
 */
public record Caller(String username, boolean admin) {

    public Caller {
        Objects.requireNonNull(username, "username");
    }

    /** Un cliente: solo accede a sus cuentas. */
    public static Caller customer(String username) {
        return new Caller(username, false);
    }

    /** Un administrador: accede a todas las cuentas y abre cuentas. */
    public static Caller administrator(String username) {
        return new Caller(username, true);
    }

    /** Administrador interno para procesos sin usuario (por ejemplo, los datos de demostración). */
    public static Caller system() {
        return administrator("system");
    }
}
