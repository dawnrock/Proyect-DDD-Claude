package com.dawnrock.ddd.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.dawnrock.ddd.application.UnitOfWork;
import com.dawnrock.ddd.application.UnitOfWorkContract;
import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.support.TestAccounts;

class TransactionalUnitOfWorkTests extends UnitOfWorkContract {

    private final UnitOfWork unitOfWork =
            new TransactionalUnitOfWork(JpaTestDatabase.bean(PlatformTransactionManager.class));
    private final CheckingAccountRepository accounts =
            new JpaCheckingAccountRepository(JpaTestDatabase.bean(CheckingAccountJpaRepository.class));

    @Override
    protected UnitOfWork unitOfWork() {
        return unitOfWork;
    }

    @BeforeEach
    void emptyDatabase() {
        JpaTestDatabase.clear();
    }

    @Test
    @DisplayName("Lo guardado en una unidad que termina bien queda en la base")
    void whatASuccessfulUnitSavesStaysInTheDatabase() {
        CheckingAccount cuenta = TestAccounts.anAccount().open();

        unitOfWork.execute(() -> accounts.save(cuenta));

        assertThat(unitOfWork.execute(() -> accounts.findById(cuenta.id()))).isPresent();
    }

    @Test
    @DisplayName("Si una unidad falla, lo que guardó se deshace")
    void whenAUnitFailsWhatItSavedIsRolledBack() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("100.00").open();
        unitOfWork.execute(() -> accounts.save(cuenta));

        assertThatIllegalStateException().isThrownBy(() -> unitOfWork.execute(() -> {
            CheckingAccount leida = accounts.findById(cuenta.id()).orElseThrow();
            leida.deposit(MonetaryAmount.euros("25.00"));
            accounts.save(leida);
            accounts.save(TestAccounts.anAccount().accountNumber("ES6699990001740000000001").open());
            throw new IllegalStateException("fallo a mitad del caso de uso");
        }));

        assertThat(unitOfWork.execute(() -> accounts.findById(cuenta.id())).orElseThrow().currentBalance())
                .isEqualTo(MonetaryAmount.euros("100.00"));
        assertThat(unitOfWork.execute(accounts::findAll)).hasSize(1);
    }
}
