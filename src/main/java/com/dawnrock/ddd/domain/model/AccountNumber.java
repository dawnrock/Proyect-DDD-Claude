package com.dawnrock.ddd.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record AccountNumber(String value) {

    private static final Pattern SPANISH_IBAN = Pattern.compile("ES\\d{22}");

    /** Entidad (9999, inventada) y oficina (0001) de monA Bank, tras {@code ES} y los dígitos de control. */
    private static final String MON_A_BANK_PREFIX = "99990001";

    /** Posición donde empieza el BBAN (entidad, oficina, control y cuenta): tras {@code ES} y los dos dígitos de control. */
    private static final int BBAN_OFFSET = 4;

    public AccountNumber {
        Objects.requireNonNull(value, "El número de cuenta es obligatorio");
        value = value.replace(" ", "").toUpperCase();
        if (!SPANISH_IBAN.matcher(value).matches()) {
            throw new IllegalArgumentException("El número de cuenta %s no es un IBAN español válido".formatted(value));
        }
    }

    /** Si lo ha generado monA Bank: entidad {@code 9999} y oficina {@code 0001} (R31, R32). */
    public boolean isMonABank() {
        return value.startsWith(MON_A_BANK_PREFIX, BBAN_OFFSET);
    }

    @Override
    public String toString() {
        return value;
    }
}
