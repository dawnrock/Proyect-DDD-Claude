package com.pronoide.ddd.adapter.in.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import com.pronoide.ddd.application.OpenAccountCommandDef;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Formulario de alta de cuenta. Aplica la misma validación que {@code OpenAccountRequest} (REST), según
 * "Shared presentation validation" de {@code docs/contracts/account-factory.md}.
 */
@Getter
@Setter
@Accessors(fluent = false)
public class OpenAccountForm {

    @NotBlank(message = "El titular es obligatorio")
    private String holderName;

    @NotBlank(message = "El NIF es obligatorio")
    @Pattern(regexp = OpenAccountCommandDef.TAX_ID_PATTERN, message = "El NIF debe tener 8 dígitos y una letra")
    private String holderTaxId;

    /** Usuario (cliente) que verá y operará la cuenta; mismo formato que el command (contrato login-roles). */
    @NotBlank(message = "El usuario del titular es obligatorio")
    @Pattern(regexp = OpenAccountCommandDef.USERNAME_PATTERN,
            message = "El usuario del titular debe tener de 3 a 30 minúsculas, dígitos, puntos o guiones")
    private String ownerUsername;

    @NotNull(message = "El depósito inicial es obligatorio")
    @Positive(message = "El depósito inicial debe ser positivo")
    private BigDecimal initialDeposit;

    @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código de tres letras mayúsculas")
    private String currency = "EUR";
}
