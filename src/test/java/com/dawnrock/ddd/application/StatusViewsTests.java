package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatusViewsTests {

    @ParameterizedTest(name = "{0}: aprobado={1}, rechazado={2}")
    @CsvSource({"APPROVED, true, false", "REJECTED, false, true", "PENDING, false, false"})
    @DisplayName("El movimiento dice si está aprobado o rechazado según su estado")
    void theTransactionSaysWhetherItIsApprovedOrRejected(String status, boolean approved, boolean rejected) {
        TransactionSummary transaction = TransactionSummary.of(UUID.randomUUID(), "WITHDRAWAL", BigDecimal.TEN, "EUR",
                Instant.now(), status);

        assertThat(transaction.approved()).isEqualTo(approved);
        assertThat(transaction.rejected()).isEqualTo(rejected);
    }

    @ParameterizedTest(name = "{0}: aprobada={1}, rechazada={2}")
    @CsvSource({"APPROVED, true, false", "REJECTED, false, true"})
    @DisplayName("La transferencia dice si está aprobada o rechazada según su estado")
    void theTransferSaysWhetherItIsApprovedOrRejected(String status, boolean approved, boolean rejected) {
        TransferSummary transfer = new TransferSummary(UUID.randomUUID(), "ES7921000418450200051332", UUID.randomUUID(),
                "ES7921000418450200051333", BigDecimal.TEN, "EUR", Instant.now(), status);

        assertThat(transfer.approved()).isEqualTo(approved);
        assertThat(transfer.rejected()).isEqualTo(rejected);
    }
}
