package com.dawnrock.ddd.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Documento de identidad español: DNI (8 dígitos y una letra) o NIE ({@code X}, {@code Y} o {@code Z}, 7 dígitos y una
 * letra). Se normaliza (sin espacios, en mayúsculas) y se comprueba la letra de control (R27). Identifica al titular
 * ante la agencia tributaria que verifica sus ingresos.
 */
public record TaxId(String value) {

    private static final Pattern DOCUMENT = Pattern.compile("[XYZ0-9]\\d{7}[A-Z]");
    private static final String CONTROL_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";

    public TaxId {
        Objects.requireNonNull(value, "El NIF es obligatorio");
        value = value.replace(" ", "").toUpperCase(Locale.ROOT);
        if (!DOCUMENT.matcher(value).matches()) {
            throw new IllegalArgumentException("El documento debe ser un DNI o un NIE");
        }
        if (value.charAt(8) != controlLetter(value)) {
            throw new IllegalArgumentException(
                    "La letra de control del documento %s no es correcta".formatted(value));
        }
    }

    private static char controlLetter(String document) {
        char first = document.charAt(0);
        String digits = switch (first) {
            case 'X' -> "0";
            case 'Y' -> "1";
            case 'Z' -> "2";
            default -> String.valueOf(first);
        } + document.substring(1, 8);
        return CONTROL_LETTERS.charAt(Integer.parseInt(digits) % 23);
    }

    @Override
    public String toString() {
        return value;
    }
}
