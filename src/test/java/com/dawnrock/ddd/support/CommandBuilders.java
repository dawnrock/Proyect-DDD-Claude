package com.dawnrock.ddd.support;

import java.math.BigDecimal;

import com.dawnrock.ddd.application.OpenAccountCommand;
import com.dawnrock.ddd.application.OpenAnotherAccountCommand;
import com.dawnrock.ddd.application.SignUpCommand;

import lombok.Builder;

/**
 * Builders de test para los commands de apertura y de alta (#219). Los tests parten de un command válido y cambian un
 * campo; con los {@code record} eso es un builder de test ({@code @Builder} de Lombok solo en builders de test,
 * {@code CLAUDE.md}). Cada {@code build()} llama al constructor del {@code record}, así que valida y rellena los
 * valores por defecto igual que en producción.
 */
public final class CommandBuilders {

    private CommandBuilders() {
    }

    @Builder(builderMethodName = "openAccount", builderClassName = "OpenAccountBuilder")
    private static OpenAccountCommand openAccountCommand(String holderName, String holderTaxId, String holderEmail,
                                                         BigDecimal initialDeposit, String ownerUsername,
                                                         String currency) {
        return new OpenAccountCommand(holderName, holderTaxId, holderEmail, initialDeposit, ownerUsername, currency);
    }

    @Builder(builderMethodName = "openAnotherAccount", builderClassName = "OpenAnotherAccountBuilder")
    private static OpenAnotherAccountCommand openAnotherAccountCommand(BigDecimal initialDeposit, String currency) {
        return new OpenAnotherAccountCommand(initialDeposit, currency);
    }

    @Builder(builderMethodName = "signUp", builderClassName = "SignUpBuilder")
    private static SignUpCommand signUpCommand(String fullName, String taxId, String email, String username,
                                               BigDecimal initialDeposit, String currency) {
        return new SignUpCommand(fullName, taxId, email, username, initialDeposit, currency);
    }
}
