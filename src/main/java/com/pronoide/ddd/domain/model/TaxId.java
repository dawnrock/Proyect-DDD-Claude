package com.pronoide.ddd.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * NIF español: 8 dígitos y una letra. Se normaliza (sin espacios, en mayúsculas). Solo se comprueba el formato, no la
 * letra de control. Identifica al titular ante quien verifica sus ingresos ({@link IncomeVerifier}).
 */
public record TaxId(String value) {

    private static final Pattern NIF = Pattern.compile("\\d{8}[A-Z]");

    public TaxId {
        Objects.requireNonNull(value, "El NIF es obligatorio");
        value = value.replace(" ", "").toUpperCase();
        if (!NIF.matcher(value).matches()) {
            throw new IllegalArgumentException("El NIF debe tener 8 dígitos y una letra");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
