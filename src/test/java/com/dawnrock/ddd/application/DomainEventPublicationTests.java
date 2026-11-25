package com.dawnrock.ddd.application;

import static com.dawnrock.ddd.application.ServiceTestSupport.ADMIN;
import static com.dawnrock.ddd.application.ServiceTestSupport.publishedBatches;
import static com.dawnrock.ddd.application.ServiceTestSupport.retailOpening;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.CheckingAccountOpened;
import com.dawnrock.ddd.domain.model.DomainEvent;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.model.DuplicateAccountNumberException;
import com.dawnrock.ddd.domain.model.MoneyDeposited;
import com.dawnrock.ddd.domain.model.WithdrawalRejected;
import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.adapter.out.taxagency.TaxAgencyTestAdapters;
import com.dawnrock.ddd.domain.model.CheckingAccountFactory;
import com.dawnrock.ddd.support.ApplicationServicesFixture;

/**
 * Publicación de los eventos de dominio de los casos de uso que cambian cuentas (#78, #165, #174): uno por cuenta
 * guardada, después de guardarla y dentro de la unidad de trabajo, y ninguno si el caso de uso falla.
 */
class DomainEventPublicationTests {

    private CheckingAccountRepository repository;
    private DomainEventPublisher publisher;
    private AccountOpeningCommands opening;
    private MoneyMovementCommands movements;

    @BeforeEach
    void setUp() {
        repository = spy(PersistenceTestAdapters.accounts());
        publisher = mock(DomainEventPublisher.class);
        ApplicationServicesFixture fixture = ApplicationServicesFixture.builder()
                .accounts(repository)
                .publisher(publisher)
                .build();
        opening = fixture.accountOpeningService();
        movements = fixture.moneyMovementService();
    }

    @Nested
    @DisplayName("Publicar los eventos de dominio")
    class PublishDomainEvents {

        @Test
        @DisplayName("Abrir una cuenta publica cuenta abierta y el ingreso inicial después de guardarla")
        void openingAnAccountPublishesItsEventsAfterSavingIt() {
            opening.openAccount(ADMIN, retailOpening().build());

            InOrder order = inOrder(repository, publisher);
            order.verify(repository).save(any(CheckingAccount.class));
            order.verify(publisher).publishAll(any());
            assertThat(publishedBatches(publisher)).singleElement().satisfies(events -> assertThat(events)
                    .map(DomainEvent::getClass).containsExactly(CheckingAccountOpened.class, MoneyDeposited.class));
        }

        @Test
        @DisplayName("Un ingreso publica el evento de dinero ingresado después de guardar la cuenta")
        void aDepositPublishesItsEventAfterSaving() {
            UUID id = opening.openAccount(ADMIN, retailOpening().build()).id();
            clearInvocations(repository, publisher);

            movements.deposit(ADMIN, DepositCommand.builder().accountId(id).amount(BigDecimal.TEN).build());

            InOrder order = inOrder(repository, publisher);
            order.verify(repository).save(any(CheckingAccount.class));
            order.verify(publisher).publishAll(any());
            assertThat(publishedBatches(publisher)).singleElement().satisfies(events -> assertThat(events)
                    .map(DomainEvent::getClass).containsExactly(MoneyDeposited.class));
        }

        @Test
        @DisplayName("Una retirada rechazada publica el evento de retirada rechazada")
        void aRejectedWithdrawalPublishesItsEvent() {
            UUID id = opening.openAccount(ADMIN, retailOpening().build()).id();
            clearInvocations(publisher);

            movements.withdraw(ADMIN, WithdrawCommand.builder().accountId(id).amount(new BigDecimal("5000")).build());

            assertThat(publishedBatches(publisher)).singleElement().satisfies(events -> assertThat(events)
                    .map(DomainEvent::getClass).containsExactly(WithdrawalRejected.class));
        }

        @Test
        @DisplayName("Si guardar la cuenta falla, no se publica ningún evento")
        void whenSavingTheAccountFailsNoEventIsPublished() {
            UUID id = opening.openAccount(ADMIN, retailOpening().build()).id();
            clearInvocations(publisher);
            doThrow(new IllegalStateException("fallo al guardar")).when(repository).save(any(CheckingAccount.class));

            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                    movements.deposit(ADMIN, DepositCommand.builder().accountId(id).amount(BigDecimal.TEN).build()));
            verify(publisher, never()).publishAll(any());
        }

        @Test
        @DisplayName("Un caso de uso que falla antes de guardar no publica nada")
        void aUseCaseFailingBeforeSavingPublishesNothing() {
            String used = opening.openAccount(ADMIN, retailOpening().build()).accountNumber();
            AccountOpeningService faulty = ApplicationServicesFixture.builder()
                    .accounts(repository)
                    .publisher(publisher)
                    .factory(new CheckingAccountFactory(() -> new AccountNumber(used), TaxAgencyTestAdapters.incomeVerifier()))
                    .build()
                    .accountOpeningService();
            clearInvocations(publisher);

            assertThatExceptionOfType(DuplicateAccountNumberException.class)
                    .isThrownBy(() -> faulty.openAccount(ADMIN, retailOpening().build()));
            verify(publisher, never()).publishAll(any());
        }
    }

    @Nested
    @DisplayName("Unidad de trabajo")
    class UnitOfWorkBoundary {

        /** Unidad de trabajo que anota si hay una en curso y, si se le pide, falla al terminar (como un commit fallido). */
        private final class RecordingUnitOfWork implements UnitOfWork {
            private final AtomicBoolean inside = new AtomicBoolean(false);
            private boolean failOnFinish;

            @Override
            public <T> T execute(Supplier<T> work) {
                inside.set(true);
                try {
                    T result = work.get();
                    if (failOnFinish) {
                        throw new IllegalStateException("la unidad de trabajo no pudo terminar");
                    }
                    return result;
                } finally {
                    inside.set(false);
                }
            }
        }

        private final RecordingUnitOfWork unit = new RecordingUnitOfWork();
        private final AtomicBoolean publishedOutsideUnit = new AtomicBoolean(false);
        private int publications;
        private AccountOpeningService recorded;
        private MoneyMovementCommands recordedMovements;

        @BeforeEach
        void setUp() {
            DomainEventPublisher recordingPublisher = event -> {
                publications++;
                if (!unit.inside.get()) {
                    publishedOutsideUnit.set(true);
                }
            };
            ApplicationServicesFixture fixture = ApplicationServicesFixture.builder()
                    .publisher(recordingPublisher)
                    .unitOfWork(unit)
                    .build();
            recorded = fixture.accountOpeningService();
            recordedMovements = fixture.moneyMovementService();
        }

        @Test
        @DisplayName("Los eventos se publican dentro de la unidad de trabajo, para que un outbox comparta su transacción (#165)")
        void eventsArePublishedInsideTheUnitOfWork() {
            UUID id = recorded.openAccount(ADMIN, retailOpening().build()).id();
            recordedMovements.deposit(ADMIN, DepositCommand.builder().accountId(id).amount(BigDecimal.TEN).build());

            assertThat(publications).isPositive();
            assertThat(publishedOutsideUnit).as("algún evento se publicó fuera de la unidad").isFalse();
        }

        @Test
        @DisplayName("Si la unidad de trabajo no termina bien, sus eventos se publicaron dentro de ella y se deshacen con ella")
        void whenTheUnitDoesNotFinishWellItsEventsWerePublishedInsideIt() {
            UUID id = recorded.openAccount(ADMIN, retailOpening().build()).id();
            publications = 0;
            unit.failOnFinish = true;

            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                    recordedMovements.deposit(ADMIN, DepositCommand.builder().accountId(id).amount(BigDecimal.TEN).build()));
            assertThat(publications).isPositive();
            assertThat(publishedOutsideUnit).as("algún evento se publicó fuera de la unidad").isFalse();
        }
    }
}
