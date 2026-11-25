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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;

import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.domain.model.CurrencyMismatchException;
import com.dawnrock.ddd.domain.model.DomainEvent;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.model.CustomerSegmentChanged;
import com.dawnrock.ddd.domain.model.MoneyDeposited;
import com.dawnrock.ddd.domain.model.MoneyWithdrawn;
import com.dawnrock.ddd.domain.model.SameAccountTransferException;
import com.dawnrock.ddd.domain.model.WithdrawalRejected;
import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.support.MutableClock;
import com.dawnrock.ddd.support.ApplicationServicesFixture;

/** Ingresos, retiradas y transferencias con los adaptadores en memoria detrás de los puertos (#174). */
class MoneyMovementServiceTests {

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

    @Nested
    @DisplayName("Ingresar")
    class Deposit {

        @Test
        @DisplayName("Un ingreso aprobado actualiza el saldo y aparece entre los movimientos")
        void anApprovedDepositUpdatesTheBalanceAndAppearsAmongTheTransactions() {
            UUID id = service.openAccount(ADMIN, retailOpening().build()).id();

            TransactionSummary transaction = movements.deposit(ADMIN, 
                    DepositCommand.builder().accountId(id).amount(new BigDecimal("25.50")).build());

            assertThat(transaction.type()).isEqualTo("DEPOSIT");
            assertThat(transaction.status()).isEqualTo("APPROVED");
            assertThat(transaction.amount()).isEqualByComparingTo("25.50");
            assertThat(transaction.currency()).isEqualTo("EUR");
            AccountDetails details = service.findAccount(ADMIN, id);
            assertThat(details.balance()).isEqualByComparingTo("125.50");
            assertThat(details.transactions()).extracting(TransactionSummary::id).last().isEqualTo(transaction.id());
            assertThat(details.transactions()).hasSize(2);
        }

        @Test
        @DisplayName("Una cuenta desconocida lanza AccountNotFoundException")
        void anUnknownAccountThrowsAccountNotFoundException() {
            UUID unknown = UUID.randomUUID();
            DepositCommand command = DepositCommand.builder().accountId(unknown).amount(BigDecimal.TEN).build();

            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> movements.deposit(ADMIN, command))
                    .withMessageContaining(unknown.toString());
        }

        @Test
        @DisplayName("Un ingreso en otra divisa lanza CurrencyMismatchException y no cambia el saldo")
        void aDepositInAnotherCurrencyThrowsCurrencyMismatchExceptionAndKeepsTheBalance() {
            UUID id = service.openAccount(ADMIN, retailOpening().build()).id();
            DepositCommand dollars =
                    DepositCommand.builder().accountId(id).amount(BigDecimal.TEN).currency("USD").build();

            assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> movements.deposit(ADMIN, dollars));

            AccountDetails details = service.findAccount(ADMIN, id);
            assertThat(details.balance()).isEqualByComparingTo("100.00");
            assertThat(details.transactions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Retirar")
    class Withdraw {

        @ParameterizedTest(name = "{0} con 100.00 retira {1}: {2}, saldo {3}")
        @DisplayName("Las retiradas en el límite del descubierto se aprueban o se rechazan según el segmento")
        @CsvSource({
                "RETAIL, 100.00, APPROVED, 0.00",
                "RETAIL, 100.01, REJECTED, 100.00",
                "HIGH_NET_WORTH, 1100.00, APPROVED, -1000.00",
                "HIGH_NET_WORTH, 1100.01, REJECTED, 100.00"
        })
        void withdrawalsAtTheOverdraftLimitDependOnTheSegment(
                String segment, String amount, String status, String finalBalance) {
            UUID id = service.openAccount(ADMIN,
                    ("HIGH_NET_WORTH".equals(segment) ? highNetWorthOpening() : retailOpening()).build()).id();

            TransactionSummary transaction = movements.withdraw(ADMIN, 
                    WithdrawCommand.builder().accountId(id).amount(new BigDecimal(amount)).build());

            assertThat(transaction.type()).isEqualTo("WITHDRAWAL");
            assertThat(transaction.status()).isEqualTo(status);
            assertThat(transaction.amount()).isEqualByComparingTo(amount);
            assertThat(service.findAccount(ADMIN, id).balance()).isEqualByComparingTo(finalBalance);
        }

        @Test
        @DisplayName("Una retirada rechazada no cambia el saldo pero queda registrada")
        void aRejectedWithdrawalKeepsTheBalanceButIsRecorded() {
            UUID id = service.openAccount(ADMIN, retailOpening().build()).id();

            TransactionSummary transaction = movements.withdraw(ADMIN, 
                    WithdrawCommand.builder().accountId(id).amount(new BigDecimal("500")).build());

            AccountDetails details = service.findAccount(ADMIN, id);
            assertThat(transaction.status()).isEqualTo("REJECTED");
            assertThat(details.balance()).isEqualByComparingTo("100.00");
            assertThat(details.transactions()).hasSize(2);
            assertThat(details.transactions()).last().satisfies(last -> {
                assertThat(last.id()).isEqualTo(transaction.id());
                assertThat(last.status()).isEqualTo("REJECTED");
            });
        }

        @Test
        @DisplayName("Una cuenta desconocida lanza AccountNotFoundException")
        void anUnknownAccountThrowsAccountNotFoundException() {
            UUID unknown = UUID.randomUUID();
            WithdrawCommand command = WithdrawCommand.builder().accountId(unknown).amount(BigDecimal.TEN).build();

            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> movements.withdraw(ADMIN, command))
                    .withMessageContaining(unknown.toString());
        }

        @Test
        @DisplayName("Una retirada en otra divisa lanza CurrencyMismatchException y no cambia el saldo")
        void aWithdrawalInAnotherCurrencyThrowsCurrencyMismatchExceptionAndKeepsTheBalance() {
            UUID id = service.openAccount(ADMIN, retailOpening().build()).id();
            WithdrawCommand dollars =
                    WithdrawCommand.builder().accountId(id).amount(BigDecimal.TEN).currency("USD").build();

            assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> movements.withdraw(ADMIN, dollars));

            AccountDetails details = service.findAccount(ADMIN, id);
            assertThat(details.balance()).isEqualByComparingTo("100.00");
            assertThat(details.transactions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Transferir")
    class Transfer {

        private UUID sourceId;
        private UUID targetId;
        private String sourceNumber;
        private String targetNumber;

        @BeforeEach
        void openAccounts() {
            AccountSummary source = service.openAccount(ADMIN, retailOpening().initialDeposit(new BigDecimal("500")).build());
            AccountSummary target = service.openAccount(ADMIN, retailOpening().initialDeposit(new BigDecimal("100")).build());
            sourceId = source.id();
            targetId = target.id();
            sourceNumber = source.accountNumber();
            targetNumber = target.accountNumber();
            clearInvocations(repository);
        }

        private TransferCommand transfer(UUID source, String targetAccountNumber, String amount) {
            return TransferCommand.builder()
                    .sourceAccountId(source)
                    .targetAccountNumber(targetAccountNumber)
                    .amount(new BigDecimal(amount))
                    .build();
        }

        @Test
        @DisplayName("Una transferencia aprobada publica la retirada del origen y el ingreso del destino tras guardarlas")
        void anApprovedTransferPublishesItsEventsAfterSavingBothAccounts() {
            clearInvocations(publisher);

            movements.transfer(ADMIN, transfer(sourceId, targetNumber, "200"));

            InOrder order = inOrder(repository, publisher);
            order.verify(repository, times(2)).save(any(CheckingAccount.class));
            order.verify(publisher, times(2)).publishAll(any());
            assertThat(publishedBatches(publisher)).flatMap(events -> events)
                    .extracting(DomainEvent::getClass, DomainEvent::accountId)
                    .containsExactly(tuple(MoneyWithdrawn.class, sourceId), tuple(MoneyDeposited.class, targetId));
        }

        @Test
        @DisplayName("Una transferencia rechazada solo publica la retirada rechazada del origen")
        void aRejectedTransferOnlyPublishesTheRejectedWithdrawal() {
            clearInvocations(publisher);

            movements.transfer(ADMIN, transfer(sourceId, targetNumber, "5000"));

            assertThat(publishedBatches(publisher)).flatMap(events -> events)
                    .extracting(DomainEvent::getClass, DomainEvent::accountId)
                    .containsExactly(tuple(WithdrawalRejected.class, sourceId));
        }

        @Test
        @DisplayName("Una transferencia aprobada actualiza los saldos de las dos cuentas y guarda ambas")
        void anApprovedTransferUpdatesTheBalancesAndSavesBothAccounts() {
            TransferSummary summary = movements.transfer(ADMIN, transfer(sourceId, targetNumber, "200"));

            assertThat(summary.status()).isEqualTo("APPROVED");
            assertThat(summary.sourceAccountId()).isEqualTo(sourceId);
            assertThat(summary.sourceAccountNumber()).isEqualTo(sourceNumber);
            assertThat(summary.targetAccountId()).isEqualTo(targetId);
            assertThat(summary.targetAccountNumber()).isEqualTo(targetNumber);
            assertThat(summary.amount()).isEqualByComparingTo("200.00");
            assertThat(summary.amount().scale()).isEqualTo(2);
            assertThat(summary.currency()).isEqualTo("EUR");
            assertThat(summary.occurredOn()).isNotNull();
            assertThat(service.findAccount(ADMIN, sourceId).balance()).isEqualByComparingTo("300.00");
            assertThat(service.findAccount(ADMIN, targetId).balance()).isEqualByComparingTo("300.00");
            verify(repository, times(2)).save(any(CheckingAccount.class));
            CheckingAccount source = repository.findById(sourceId).orElseThrow();
            CheckingAccount target = repository.findById(targetId).orElseThrow();
            verify(repository).save(source);
            verify(repository).save(target);
        }

        @Test
        @DisplayName("Tras una transferencia, el detalle de ambas cuentas muestra la contrapartida solo en sus movimientos")
        void afterATransferTheDetailsShowTheCounterpartOnlyInItsTransactions() {
            movements.transfer(ADMIN, transfer(sourceId, targetNumber, "200"));

            List<TransactionSummary> inSource = service.findAccount(ADMIN, sourceId).transactions();
            List<TransactionSummary> inTarget = service.findAccount(ADMIN, targetId).transactions();
            assertThat(inSource).extracting(TransactionSummary::type, TransactionSummary::counterpartAccountNumber)
                    .containsExactly(tuple("DEPOSIT", Optional.empty()), tuple("WITHDRAWAL", Optional.of(targetNumber)));
            assertThat(inTarget).extracting(TransactionSummary::type, TransactionSummary::counterpartAccountNumber)
                    .containsExactly(tuple("DEPOSIT", Optional.empty()), tuple("DEPOSIT", Optional.of(sourceNumber)));
        }

        @Test
        @DisplayName("Una transferencia rechazada deja la contrapartida en la retirada rechazada del origen")
        void aRejectedTransferKeepsTheCounterpartInTheRejectedWithdrawal() {
            movements.transfer(ADMIN, transfer(sourceId, targetNumber, "600"));

            assertThat(service.findAccount(ADMIN, sourceId).transactions()).last()
                    .returns("REJECTED", TransactionSummary::status)
                    .returns(Optional.of(targetNumber), TransactionSummary::counterpartAccountNumber);
        }

        @Test
        @DisplayName("Un ingreso o una retirada normales no llevan contrapartida")
        void regularDepositsAndWithdrawalsHaveNoCounterpart() {
            TransactionSummary deposit = movements.deposit(ADMIN,
                    DepositCommand.builder().accountId(sourceId).amount(new BigDecimal("10")).build());

            assertThat(deposit.counterpartAccountNumber()).isEmpty();
        }

        @Test
        @DisplayName("Una transferencia rechazada se devuelve como REJECTED y no cambia ningún saldo")
        void aRejectedTransferIsReturnedAsRejectedAndKeepsTheBalances() {
            TransferSummary summary = movements.transfer(ADMIN, transfer(sourceId, targetNumber, "600"));

            assertThat(summary.status()).isEqualTo("REJECTED");
            assertThat(service.findAccount(ADMIN, sourceId).balance()).isEqualByComparingTo("500.00");
            assertThat(service.findAccount(ADMIN, sourceId).transactions()).hasSize(2);
            assertThat(service.findAccount(ADMIN, targetId).balance()).isEqualByComparingTo("100.00");
            assertThat(service.findAccount(ADMIN, targetId).transactions()).hasSize(1);
            verify(repository, times(2)).save(any(CheckingAccount.class));
        }

        @Test
        @DisplayName("Una cuenta de origen desconocida lanza AccountNotFoundException y no guarda nada")
        void anUnknownSourceAccountThrowsAccountNotFoundException() {
            UUID unknown = UUID.randomUUID();

            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> movements.transfer(ADMIN, transfer(unknown, targetNumber, "10")))
                    .withMessageContaining(unknown.toString());
            verify(repository, never()).save(any(CheckingAccount.class));
        }

        @Test
        @DisplayName("Un número de cuenta de destino desconocido lanza TargetAccountNotFoundException y no mueve dinero")
        void anUnknownTargetAccountNumberThrowsTargetAccountNotFoundException() {
            assertThatExceptionOfType(TargetAccountNotFoundException.class)
                    .isThrownBy(() -> movements.transfer(ADMIN, transfer(sourceId, "es00 0000 0000 0000 0000 0099", "10")))
                    .withMessageContaining("ES0000000000000000000099");
            assertThat(service.findAccount(ADMIN, sourceId).balance()).isEqualByComparingTo("500.00");
            assertThat(service.findAccount(ADMIN, sourceId).transactions()).hasSize(1);
            verify(repository, never()).save(any(CheckingAccount.class));
        }

        @Test
        @DisplayName("Transferir a la misma cuenta lanza SameAccountTransferException y no registra movimientos")
        void transferringToTheSameAccountThrowsSameAccountTransferException() {
            assertThatExceptionOfType(SameAccountTransferException.class)
                    .isThrownBy(() -> movements.transfer(ADMIN, transfer(sourceId, sourceNumber, "10")));
            assertThat(service.findAccount(ADMIN, sourceId).transactions()).hasSize(1);
            verify(repository, never()).save(any(CheckingAccount.class));
        }

        @Test
        @DisplayName("Una transferencia en otra divisa lanza CurrencyMismatchException y no cambia los saldos")
        void aTransferInAnotherCurrencyThrowsCurrencyMismatchException() {
            TransferCommand dollars = TransferCommand.builder()
                    .sourceAccountId(sourceId)
                    .targetAccountNumber(targetNumber)
                    .amount(BigDecimal.TEN)
                    .currency("USD")
                    .build();

            assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> movements.transfer(ADMIN, dollars));
            assertThat(service.findAccount(ADMIN, sourceId).balance()).isEqualByComparingTo("500.00");
            assertThat(service.findAccount(ADMIN, targetId).balance()).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    @DisplayName("Revisión del segmento antes de un cargo")
    class SegmentReviewBeforeDebit {

        /** Abre con el patrimonio mínimo (alto patrimonio por saldo) y retira hasta dejar la cuenta en descubierto. */
        private UUID cuentaEnDescubierto(OpenAccountCommand.Builder command) {
            UUID id = service.openAccount(ADMIN, command.initialDeposit(new BigDecimal("100000")).build()).id();
            movements.withdraw(ADMIN, WithdrawCommand.builder().accountId(id).amount(new BigDecimal("100500")).build());
            return id;
        }

        private TransactionSummary retirar(UUID id, String amount) {
            return movements.withdraw(ADMIN,
                    WithdrawCommand.builder().accountId(id).amount(new BigDecimal(amount)).build());
        }

        @Test
        @DisplayName("Tras un mes bajo el mínimo y sin ingresos altos, la retirada se evalúa ya como minorista")
        void afterAMonthBelowTheMinimumWithoutHighIncomeTheWithdrawalIsEvaluatedAsRetail() {
            UUID id = cuentaEnDescubierto(retailOpening());
            clock.advance(Duration.ofDays(32));

            assertThat(retirar(id, "10").status()).isEqualTo("REJECTED");
            assertThat(service.findAccount(ADMIN, id).segment()).isEqualTo("RETAIL");
        }

        @Test
        @DisplayName("Antes de un mes la retirada sigue contando con el descubierto de alto patrimonio")
        void beforeAMonthTheWithdrawalStillCountsOnTheOverdraft() {
            UUID id = cuentaEnDescubierto(retailOpening());
            clock.advance(Duration.ofDays(20));

            assertThat(retirar(id, "10").status()).isEqualTo("APPROVED");
            assertThat(service.findAccount(ADMIN, id).segment()).isEqualTo("HIGH_NET_WORTH");
        }

        @Test
        @DisplayName("Tras un mes bajo el mínimo, un titular con ingresos altos verificados conserva el segmento")
        void afterAMonthBelowTheMinimumAHolderWithHighIncomeKeepsTheSegment() {
            UUID id = cuentaEnDescubierto(highNetWorthOpening());
            clock.advance(Duration.ofDays(32));

            assertThat(retirar(id, "10").status()).isEqualTo("APPROVED");
            assertThat(service.findAccount(ADMIN, id).segment()).isEqualTo("HIGH_NET_WORTH");
        }

        @Test
        @DisplayName("Tras un mes bajo el mínimo y sin ingresos altos, la transferencia se evalúa ya como minorista")
        void afterAMonthBelowTheMinimumWithoutHighIncomeTheTransferIsEvaluatedAsRetail() {
            UUID id = cuentaEnDescubierto(retailOpening());
            String target = service.openAccount(ADMIN, highNetWorthOpening().build()).accountNumber();
            clock.advance(Duration.ofDays(32));

            TransferSummary transferencia = movements.transfer(ADMIN, TransferCommand.builder()
                    .sourceAccountId(id).targetAccountNumber(target).amount(new BigDecimal("10")).build());

            assertThat(transferencia.status()).isEqualTo("REJECTED");
            assertThat(service.findAccount(ADMIN, id).segment()).isEqualTo("RETAIL");
        }
    }

    @Nested
    @DisplayName("La revisión del segmento no se pierde si el cargo falla")
    class SegmentReviewSurvivesAFailedDebit {

        @Test
        @DisplayName("Una retirada no válida sobre una cuenta con la revisión vencida deja al titular en minorista y publica el cambio")
        void anInvalidWithdrawalDoesNotLoseTheDueReview() {
            UUID id = service.openAccount(ADMIN, retailOpening().initialDeposit(new BigDecimal("100000")).build()).id();
            movements.withdraw(ADMIN, WithdrawCommand.builder().accountId(id).amount(new BigDecimal("500")).build());
            clock.advance(Duration.ofDays(32));
            clearInvocations(publisher);

            assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> movements.withdraw(ADMIN,
                    WithdrawCommand.builder().accountId(id).amount(BigDecimal.TEN).currency("USD").build()));

            assertThat(service.findAccount(ADMIN, id).segment()).isEqualTo("RETAIL");
            assertThat(publishedBatches(publisher).stream().flatMap(List::stream))
                    .singleElement().isInstanceOf(CustomerSegmentChanged.class);
        }

        @Test
        @DisplayName("Una transferencia no válida sobre una cuenta con la revisión vencida deja al titular en minorista y publica el cambio")
        void anInvalidTransferDoesNotLoseTheDueReview() {
            AccountSummary source = service.openAccount(ADMIN, retailOpening().initialDeposit(new BigDecimal("100000")).build());
            movements.withdraw(ADMIN,
                    WithdrawCommand.builder().accountId(source.id()).amount(new BigDecimal("500")).build());
            clock.advance(Duration.ofDays(32));
            clearInvocations(publisher);

            assertThatExceptionOfType(SameAccountTransferException.class).isThrownBy(() -> movements.transfer(ADMIN,
                    TransferCommand.builder().sourceAccountId(source.id())
                            .targetAccountNumber(source.accountNumber()).amount(BigDecimal.TEN).build()));

            assertThat(service.findAccount(ADMIN, source.id()).segment()).isEqualTo("RETAIL");
            assertThat(publishedBatches(publisher).stream().flatMap(List::stream))
                    .singleElement().isInstanceOf(CustomerSegmentChanged.class);
        }
    }
}
