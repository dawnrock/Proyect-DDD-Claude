package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.pronoide.ddd.adapter.out.taxagency.TaxAgencyTestAdapters;
import com.pronoide.ddd.domain.model.CheckingAccountFactory;
import com.pronoide.ddd.domain.model.DomainEventPublisher;
import com.pronoide.ddd.domain.service.MoneyTransferService;
import com.pronoide.ddd.domain.service.SegmentReviewService;

/**
 * Operaciones simultáneas sobre las mismas cuentas (#78): el servicio real con los adaptadores en memoria, sin Spring.
 * Muchos hilos transfieren a la vez entre dos cuentas en los dos sentidos e ingresan en ellas; al terminar, el dinero
 * tiene que cuadrar y no puede faltar ningún movimiento.
 */
class CheckingAccountServiceConcurrencyTests {

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();
    private static final int THREADS = 8;
    private static final int OPERATIONS_PER_THREAD = 300;
    private static final int ROUNDS = 5;
    private static final BigDecimal INITIAL = new BigDecimal("100000.00");
    private static final BigDecimal ONE = new BigDecimal("1.00");

    private static CheckingAccountService newService() {
        DomainEventPublisher ignoreEvents = events -> { };
        return new CheckingAccountService(PersistenceTestAdapters.accounts(), PersistenceTestAdapters.ownership(),
                ignoreEvents, new CheckingAccountFactory(PersistenceTestAdapters.accountNumbers(),
                        TaxAgencyTestAdapters.incomeVerifier()), new MoneyTransferService(),
                new SegmentReviewService(TaxAgencyTestAdapters.incomeVerifier()), Clock.systemUTC(), PersistenceTestAdapters.unitOfWork());
    }

    private static AccountSummary open(CheckingAccountService service, String holder, String taxId, String owner) {
        return service.openAccount(ADMIN, OpenAccountCommand.builder().holderName(holder).holderTaxId(taxId)
                .initialDeposit(INITIAL).ownerUsername(owner).build());
    }

    @Test
    @DisplayName("Transferencias e ingresos simultáneos sobre las mismas cuentas no pierden dinero ni movimientos")
    void transferencias_e_ingresos_simultaneos_no_pierden_dinero_ni_movimientos() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            runRound();
        }
    }

    private void runRound() throws Exception {
        CheckingAccountService service = newService();
        AccountSummary ada = open(service, "Ada Lovelace", "12345678Z", "ada");
        AccountSummary alan = open(service, "Alan Turing", "11111111H", "alan");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            runConcurrently(service, ada, alan, pool);
        } finally {
            pool.shutdownNow();
        }
        assertBalancesAndTransactions(service, ada, alan);
    }

    private static void runConcurrently(CheckingAccountService service, AccountSummary ada, AccountSummary alan,
                                        ExecutorService pool) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Callable<Void> worker = () -> {
                start.await();
                for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                    service.transfer(ADMIN, TransferCommand.builder().sourceAccountId(ada.id())
                            .targetAccountNumber(alan.accountNumber()).amount(ONE).build());
                    service.transfer(ADMIN, TransferCommand.builder().sourceAccountId(alan.id())
                            .targetAccountNumber(ada.accountNumber()).amount(ONE).build());
                    service.deposit(ADMIN, DepositCommand.builder().accountId(ada.id()).amount(ONE).build());
                }
                return null;
            };
            results.add(pool.submit(worker));
        }
        start.countDown();
        for (Future<?> result : results) {
            result.get(60, TimeUnit.SECONDS);
        }
    }

    private static void assertBalancesAndTransactions(CheckingAccountService service, AccountSummary ada,
                                                      AccountSummary alan) {
        int operations = THREADS * OPERATIONS_PER_THREAD;
        AccountDetails adaDetails = service.findAccount(ADMIN, ada.id());
        AccountDetails alanDetails = service.findAccount(ADMIN, alan.id());

        BigDecimal deposits = ONE.multiply(BigDecimal.valueOf(operations));
        assertThat(adaDetails.balance().add(alanDetails.balance()))
                .as("dinero total").isEqualByComparingTo(INITIAL.add(INITIAL).add(deposits));
        assertThat(adaDetails.balance()).as("saldo de Ada = sus movimientos").isEqualByComparingTo(sumOf(adaDetails));
        assertThat(alanDetails.balance()).as("saldo de Alan = sus movimientos").isEqualByComparingTo(sumOf(alanDetails));
        // Inicial + una retirada y un ingreso por cada par de transferencias; Ada también tiene sus ingresos.
        assertThat(adaDetails.transactions()).as("movimientos de Ada").hasSize(1 + 3 * operations);
        assertThat(alanDetails.transactions()).as("movimientos de Alan").hasSize(1 + 2 * operations);
    }

    /** Suma de los movimientos aprobados: ingresos en positivo, retiradas en negativo. */
    private static BigDecimal sumOf(AccountDetails details) {
        return details.transactions().stream()
                .filter(t -> t.status().equals("APPROVED"))
                .map(t -> t.type().equals("DEPOSIT") ? t.amount() : t.amount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
