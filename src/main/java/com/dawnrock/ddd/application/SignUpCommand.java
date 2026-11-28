package com.dawnrock.ddd.application;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Orden de darse de alta como cliente de monA Bank: registra al cliente y le abre su primera cuenta, con un IBAN que
 * asigna el banco. Valida sus campos al construirse ({@code IllegalArgumentException}), antes de llegar al dominio,
 * en este orden: la primera regla que falla es la que se informa. La contraseña no viaja aquí: la gestiona la
 * presentación ({@code CustomerRegistration}). Contrato: {@code docs/contracts/customer-sign-up.md}.
 *
 * @param taxId    DNI o NIE del cliente; lo normaliza {@code TaxId}
 * @param username usuario con el que entrará; mismo formato que {@link OpenAccountCommand#USERNAME_PATTERN}
 * @param currency código ISO 4217 ({@code EUR} si es {@code null})
 */
public record SignUpCommand(String fullName, String taxId, String email, String username, BigDecimal initialDeposit,
                            String currency) {

    /** Formato de un correo: algo, una arroba, un dominio con al menos un punto; sin espacios. */
    public static final String EMAIL_PATTERN = "^ *[^@\\s]+@[^@\\s]+\\.[^@\\s]+ *$";
    /** Mensaje de un correo mal formado; lo comparten los commands de alta y de apertura. */
    public static final String EMAIL_MESSAGE = "El correo electrónico no es válido";

    public SignUpCommand {
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(taxId, "taxId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(initialDeposit, "initialDeposit");
        currency = Objects.requireNonNullElse(currency, CommandValidation.DEFAULT_CURRENCY);
        CommandValidation.requireNotBlank(fullName, "El nombre y apellidos son obligatorios");
        CommandValidation.requireMatching(taxId, OpenAccountCommand.TAX_ID_PATTERN, OpenAccountCommand.TAX_ID_MESSAGE);
        CommandValidation.requireMatching(email, EMAIL_PATTERN, EMAIL_MESSAGE);
        CommandValidation.requireMatching(username, OpenAccountCommand.USERNAME_PATTERN,
                OpenAccountCommand.USERNAME_MESSAGE);
        CommandValidation.requireIsoCurrency(currency);
        CommandValidation.requireValidInitialDeposit(initialDeposit, currency);
    }

    /** El alta en euros. */
    public static SignUpCommand of(String fullName, String taxId, String email, String username,
                                   BigDecimal initialDeposit) {
        return new SignUpCommand(fullName, taxId, email, username, initialDeposit, null);
    }
}
