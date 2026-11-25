package com.dawnrock.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Historial de movimientos de una cuenta (#182): sus movimientos en orden y la comprobación de una cuenta guardada. */
class TransactionHistoryTests {

    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final Instant DAY = Instant.parse("2026-11-03T10:15:30Z");

    private static Transaction movement(UUID accountId, TransactionType type, String amount, TransactionStatus status) {
        return Transaction.reconstitute(UUID.randomUUID(), accountId, type, MonetaryAmount.euros(amount), DAY, null,
                status);
    }

    private static Transaction approvedDeposit(String amount) {
        return movement(ACCOUNT, TransactionType.DEPOSIT, amount, TransactionStatus.APPROVED);
    }

    @Test
    @DisplayName("Un historial nuevo está vacío y guarda los movimientos en el orden en que llegan")
    void aNewHistoryIsEmptyAndKeepsTheOrderOfItsTransactions() {
        TransactionHistory history = new TransactionHistory();
        assertThat(history.asList()).isEmpty();

        Transaction first = approvedDeposit("100.00");
        Transaction second = approvedDeposit("20.00");

        assertThat(history.add(first)).isSameAs(first);
        history.add(second);
        assertThat(history.asList()).containsExactly(first, second);
    }

    @Test
    @DisplayName("La lista que da el historial es una copia: cambiarla no cambia el historial")
    void theListItGivesIsACopy() {
        TransactionHistory history = new TransactionHistory();
        history.add(approvedDeposit("100.00"));

        List<Transaction> copy = history.asList();

        assertThatThrownBy(() -> copy.add(approvedDeposit("1.00"))).isInstanceOf(UnsupportedOperationException.class);
        assertThat(history.asList()).hasSize(1);
    }

    @Nested
    @DisplayName("Recuperar el historial de una cuenta guardada")
    class Restore {

        @Test
        @DisplayName("conserva un historial coherente; las retiradas rechazadas no cuentan para el saldo")
        void keepsAConsistentHistoryWhereRejectedWithdrawalsDoNotCount() {
            List<Transaction> stored = List.of(approvedDeposit("1000.00"),
                    movement(ACCOUNT, TransactionType.WITHDRAWAL, "50.00", TransactionStatus.APPROVED),
                    movement(ACCOUNT, TransactionType.WITHDRAWAL, "5000.00", TransactionStatus.REJECTED));

            TransactionHistory history = TransactionHistory.restore(ACCOUNT, stored, MonetaryAmount.euros("950.00"));

            assertThat(history.asList()).containsExactlyElementsOf(stored);
        }

        @Test
        @DisplayName("rechaza un movimiento repetido")
        void rejectsARepeatedTransaction() {
            Transaction deposit = approvedDeposit("100.00");

            assertThatThrownBy(() -> TransactionHistory.restore(ACCOUNT, List.of(deposit, deposit),
                    MonetaryAmount.euros("200.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Una cuenta guardada no repite movimientos");
        }

        @Test
        @DisplayName("rechaza un movimiento de otra cuenta")
        void rejectsATransactionOfAnotherAccount() {
            List<Transaction> stored = List.of(
                    movement(UUID.randomUUID(), TransactionType.DEPOSIT, "100.00", TransactionStatus.APPROVED));

            assertThatThrownBy(() -> TransactionHistory.restore(ACCOUNT, stored, MonetaryAmount.euros("100.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Un movimiento de la cuenta reconstituida pertenece a otra cuenta");
        }

        @Test
        @DisplayName("rechaza un movimiento pendiente")
        void rejectsAPendingTransaction() {
            List<Transaction> stored = List.of(
                    movement(ACCOUNT, TransactionType.DEPOSIT, "100.00", TransactionStatus.PENDING));

            assertThatThrownBy(() -> TransactionHistory.restore(ACCOUNT, stored, MonetaryAmount.euros("0.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Una cuenta guardada no tiene movimientos pendientes");
        }

        @Test
        @DisplayName("rechaza un saldo que no cuadra con los movimientos aprobados")
        void rejectsABalanceThatDoesNotMatchTheApprovedTransactions() {
            assertThatThrownBy(() -> TransactionHistory.restore(ACCOUNT, List.of(approvedDeposit("100.00")),
                    MonetaryAmount.euros("90.00")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("El saldo guardado 90.00 EUR no cuadra con los movimientos aprobados (100.00 EUR)");
        }

        @Test
        @DisplayName("exige la lista de movimientos")
        void requiresTheListOfTransactions() {
            assertThatNullPointerException()
                    .isThrownBy(() -> TransactionHistory.restore(ACCOUNT, null, MonetaryAmount.euros("0.00")))
                    .withMessage("Los movimientos son obligatorios");
        }
    }
}
