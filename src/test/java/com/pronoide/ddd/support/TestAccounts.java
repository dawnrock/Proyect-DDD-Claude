package com.pronoide.ddd.support;

import java.util.Objects;

import com.pronoide.ddd.domain.model.AccountHolder;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CustomerSegment;
import com.pronoide.ddd.domain.model.MonetaryAmount;

import lombok.Builder;

/**
 * Cuentas de prueba: {@code anAccount().segment(HIGH_NET_WORTH).initialDeposit("500.00").open()}. Abre la cuenta con
 * su método factoría ({@code CheckingAccount.open}); los datos que no se indican toman un valor por defecto válido.
 */
public final class TestAccounts {

    public static final String DEFAULT_ACCOUNT_NUMBER = "ES9121000418450200051332";

    private TestAccounts() {
    }

    /**
     * @param initialDeposit importe en euros, por ejemplo {@code "100.00"}
     */
    @Builder(builderMethodName = "anAccount", buildMethodName = "open")
    private static CheckingAccount newAccount(String accountNumber, String holderName, CustomerSegment segment,
                                              String initialDeposit) {
        return CheckingAccount.open(
                new AccountNumber(Objects.requireNonNullElse(accountNumber, DEFAULT_ACCOUNT_NUMBER)),
                AccountHolder.register(Objects.requireNonNullElse(holderName, "Ada Lovelace"),
                        Objects.requireNonNullElse(segment, CustomerSegment.RETAIL)),
                MonetaryAmount.euros(Objects.requireNonNullElse(initialDeposit, "100.00")));
    }
}
