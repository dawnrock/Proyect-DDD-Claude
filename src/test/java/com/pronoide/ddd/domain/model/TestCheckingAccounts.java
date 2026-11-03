package com.pronoide.ddd.domain.model;

/**
 * Puente de test: {@code CheckingAccount.open} es package-private (las cuentas se abren con la factoría) y los
 * builders de {@code support} necesitan cuentas con número y segmento fijos, sin pasar por la factoría ni sus puertos.
 * Vive en el paquete de test {@code domain.model} para alcanzarlo.
 */
public final class TestCheckingAccounts {

    private TestCheckingAccounts() {
    }

    public static CheckingAccount open(AccountNumber accountNumber, AccountHolder primaryHolder,
                                       MonetaryAmount initialDeposit) {
        return CheckingAccount.open(accountNumber, primaryHolder, initialDeposit);
    }
}
