package com.dawnrock.ddd.adapter.in.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import com.dawnrock.ddd.application.OpenAccountCommandDef;
import com.dawnrock.ddd.application.SignUpCommandDef;
import com.dawnrock.ddd.adapter.in.Usernames;

import lombok.Getter;
import lombok.Setter;

/**
 * Formulario de alta de cuenta del administrador. Aplica la misma validación que {@code OpenAccountRequest} (REST).
 * Sin IBAN: lo asigna el banco. Para un cliente nuevo hacen falta nombre, correo y usuario; para uno existente basta
 * su documento. Contrato: {@code docs/contracts/customer-sign-up.md}.
 */
@Getter
@Setter
public class OpenAccountForm {

    @NotBlank(message = "El documento del titular es obligatorio")
    @Pattern(regexp = OpenAccountCommandDef.TAX_ID_PATTERN, message = OpenAccountCommandDef.TAX_ID_MESSAGE)
    private String holderTaxId;

    /** Obligatorio solo para un cliente nuevo; para uno existente, en blanco o igual al suyo. */
    private String holderName;

    /** Obligatorio solo para un cliente nuevo. */
    @Pattern(regexp = "^$|" + SignUpCommandDef.EMAIL_PATTERN, message = "El correo electrónico no es válido")
    private String holderEmail;

    /** Usuario (cliente) que verá y operará la cuenta; obligatorio solo para un cliente nuevo. */
    @Pattern(regexp = "^$|" + OpenAccountCommandDef.USERNAME_PATTERN,
            message = OpenAccountCommandDef.OWNER_USERNAME_MESSAGE)
    private String ownerUsername;

    @NotNull(message = "El depósito inicial es obligatorio")
    @Positive(message = "El depósito inicial debe ser positivo")
    private BigDecimal initialDeposit;

    @Pattern(regexp = "[A-Z]{3}", message = "La divisa debe ser un código de tres letras mayúsculas")
    private String currency = "EUR";

    /** Como en el alta (#152): el usuario del titular se envía en minúsculas y sin espacios alrededor. */
    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = Usernames.normalize(ownerUsername);
    }
}
