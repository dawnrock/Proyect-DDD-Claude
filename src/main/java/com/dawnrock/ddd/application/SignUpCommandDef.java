package com.dawnrock.ddd.application;

import java.math.BigDecimal;

import org.immutables.value.Value;

/**
 * Orden de darse de alta como cliente de Mon-A Bank: registra al cliente y le abre su primera cuenta, con un IBAN que
 * asigna el banco. Valida sus campos al construirse ({@code IllegalArgumentException}), antes de llegar al dominio.
 * La contraseña no viaja aquí: la gestiona la presentación ({@code CustomerRegistration}). Contrato:
 * {@code docs/contracts/customer-sign-up.md}.
 */
@ApplicationValue
@Value.Immutable
public interface SignUpCommandDef {

    /** Formato de un correo: algo, una arroba, un dominio con al menos un punto; sin espacios. */
    String EMAIL_PATTERN = "^ *[^@\\s]+@[^@\\s]+\\.[^@\\s]+ *$";

    String fullName();

    /** DNI o NIE del cliente; lo normaliza {@code TaxId}. */
    String taxId();

    String email();

    /** Usuario con el que entrará; mismo formato que {@link OpenAccountCommandDef#USERNAME_PATTERN}. */
    String username();

    BigDecimal initialDeposit();

    @Value.Default
    default String currency() {
        return "EUR";
    }

    @Value.Check
    default void validate() {
        if (fullName().isBlank()) {
            throw new IllegalArgumentException("El nombre y apellidos son obligatorios");
        }
        if (!taxId().matches(OpenAccountCommandDef.TAX_ID_PATTERN)) {
            throw new IllegalArgumentException(OpenAccountCommandDef.TAX_ID_MESSAGE);
        }
        if (!email().matches(EMAIL_PATTERN)) {
            throw new IllegalArgumentException("El correo electrónico no es válido");
        }
        if (!username().matches(OpenAccountCommandDef.USERNAME_PATTERN)) {
            throw new IllegalArgumentException(
                    "El usuario debe tener de 3 a 30 minúsculas, dígitos, puntos o guiones");
        }
        if (initialDeposit().signum() <= 0) {
            throw new IllegalArgumentException("El depósito inicial debe ser positivo");
        }
        if (!CommandValidation.isIsoCurrency(currency())) {
            throw new IllegalArgumentException("La divisa %s no es un código ISO 4217 válido".formatted(currency()));
        }
    }
}
