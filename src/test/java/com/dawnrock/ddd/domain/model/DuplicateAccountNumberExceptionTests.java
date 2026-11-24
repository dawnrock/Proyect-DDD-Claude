package com.dawnrock.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DuplicateAccountNumberExceptionTests {

    private static final AccountNumber ACCOUNT_NUMBER = new AccountNumber("es91 2100 0418 4502 0005 1332");

    @Test
    @DisplayName("Un número de cuenta duplicado es una regla de negocio incumplida")
    void aDuplicateAccountNumberIsABrokenBusinessRule() {
        assertThat(new DuplicateAccountNumberException(ACCOUNT_NUMBER)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("El mensaje en español incluye el número de cuenta normalizado")
    void theSpanishMessageIncludesTheNormalisedAccountNumber() {
        assertThat(new DuplicateAccountNumberException(ACCOUNT_NUMBER))
                .hasMessage("Ya existe una cuenta con el número ES9121000418450200051332");
    }
}
