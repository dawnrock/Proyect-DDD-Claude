package com.dawnrock.ddd.application;

import static com.dawnrock.ddd.application.ServiceTestSupport.ADMIN;
import static com.dawnrock.ddd.application.ServiceTestSupport.highNetWorthOpening;
import static com.dawnrock.ddd.application.ServiceTestSupport.publishedBatches;
import static com.dawnrock.ddd.application.ServiceTestSupport.retailOpening;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
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
import com.dawnrock.ddd.domain.model.CurrencyMismatchException;
import com.dawnrock.ddd.domain.model.DomainEvent;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.model.DuplicateAccountNumberException;
import com.dawnrock.ddd.domain.model.InsufficientInitialDepositException;
import com.dawnrock.ddd.domain.model.MoneyDeposited;
import com.dawnrock.ddd.domain.model.WithdrawalRejected;
import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.adapter.out.taxagency.TaxAgencyTestAdapters;
import com.dawnrock.ddd.domain.model.CheckingAccountFactory;
import com.dawnrock.ddd.support.MutableClock;
import com.dawnrock.ddd.support.ApplicationServicesFixture;

/** Casos de uso con el adaptador en memoria real detrás de los puertos: no hace falta simularlo. */
class CheckingAccountServiceTests {

    private CheckingAccountRepository repository;
    private DomainEventPublisher publisher;
    private CheckingAccountService service;
    private MoneyMovementCommands movements;

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        repository = spy(PersistenceTestAdapters.accounts());
        publisher = mock(DomainEventPublisher.class);
        ApplicationServicesFixture fixture = ApplicationServicesFixture.builder()
                .accounts(repository)
                .publisher(publisher)
                .clock(clock)
                .build();
        service = fixture.service();
        movements = fixture.moneyMovementService();
    }

    @Test
    @DisplayName("El servicio ofrece los casos de uso por sus puertos de entrada de commands y de queries")
    void theServiceOffersTheUseCasesThroughItsCommandAndQueryInputPorts() {
        assertThat(service).isInstanceOf(CheckingAccountCommands.class).isInstanceOf(CheckingAccountQueries.class);
    }

    @Nested
    @DisplayName("Abrir cuenta con la factoría")
    class OpenAccountThroughFactory {

        private OpenAccountCommand.Builder byTaxId(String taxId) {
            return OpenAccountCommand.builder()
                    .holderName("Grace Hopper")
                    .holderTaxId(taxId)
                    .holderEmail("grace@example.com")
                    .initialDeposit(new BigDecimal("100"))
                    .ownerUsername("grace");
        }

        @Test
        @DisplayName("Con NIF la cuenta recibe un IBAN generado y el resumen lo devuelve")
        void withATaxIdTheAccountGetsAGeneratedIban() {
            AccountSummary summary = service.openAccount(ADMIN, byTaxId("87654321X").build());

            assertThat(summary.accountNumber()).startsWith("ES").hasSize(24);
            assertThat(repository.findByAccountNumber(new AccountNumber(summary.accountNumber()))).isPresent();
        }

        @Test
        @DisplayName("Con ingresos verificados por encima del umbral el resumen es de alto patrimonio")
        void withHighIncomeTheSummaryIsHighNetWorth() {
            AccountSummary summary = service.openAccount(ADMIN, byTaxId("87654321X").build());

            assertThat(summary.segment()).isEqualTo("HIGH_NET_WORTH");
        }

        @Test
        @DisplayName("Con ingresos bajos o un NIF desconocido el resumen es minorista")
        void withLowIncomeOrAnUnknownTaxIdTheSummaryIsRetail() {
            assertThat(service.openAccount(ADMIN, byTaxId("12345678Z").build()).segment()).isEqualTo("RETAIL");
            assertThat(service.openAccount(ADMIN, byTaxId("99999999R").holderName("Alan Turing")
                    .holderEmail("alan@example.com").ownerUsername("alan").build()).segment()).isEqualTo("RETAIL");
        }

        @Test
        @DisplayName("Un número generado que ya está en uso se rechaza y no se guarda")
        void aGeneratedNumberAlreadyInUseIsRejected() {
            String used = service.openAccount(ADMIN, retailOpening().build()).accountNumber();
            CheckingAccountService faulty = ApplicationServicesFixture.builder()
                    .accounts(repository)
                    .publisher(publisher)
                    .factory(new CheckingAccountFactory(() -> new AccountNumber(used), TaxAgencyTestAdapters.incomeVerifier()))
                    .clock(clock)
                    .build()
                    .service();

            assertThatExceptionOfType(DuplicateAccountNumberException.class)
                    .isThrownBy(() -> faulty.openAccount(ADMIN, byTaxId("87654321X").build()))
                    .withMessageContaining(used);
            assertThat(repository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("Con NIF se publican los eventos de apertura y se asigna el propietario")
        void withATaxIdTheEventsArePublishedAndTheOwnerIsAssigned() {
            clearInvocations(publisher);

            AccountSummary summary = service.openAccount(ADMIN, byTaxId("87654321X").build());

            assertThat(publishedBatches(publisher)).singleElement().satisfies(events -> assertThat(events)
                    .hasAtLeastOneElementOfType(CheckingAccountOpened.class));
            assertThat(service.listAccounts(Caller.builder().username("grace").admin(false).build()))
                    .extracting(AccountSummary::id).containsExactly(summary.id());
        }

        @Test
        @DisplayName("Un depósito válido abre la cuenta, la guarda y devuelve su resumen")
        void aValidDepositOpensSavesAndReturnsTheAccountSummary() {
            AccountSummary summary = service.openAccount(ADMIN, retailOpening().build());

            assertThat(summary.holderName()).isEqualTo("Ada Lovelace");
            assertThat(summary.balance()).isEqualByComparingTo("100.00");
            assertThat(summary.balance().scale()).isEqualTo(2);
            assertThat(summary.currency()).isEqualTo("EUR");
            assertThat(repository.findById(summary.id())).isPresent();
        }

        @Test
        @DisplayName("Un depósito en otra divisa se rechaza y no se guarda")
        void aDepositInAnotherCurrencyIsRejectedAndNotSaved() {
            OpenAccountCommand inDollars = retailOpening().currency("USD").build();

            assertThatExceptionOfType(CurrencyMismatchException.class)
                    .isThrownBy(() -> service.openAccount(ADMIN, inDollars));
            assertThat(repository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Un depósito por debajo del mínimo se rechaza también con NIF")
        void aLowDepositIsRejectedAlsoWithATaxId() {
            OpenAccountCommand tooLow = byTaxId("87654321X").initialDeposit(new BigDecimal("49.99")).build();

            assertThatExceptionOfType(InsufficientInitialDepositException.class)
                    .isThrownBy(() -> service.openAccount(ADMIN, tooLow));
            assertThat(repository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Consultar cuenta")
    class FindAccount {

        @Test
        @DisplayName("Una cuenta existente se devuelve con el depósito inicial entre sus movimientos")
        void anExistingAccountIsReturnedWithTheInitialDepositAmongItsTransactions() {
            AccountSummary opened = service.openAccount(ADMIN, highNetWorthOpening().build());

            AccountDetails details = service.findAccount(ADMIN, opened.id());

            assertThat(details.id()).isEqualTo(opened.id());
            assertThat(details.accountNumber()).isEqualTo(opened.accountNumber());
            assertThat(details.holderName()).isEqualTo("Grace Hopper");
            assertThat(details.segment()).isEqualTo("HIGH_NET_WORTH");
            assertThat(details.balance()).isEqualByComparingTo("100.00");
            assertThat(details.currency()).isEqualTo("EUR");
            assertThat(details.transactions()).singleElement().satisfies(transaction -> {
                assertThat(transaction.type()).isEqualTo("DEPOSIT");
                assertThat(transaction.amount()).isEqualByComparingTo("100.00");
                assertThat(transaction.currency()).isEqualTo("EUR");
                assertThat(transaction.status()).isEqualTo("APPROVED");
                assertThat(transaction.occurredOn()).isNotNull();
            });
        }

        @Test
        @DisplayName("Una cuenta desconocida lanza AccountNotFoundException")
        void anUnknownAccountThrowsAccountNotFoundException() {
            UUID unknown = UUID.randomUUID();

            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.findAccount(ADMIN, unknown))
                    .withMessageContaining(unknown.toString());
        }
    }

    @Nested
    @DisplayName("Listar cuentas")
    class ListAccounts {

        @Test
        @DisplayName("Sin cuentas devuelve una lista vacía")
        void withNoAccountsReturnsAnEmptyList() {
            assertThat(service.listAccounts(ADMIN)).isEmpty();
        }

        @Test
        @DisplayName("Varias cuentas se devuelven ordenadas por número de cuenta, con su segmento")
        void severalAccountsAreReturnedSortedByAccountNumber() {
            AccountSummary grace = service.openAccount(ADMIN, highNetWorthOpening().build());
            AccountSummary ada = service.openAccount(ADMIN, retailOpening().build());

            // El orden lo decide el IBAN completo, dígitos de control incluidos, no el orden de apertura
            assertThat(service.listAccounts(ADMIN))
                    .isSortedAccordingTo(java.util.Comparator.comparing(AccountSummary::accountNumber))
                    .extracting(AccountSummary::accountNumber, AccountSummary::holderName, AccountSummary::segment)
                    .containsExactlyInAnyOrder(
                            tuple(grace.accountNumber(), "Grace Hopper", "HIGH_NET_WORTH"),
                            tuple(ada.accountNumber(), "Ada Lovelace", "RETAIL"));
        }
    }

    @Nested
    @DisplayName("Publicar los eventos de dominio")
    class PublishDomainEvents {

        @Test
        @DisplayName("Abrir una cuenta publica cuenta abierta y el ingreso inicial después de guardarla")
        void openingAnAccountPublishesItsEventsAfterSavingIt() {
            service.openAccount(ADMIN, retailOpening().build());

            InOrder order = inOrder(repository, publisher);
            order.verify(repository).save(any(CheckingAccount.class));
            order.verify(publisher).publishAll(any());
            assertThat(publishedBatches(publisher)).singleElement().satisfies(events -> assertThat(events)
                    .map(DomainEvent::getClass).containsExactly(CheckingAccountOpened.class, MoneyDeposited.class));
        }

        @Test
        @DisplayName("Un ingreso publica el evento de dinero ingresado después de guardar la cuenta")
        void aDepositPublishesItsEventAfterSaving() {
            UUID id = service.openAccount(ADMIN, retailOpening().build()).id();
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
            UUID id = service.openAccount(ADMIN, retailOpening().build()).id();
            clearInvocations(publisher);

            movements.withdraw(ADMIN, WithdrawCommand.builder().accountId(id).amount(new BigDecimal("5000")).build());

            assertThat(publishedBatches(publisher)).singleElement().satisfies(events -> assertThat(events)
                    .map(DomainEvent::getClass).containsExactly(WithdrawalRejected.class));
        }

        @Test
        @DisplayName("Si guardar la cuenta falla, no se publica ningún evento")
        void whenSavingTheAccountFailsNoEventIsPublished() {
            UUID id = service.openAccount(ADMIN, retailOpening().build()).id();
            clearInvocations(publisher);
            doThrow(new IllegalStateException("fallo al guardar")).when(repository).save(any(CheckingAccount.class));

            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                    movements.deposit(ADMIN, DepositCommand.builder().accountId(id).amount(BigDecimal.TEN).build()));
            verify(publisher, never()).publishAll(any());
        }

        @Test
        @DisplayName("Un caso de uso que falla antes de guardar no publica nada")
        void aUseCaseFailingBeforeSavingPublishesNothing() {
            String used = service.openAccount(ADMIN, retailOpening().build()).accountNumber();
            CheckingAccountService faulty = ApplicationServicesFixture.builder()
                    .accounts(repository)
                    .publisher(publisher)
                    .factory(new CheckingAccountFactory(() -> new AccountNumber(used), TaxAgencyTestAdapters.incomeVerifier()))
                    .clock(clock)
                    .build()
                    .service();
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
        private CheckingAccountService recorded;
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
                    .clock(clock)
                    .unitOfWork(unit)
                    .build();
            recorded = fixture.service();
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
