package com.pronoide.ddd.adapter.in;

import java.util.Locale;

/**
 * Normalización de un nombre de usuario escrito por una persona (#152, #157): sin espacios alrededor y en minúsculas
 * ({@link Locale#ROOT}). Todos los usuarios guardados cumplen {@code [a-z0-9._-]{3,30}}, así que el inicio de sesión,
 * el límite de intentos y los formularios web normalizan igual lo que se escribe antes de usarlo.
 */
public final class Usernames {

    private Usernames() {
    }

    /** El usuario normalizado; {@code null} sigue siendo {@code null}. */
    public static String normalize(String username) {
        return username == null ? null : username.strip().toLowerCase(Locale.ROOT);
    }
}
