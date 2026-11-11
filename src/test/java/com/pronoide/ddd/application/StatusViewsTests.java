package com.pronoide.ddd.application;

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
    void el_movimiento_dice_si_esta_aprobado_o_rechazado(String status, boolean approved, boolean rejected) {
        TransactionSummary transaction = TransactionSummary.builder().id(UUID.randomUUID()).type("WITHDRAWAL")
                .amount(BigDecimal.TEN).currency("EUR").occurredOn(Instant.now()).status(status).build();

        assertThat(transaction.approved()).isEqualTo(approved);
        assertThat(transaction.rejected()).isEqualTo(rejected);
    }

    @ParameterizedTest(name = "{0}: aprobada={1}, rechazada={2}")
    @CsvSource({"APPROVED, true, false", "REJECTED, false, true"})
    @DisplayName("La transferencia dice si está aprobada o rechazada según su estado")
    void la_transferencia_dice_si_esta_aprobada_o_rechazada(String status, boolean approved, boolean rejected) {
        TransferSummary transfer = TransferSummary.builder().sourceAccountId(UUID.randomUUID())
                .sourceAccountNumber("ES7921000418450200051332").targetAccountId(UUID.randomUUID())
                .targetAccountNumber("ES7921000418450200051333").amount(BigDecimal.TEN).currency("EUR")
                .occurredOn(Instant.now()).status(status).build();

        assertThat(transfer.approved()).isEqualTo(approved);
        assertThat(transfer.rejected()).isEqualTo(rejected);
    }
}
