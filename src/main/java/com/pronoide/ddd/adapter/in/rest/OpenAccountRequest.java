package com.pronoide.ddd.adapter.in.rest;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import com.pronoide.ddd.application.OpenAccountCommandDef;
import com.pronoide.ddd.application.SignUpCommandDef;

/**
 * Cuerpo de {@code POST /api/accounts}. Es un {@code record} y no un DTO de Immutables porque Hibernate
 * Validator solo ve las restricciones de campos y getters JavaBean. Aplica la misma validación que el
 * formulario web ("Shared presentation validation" del contrato {@code docs/contracts/account-factory.md}), de modo
 * que lo que acepta nunca hace que el dominio lance {@code IllegalArgumentException}.
 *
 * <p>No lleva IBAN ni segmento: el IBAN lo genera la factoría y el segmento lo deciden los ingresos verificados del
 * titular. El titular se identifica por su documento ({@code holderTaxId}); para un cliente nuevo hacen falta nombre,
 * correo y usuario, y para uno existente van en blanco o coinciden con los suyos (contrato
 * {@code docs/contracts/customer-sign-up.md}). {@code currency} es opcional ({@code EUR}).
 */
public record OpenAccountRequest(
        @NotBlank(message = "El documento del titular es obligatorio")
        @Pattern(regexp = OpenAccountCommandDef.TAX_ID_PATTERN, message = OpenAccountCommandDef.TAX_ID_MESSAGE)
        String holderTaxId,

        /** Obligatorio solo para un cliente nuevo; para uno existente, en blanco o igual al suyo. */
        String holderName,

        @Pattern(regexp = "^$|" + SignUpCommandDef.EMAIL_PATTERN, message = "El correo electrónico no es válido")
        String holderEmail,

        @NotNull(message = "El depósito inicial es obligatorio")
        @Positive(message = "El depósito inicial debe ser positivo")
        BigDecimal initialDeposit,

        @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código ISO 4217 de tres letras mayúsculas")
        String currency,

        /** Usuario (cliente) que verá y operará la cuenta; obligatorio solo para un cliente nuevo. */
        @Pattern(regexp = "^$|" + OpenAccountCommandDef.USERNAME_PATTERN,
                message = "El usuario debe tener de 3 a 30 minúsculas, dígitos, puntos o guiones")
        String ownerUsername) {
}
