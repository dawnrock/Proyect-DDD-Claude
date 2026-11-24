package com.dawnrock.ddd.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.CheckingAccountRepositoryContract;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.support.TestAccounts;

class JpaCheckingAccountRepositoryTests extends CheckingAccountRepositoryContract {

    /** El adaptador sobre la base del test, con cada llamada en su propia transacción. */
    @Override
    protected CheckingAccountRepository createRepository() {
        JpaTestDatabase.clear();
        CheckingAccountRepository adapter =
                new JpaCheckingAccountRepository(JpaTestDatabase.bean(CheckingAccountJpaRepository.class));
        return new CheckingAccountRepository() {
            @Override
            public CheckingAccount save(CheckingAccount account) {
                return JpaTestDatabase.inTransaction(() -> adapter.save(account));
            }

            @Override
            public Optional<CheckingAccount> findById(UUID id) {
                return JpaTestDatabase.inTransaction(() -> adapter.findById(id));
            }

            @Override
            public Optional<CheckingAccount> findByAccountNumber(AccountNumber accountNumber) {
                return JpaTestDatabase.inTransaction(() -> adapter.findByAccountNumber(accountNumber));
            }

            @Override
            public List<CheckingAccount> findAll() {
                return JpaTestDatabase.inTransaction(adapter::findAll);
            }
        };
    }

    @Test
    @DisplayName("Una cuenta se lee de la base con su saldo y sus movimientos, como otra instancia")
    void anAccountIsReadFromTheDatabaseWithItsBalanceAndTransactions() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("100.00").open();
        cuenta.deposit(MonetaryAmount.euros("25.00"));
        repository.save(cuenta);

        CheckingAccount leida = repository.findById(cuenta.id()).orElseThrow();

        assertThat(leida).isNotSameAs(cuenta).isEqualTo(cuenta);
        assertThat(leida.currentBalance()).isEqualTo(MonetaryAmount.euros("125.00"));
        assertThat(leida.transactions()).hasSize(2);
    }

    @Test
    @DisplayName("Un cambio en una cuenta no llega a la base hasta que se guarda")
    void aChangeToAnAccountDoesNotReachTheDatabaseUntilItIsSaved() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("100.00").open();
        repository.save(cuenta);

        cuenta.deposit(MonetaryAmount.euros("25.00"));
        assertThat(repository.findById(cuenta.id()).orElseThrow().currentBalance())
                .isEqualTo(MonetaryAmount.euros("100.00"));

        repository.save(cuenta);
        assertThat(repository.findById(cuenta.id()).orElseThrow().currentBalance())
                .isEqualTo(MonetaryAmount.euros("125.00"));
    }
}
