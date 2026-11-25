package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.DomainEvent;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.domain.model.MoneyDeposited;
import com.dawnrock.ddd.support.TestAccounts;

/** La unidad de trabajo de los casos de uso de cuentas: guarda, recoge los eventos y los publica al terminar (#174). */
class AccountUnitOfWorkTests {

    /** Unidad de trabajo que anota si hay una en curso. */
    private static final class RecordingUnitOfWork implements UnitOfWork {
        private final AtomicBoolean inside = new AtomicBoolean(false);

        @Override
        public <T> T execute(Supplier<T> work) {
            inside.set(true);
            try {
                return work.get();
            } finally {
                inside.set(false);
            }
        }
    }

    private final RecordingUnitOfWork unit = new RecordingUnitOfWork();
    private final List<List<DomainEvent>> batches = new ArrayList<>();
    private final AtomicBoolean publishedOutsideUnit = new AtomicBoolean(false);
    private CheckingAccountRepository accounts;
    private AccountUnitOfWork accountUnit;

    @BeforeEach
    void setUp() {
        accounts = PersistenceTestAdapters.accounts();
        DomainEventPublisher publisher = new DomainEventPublisher() {
            @Override
            public void publish(DomainEvent event) {
                publishAll(List.of(event));
            }

            @Override
            public void publishAll(List<DomainEvent> events) {
                if (!unit.inside.get()) {
                    publishedOutsideUnit.set(true);
                }
                batches.add(List.copyOf(events));
            }
        };
        accountUnit = new AccountUnitOfWork(unit, accounts, publisher);
    }

    private static CheckingAccount depositedAccount(String accountNumber) {
        CheckingAccount account = TestAccounts.anAccount().accountNumber(accountNumber).open();
        account.pullDomainEvents();
        account.deposit(MonetaryAmount.euros("10.00"));
        return account;
    }

    @Test
    @DisplayName("Guarda cada cuenta y devuelve el resultado del trabajo")
    void savesEachAccountAndReturnsTheResultOfTheWork() {
        CheckingAccount account = depositedAccount(TestAccounts.DEFAULT_ACCOUNT_NUMBER);

        String result = accountUnit.execute(changes -> {
            changes.save(account);
            return "hecho";
        });

        assertThat(result).isEqualTo("hecho");
        assertThat(accounts.findById(account.id())).isPresent();
    }

    @Test
    @DisplayName("Publica un lote de eventos por cuenta guardada, en el orden en que se guardaron y dentro de la unidad")
    void publishesOneBatchPerSavedAccountInSaveOrderInsideTheUnit() {
        CheckingAccount first = depositedAccount(TestAccounts.DEFAULT_ACCOUNT_NUMBER);
        CheckingAccount second = depositedAccount("ES7921000813610123456789");

        accountUnit.execute(changes -> {
            changes.save(first);
            changes.save(second);
            return null;
        });

        assertThat(batches).hasSize(2);
        assertThat(batches.get(0)).singleElement().isInstanceOfSatisfying(MoneyDeposited.class,
                event -> assertThat(event.accountId()).isEqualTo(first.id()));
        assertThat(batches.get(1)).singleElement().isInstanceOfSatisfying(MoneyDeposited.class,
                event -> assertThat(event.accountId()).isEqualTo(second.id()));
        assertThat(publishedOutsideUnit).as("algún evento se publicó fuera de la unidad").isFalse();
    }

    @Test
    @DisplayName("Si el trabajo falla no publica nada, aunque ya hubiera guardado alguna cuenta")
    void whenTheWorkFailsNothingIsPublished() {
        CheckingAccount account = depositedAccount(TestAccounts.DEFAULT_ACCOUNT_NUMBER);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> accountUnit.execute(changes -> {
            changes.save(account);
            throw new IllegalStateException("falla a medias");
        }));

        assertThat(batches).isEmpty();
    }
}
