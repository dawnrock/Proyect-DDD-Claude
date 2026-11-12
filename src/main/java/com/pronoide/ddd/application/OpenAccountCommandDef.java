package com.pronoide.ddd.application;

import java.math.BigDecimal;

import org.immutables.value.Value;

/**
 * Orden de abrir una cuenta corriente. Valida sus campos al construirse ({@code IllegalArgumentException}),
 * antes de llegar al dominio.
 *
 * <p>{@code currency} es un código ISO 4217 ({@code EUR} por defecto). El segmento del titular no se indica: lo
 * decide el dominio según el saldo de la cuenta.
 */
@ApplicationValue
@Value.Immutable
public interface OpenAccountCommandDef {

    /** Formato de un nombre de usuario: 3-30 minúsculas, dígitos, punto, guion o guion bajo. */
    String USERNAME_PATTERN = "[a-z0-9._-]{3,30}";

    String accountNumber();

    String holderName();

    BigDecimal initialDeposit();

    @Value.Default
    default String currency() {
        return "EUR";
    }

    /**
     * Usuario (cliente) propietario de la cuenta: solo él y los administradores podrán verla y operarla. Vacío por
     * defecto en el command; {@code openAccount(Caller, ...)} lo exige.
     */
    @Value.Default
    default String ownerUsername() {
        return "";
    }

    @Value.Check
    default void validate() {
        if (accountNumber().isBlank()) {
            throw new IllegalArgumentException("El número de cuenta es obligatorio");
        }
        if (holderName().isBlank()) {
            throw new IllegalArgumentException("El nombre del titular es obligatorio");
        }
        if (initialDeposit().signum() <= 0) {
            throw new IllegalArgumentException("El depósito inicial debe ser positivo");
        }
        if (!ownerUsername().isEmpty() && !ownerUsername().matches(USERNAME_PATTERN)) {
            throw new IllegalArgumentException(
                    "El usuario del titular debe tener de 3 a 30 minúsculas, dígitos, puntos o guiones");
        }
        if (!CommandValidation.isIsoCurrency(currency())) {
            throw new IllegalArgumentException("La divisa %s no es un código ISO 4217 válido".formatted(currency()));
        }
    }
}
