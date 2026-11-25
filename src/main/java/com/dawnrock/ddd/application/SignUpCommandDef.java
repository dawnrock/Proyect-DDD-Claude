package com.dawnrock.ddd.application;

import java.math.BigDecimal;

import org.immutables.value.Value;

/**
 * Orden de darse de alta como cliente de monA Bank: registra al cliente y le abre su primera cuenta, con un IBAN que
 * asigna el banco. Valida sus campos al construirse ({@code IllegalArgumentException}), antes de llegar al dominio.
 * La contraseña no viaja aquí: la gestiona la presentación ({@code CustomerRegistration}). Contrato:
 * {@code docs/contracts/customer-sign-up.md}.
 */
@ApplicationValue
@Value.Immutable
public interface SignUpCommandDef {

    /** Formato de un correo: algo, una arroba, un dominio con al menos un punto; sin espacios. */
    String EMAIL_PATTERN = "^ *[^@\\s]+@[^@\\s]+\\.[^@\\s]+ *$";

    /** Mensaje de un correo mal formado; lo comparten los commands de alta y de apertura. */
    String EMAIL_MESSAGE = "El correo electrónico no es válido";

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

    /** Reglas de formato, en este orden: la primera que falla es la que se informa. */
    @Value.Check
    default void validate() {
        CommandValidation.requireNotBlank(fullName(), "El nombre y apellidos son obligatorios");
        CommandValidation.requireMatching(taxId(), OpenAccountCommandDef.TAX_ID_PATTERN,
                OpenAccountCommandDef.TAX_ID_MESSAGE);
        CommandValidation.requireMatching(email(), EMAIL_PATTERN, EMAIL_MESSAGE);
        CommandValidation.requireMatching(username(), OpenAccountCommandDef.USERNAME_PATTERN,
                OpenAccountCommandDef.USERNAME_MESSAGE);
        CommandValidation.requireIsoCurrency(currency());
        CommandValidation.requireValidInitialDeposit(initialDeposit(), currency());
    }
}
