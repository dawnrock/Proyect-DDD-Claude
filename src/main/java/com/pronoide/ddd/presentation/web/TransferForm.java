package com.pronoide.ddd.presentation.web;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Formulario de transferencia. Aplica la misma validación que {@code TransferRequest} (REST), según
 * "Shared presentation validation" de {@code docs/contracts/money-transfers.md}; la cuenta de origen es el
 * {@code {id}} de la ruta.
 */
@Getter
@Setter
@Accessors(fluent = false)
public class TransferForm {

    @NotNull(message = "Elige la cuenta de destino")
    private UUID targetAccountId;

    @NotNull(message = "El importe es obligatorio")
    @Positive(message = "El importe debe ser positivo")
    private BigDecimal amount;

    @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código de tres letras mayúsculas")
    private String currency = "EUR";
}
