package com.dawnrock.ddd.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.dawnrock.ddd.application.OpenAccountCommand;
import com.dawnrock.ddd.application.SignUpCommand;

/**
 * Cuerpo de {@code POST /api/accounts}, con las restricciones de Bean Validation en sus componentes. Aplica la misma
 * validación que el formulario web ("Shared presentation validation" del contrato {@code docs/contracts/account-factory.md}), de modo
 * que lo que acepta nunca hace que el dominio lance {@code IllegalArgumentException}.
 *
 * <p>No lleva IBAN ni segmento: el IBAN lo genera la factoría y el segmento lo deciden los ingresos verificados del
 * titular. El titular se identifica por su documento ({@code holderTaxId}); para un cliente nuevo hacen falta nombre,
 * correo y usuario, y para uno existente van en blanco o coinciden con los suyos (contrato
 * {@code docs/contracts/customer-sign-up.md}). {@code currency} es opcional ({@code EUR}).
 */
public record OpenAccountRequest(
        @NotBlank(message = "El documento del titular es obligatorio")
        @Pattern(regexp = OpenAccountCommand.TAX_ID_PATTERN, message = OpenAccountCommand.TAX_ID_MESSAGE)
        String holderTaxId,

        /** Obligatorio solo para un cliente nuevo; para uno existente, en blanco o igual al suyo. */
        String holderName,

        @Pattern(regexp = "^$|" + SignUpCommand.EMAIL_PATTERN, message = "El correo electrónico no es válido")
        String holderEmail,

        @NotNull(message = "El depósito inicial es obligatorio")
        @DecimalMin(value = "0.005", message = "El depósito inicial debe ser positivo")
        @DecimalMax(value = "999999999.99", message = "El depósito inicial no puede superar 999999999.99")
        BigDecimal initialDeposit,

        @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código ISO 4217 de tres letras mayúsculas")
        String currency,

        /** Usuario (cliente) que verá y operará la cuenta; obligatorio solo para un cliente nuevo. */
        @Pattern(regexp = "^$|" + OpenAccountCommand.USERNAME_PATTERN,
                message = OpenAccountCommand.USERNAME_MESSAGE)
        String ownerUsername) {
}
