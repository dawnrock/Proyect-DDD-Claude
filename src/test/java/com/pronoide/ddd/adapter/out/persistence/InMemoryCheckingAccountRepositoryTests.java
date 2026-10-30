package com.pronoide.ddd.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CheckingAccountRepository;
import com.pronoide.ddd.domain.model.CheckingAccountRepositoryContract;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.support.TestAccounts;

class InMemoryCheckingAccountRepositoryTests extends CheckingAccountRepositoryContract {

    @Override
    protected CheckingAccountRepository createRepository() {
        return new InMemoryCheckingAccountRepository();
    }

    @Test
    @DisplayName("El repositorio guarda la propia cuenta, no una copia")
    void el_repositorio_guarda_la_propia_cuenta_no_una_copia() {
        CheckingAccount cuenta = TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open();
        repository.save(cuenta);

        cuenta.deposit(MonetaryAmount.euros("25.00"));

        assertThat(repository.findById(cuenta.id())).containsSame(cuenta);
        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("125.00"));
    }
}
