package com.pronoide.ddd.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Correo electrónico de un cliente: algo, una arroba y un dominio con al menos un punto, sin espacios. Se normaliza
 * (sin espacios alrededor, en minúsculas) (R28).
 */
public record EmailAddress(String value) {

    private static final Pattern FORMAT = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    public EmailAddress {
        Objects.requireNonNull(value, "El correo electrónico es obligatorio");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("El correo electrónico no es válido");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
