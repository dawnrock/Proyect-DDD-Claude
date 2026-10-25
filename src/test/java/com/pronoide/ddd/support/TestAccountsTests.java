package com.pronoide.ddd.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CustomerSegment;
import com.pronoide.ddd.domain.model.MonetaryAmount;

class TestAccountsTests {

    @Test
    @DisplayName("Sin indicar nada abre una cuenta minorista con 100,00 €")
    void sin_indicar_nada_abre_una_cuenta_minorista_con_100_euros() {
        CheckingAccount account = TestAccounts.anAccount().open();

        assertThat(account.accountNumber().value()).isEqualTo(TestAccounts.DEFAULT_ACCOUNT_NUMBER);
        assertThat(account.primaryHolder().fullName()).isEqualTo("Ada Lovelace");
        assertThat(account.primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
        assertThat(account.currentBalance()).isEqualTo(MonetaryAmount.euros("100.00"));
    }

    @Test
    @DisplayName("Cada dato de la cuenta se puede cambiar")
    void cada_dato_de_la_cuenta_se_puede_cambiar() {
        CheckingAccount account = TestAccounts.anAccount()
                .accountNumber("ES7921000813610123456789")
                .holderName("Grace Hopper")
                .segment(CustomerSegment.HIGH_NET_WORTH)
                .initialDeposit("500.00")
                .open();

        assertThat(account.accountNumber().value()).isEqualTo("ES7921000813610123456789");
        assertThat(account.primaryHolder().fullName()).isEqualTo("Grace Hopper");
        assertThat(account.primaryHolder().segment()).isEqualTo(CustomerSegment.HIGH_NET_WORTH);
        assertThat(account.currentBalance()).isEqualTo(MonetaryAmount.euros("500.00"));
    }
}
