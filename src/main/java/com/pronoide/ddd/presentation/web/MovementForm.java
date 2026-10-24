package com.pronoide.ddd.presentation.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Formulario de ingreso o retirada. Aplica la misma validación que {@code MovementRequest} (REST), según
 * "Shared presentation validation" de {@code docs/contracts/deposits-withdrawals.md}: un importe no positivo
 * es un error de campo, no del dominio.
 */
@Getter
@Setter
@Accessors(fluent = false)
public class MovementForm {

    @NotNull(message = "El importe es obligatorio")
    @Positive(message = "El importe debe ser positivo")
    private BigDecimal amount;

    @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código de tres letras mayúsculas")
    private String currency = "EUR";
}
