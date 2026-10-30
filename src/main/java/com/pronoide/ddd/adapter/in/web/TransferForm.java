package com.pronoide.ddd.adapter.in.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Formulario de transferencia. Aplica la misma validación que {@code TransferRequest} (REST), según
 * "Shared presentation validation" de {@code docs/contracts/money-transfers.md}; la cuenta de origen es el
 * {@code {id}} de la ruta y la de destino se escribe como IBAN ({@code docs/contracts/login-roles.md}): así nadie ve
 * la lista de cuentas de los demás.
 */
@Getter
@Setter
@Accessors(fluent = false)
public class TransferForm {

    @NotBlank(message = "Escribe el número de cuenta de destino")
    @Pattern(regexp = "^ *[Ee][Ss]( *\\d){22} *$",
            message = "El número de cuenta de destino debe ser ES seguido de 22 dígitos")
    private String targetAccountNumber;

    @NotNull(message = "El importe es obligatorio")
    @Positive(message = "El importe debe ser positivo")
    private BigDecimal amount;

    @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código de tres letras mayúsculas")
    private String currency = "EUR";

    /** Una divisa vacía cuenta como ausente: se aplica el euro por defecto. */
    public void setCurrency(String currency) {
        this.currency = (currency == null || currency.isBlank()) ? "EUR" : currency;
    }
}
