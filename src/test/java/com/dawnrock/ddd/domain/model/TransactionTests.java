package com.dawnrock.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TransactionTests {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final Instant OCCURRED_ON = Instant.parse("2026-11-03T10:15:30Z");
    private static final AccountNumber COUNTERPART = new AccountNumber("ES9121000418450200051332");

    @Nested
    @DisplayName("Reconstitución desde la persistencia (#150)")
    class Reconstitution {

        @Test
        @DisplayName("Un movimiento reconstituido conserva todos sus datos y su identidad")
        void aReconstitutedTransactionKeepsAllItsDataAndIdentity() {
            Transaction movimiento = Transaction.reconstitute(ID, ACCOUNT, TransactionType.WITHDRAWAL,
                    MonetaryAmount.euros("50.00"), OCCURRED_ON, COUNTERPART, TransactionStatus.REJECTED);

            assertThat(movimiento.id()).isEqualTo(ID);
            assertThat(movimiento.accountId()).isEqualTo(ACCOUNT);
            assertThat(movimiento.type()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(movimiento.amount()).isEqualTo(MonetaryAmount.euros("50.00"));
            assertThat(movimiento.occurredOn()).isEqualTo(OCCURRED_ON);
            assertThat(movimiento.counterpart()).contains(COUNTERPART);
            assertThat(movimiento.isRejected()).isTrue();
            assertThat(movimiento).isEqualTo(Transaction.reconstitute(ID, ACCOUNT, TransactionType.WITHDRAWAL,
                    MonetaryAmount.euros("50.00"), OCCURRED_ON, null, TransactionStatus.REJECTED));
        }

        @Test
        @DisplayName("Un movimiento sin contrapartida se reconstituye sin ella")
        void aTransactionWithoutCounterpartIsReconstitutedWithoutOne() {
            Transaction movimiento = Transaction.reconstitute(ID, ACCOUNT, TransactionType.DEPOSIT,
                    MonetaryAmount.euros("10.00"), OCCURRED_ON, null, TransactionStatus.APPROVED);

            assertThat(movimiento.counterpart()).isEmpty();
            assertThat(movimiento.isApproved()).isTrue();
        }

        @Test
        @DisplayName("Los datos obligatorios de un movimiento no pueden faltar al reconstituirlo")
        void aTransactionCannotBeReconstitutedWithoutItsRequiredData() {
            MonetaryAmount amount = MonetaryAmount.euros("10.00");
            assertThatThrownBy(() -> Transaction.reconstitute(null, ACCOUNT, TransactionType.DEPOSIT, amount,
                    OCCURRED_ON, null, TransactionStatus.APPROVED)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Transaction.reconstitute(ID, null, TransactionType.DEPOSIT, amount,
                    OCCURRED_ON, null, TransactionStatus.APPROVED)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Transaction.reconstitute(ID, ACCOUNT, null, amount,
                    OCCURRED_ON, null, TransactionStatus.APPROVED)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Transaction.reconstitute(ID, ACCOUNT, TransactionType.DEPOSIT, null,
                    OCCURRED_ON, null, TransactionStatus.APPROVED)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Transaction.reconstitute(ID, ACCOUNT, TransactionType.DEPOSIT, amount,
                    null, null, TransactionStatus.APPROVED)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Transaction.reconstitute(ID, ACCOUNT, TransactionType.DEPOSIT, amount,
                    OCCURRED_ON, null, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Un movimiento no se reconstituye con un importe que no es positivo")
        void aTransactionIsNotReconstitutedWithANonPositiveAmount() {
            assertThatThrownBy(() -> Transaction.reconstitute(ID, ACCOUNT, TransactionType.DEPOSIT,
                    MonetaryAmount.euros("0.00"), OCCURRED_ON, null, TransactionStatus.APPROVED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El importe del movimiento debe ser positivo");
        }
    }
}
