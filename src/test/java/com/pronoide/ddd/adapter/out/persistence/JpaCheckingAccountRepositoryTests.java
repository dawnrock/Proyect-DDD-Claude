package com.pronoide.ddd.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CheckingAccountRepository;
import com.pronoide.ddd.domain.model.CheckingAccountRepositoryContract;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.support.TestAccounts;

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
    void una_cuenta_se_lee_de_la_base_con_su_saldo_y_sus_movimientos() {
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
    void un_cambio_en_una_cuenta_no_llega_a_la_base_hasta_que_se_guarda() {
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
