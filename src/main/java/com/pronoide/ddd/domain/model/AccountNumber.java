package com.pronoide.ddd.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record AccountNumber(String value) {

    private static final Pattern SPANISH_IBAN = Pattern.compile("ES\\d{22}");

    /** Entidad (9999, inventada) y oficina (0001) de Mon-A Bank, tras {@code ES} y los dígitos de control. */
    private static final String MON_A_BANK_PREFIX = "99990001";

    public AccountNumber {
        Objects.requireNonNull(value, "El número de cuenta es obligatorio");
        value = value.replace(" ", "").toUpperCase();
        if (!SPANISH_IBAN.matcher(value).matches()) {
            throw new IllegalArgumentException("El número de cuenta %s no es un IBAN español válido".formatted(value));
        }
    }

    /** Si lo ha generado Mon-A Bank: entidad {@code 9999} y oficina {@code 0001} (R31, R32). */
    public boolean isMonABank() {
        return value.startsWith(MON_A_BANK_PREFIX, 4);
    }

    @Override
    public String toString() {
        return value;
    }
}
