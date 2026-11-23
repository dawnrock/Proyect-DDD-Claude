package com.dawnrock.ddd.presentation.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Formulario de «Abrir otra cuenta» de un cliente: solo el depósito (y la divisa). El titular sale del cliente que lo
 * pide. Misma validación que {@code OpenAnotherAccountCommandDef}, según "Shared presentation validation" de
 * {@code docs/contracts/customer-sign-up.md}.
 */
@Getter
@Setter
@Accessors(fluent = false)
public class AnotherAccountForm {

    @NotNull(message = "El depósito inicial es obligatorio")
    @Positive(message = "El depósito inicial debe ser positivo")
    private BigDecimal initialDeposit;

    @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código de tres letras mayúsculas")
    private String currency = "EUR";
}
