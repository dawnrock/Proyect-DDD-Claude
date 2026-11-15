package com.pronoide.ddd.presentation;

/**
 * El nombre de usuario elegido en el alta ya existe. No es una regla del dominio de cuentas (la identidad no llega a
 * {@code domain}): la lanza {@link CustomerRegistration} antes de abrir nada. La web vuelve al paso 2 del alta y la API
 * responde 422.
 */
public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String username) {
        super("El usuario %s ya existe: elige otro".formatted(username));
    }
}
