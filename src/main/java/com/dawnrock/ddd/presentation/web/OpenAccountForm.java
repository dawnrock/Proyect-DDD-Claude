package com.dawnrock.ddd.presentation.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import com.dawnrock.ddd.application.OpenAccountCommandDef;
import com.dawnrock.ddd.application.SignUpCommandDef;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Formulario de alta de cuenta del administrador. Aplica la misma validación que {@code OpenAccountRequest} (REST).
 * Sin IBAN: lo asigna el banco. Contrato: {@code docs/contracts/customer-sign-up.md}.
 */
@Getter
@Setter
@Accessors(fluent = false)
public class OpenAccountForm {

    /** Obligatorio solo para un cliente nuevo; para uno existente, en blanco o igual al suyo. */
    private String holderName;

    /** Obligatorio solo para un cliente nuevo. */
    @Pattern(regexp = "^$|" + SignUpCommandDef.EMAIL_PATTERN, message = "El correo electrónico no es válido")
    private String holderEmail;

    @NotBlank(message = "El documento del titular es obligatorio")
    @Pattern(regexp = OpenAccountCommandDef.TAX_ID_PATTERN,
            message = OpenAccountCommandDef.TAX_ID_MESSAGE)
    private String holderTaxId;

    /** Usuario (cliente) que verá y operará la cuenta; obligatorio solo para un cliente nuevo. */
    @Pattern(regexp = "^$|" + OpenAccountCommandDef.USERNAME_PATTERN,
            message = "El usuario del titular debe tener de 3 a 30 minúsculas, dígitos, puntos o guiones")
    private String ownerUsername;

    @NotNull(message = "El depósito inicial es obligatorio")
    @Positive(message = "El depósito inicial debe ser positivo")
    private BigDecimal initialDeposit;

    @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código de tres letras mayúsculas")
    private String currency = "EUR";
}
