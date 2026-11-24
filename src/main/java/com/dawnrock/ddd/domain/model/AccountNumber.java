package com.dawnrock.ddd.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record AccountNumber(String value) {

    private static final Pattern SPANISH_IBAN = Pattern.compile("ES\\d{22}");

    public AccountNumber {
        Objects.requireNonNull(value, "El número de cuenta es obligatorio");
        value = value.replace(" ", "").toUpperCase();
        if (!SPANISH_IBAN.matcher(value).matches()) {
            throw new IllegalArgumentException("El número de cuenta %s no es un IBAN español válido".formatted(value));
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
