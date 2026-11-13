package com.pronoide.ddd.application.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.pronoide.ddd.domain.model.CustomerSegment;
import com.pronoide.ddd.domain.model.CustomerSegmentChanged;
import com.pronoide.ddd.domain.model.MoneyDeposited;
import com.pronoide.ddd.domain.model.MoneyWithdrawn;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.WithdrawalRejected;

@ExtendWith(OutputCaptureExtension.class)
class CustomerNotificationListenerTests {

    private final CustomerNotificationListener listener = new CustomerNotificationListener();
    private final UUID accountId = UUID.randomUUID();

    @Test
    @DisplayName("Avisa al cliente de un ingreso con su importe")
    void avisa_al_cliente_de_un_ingreso(CapturedOutput output) {
        listener.on(new MoneyDeposited(accountId, UUID.randomUUID(), MonetaryAmount.euros("25.00"), Instant.now()));

        assertThat(output).contains("Aviso al cliente", accountId.toString(), "ingresado", "25.00 EUR");
    }

    @Test
    @DisplayName("Avisa al cliente de una retirada con su importe")
    void avisa_al_cliente_de_una_retirada(CapturedOutput output) {
        listener.on(new MoneyWithdrawn(accountId, UUID.randomUUID(), MonetaryAmount.euros("200.00"), Instant.now()));

        assertThat(output).contains("Aviso al cliente", accountId.toString(), "retirado", "200.00 EUR");
    }

    @Test
    @DisplayName("Avisa al cliente de que se ha rechazado una retirada")
    void avisa_al_cliente_de_una_retirada_rechazada(CapturedOutput output) {
        listener.on(new WithdrawalRejected(accountId, UUID.randomUUID(), MonetaryAmount.euros("600.00"), Instant.now()));

        assertThat(output).contains("Aviso al cliente", accountId.toString(), "rechazado", "600.00 EUR");
    }

    @Test
    @DisplayName("Avisa al cliente de que pasa a alto patrimonio")
    void avisa_al_cliente_de_que_pasa_a_alto_patrimonio(CapturedOutput output) {
        listener.on(new CustomerSegmentChanged(accountId, "Ada Lovelace", CustomerSegment.HIGH_NET_WORTH, Instant.now()));

        assertThat(output).contains("Aviso al cliente", accountId.toString(), "Ada Lovelace", "alto patrimonio");
    }

    @Test
    @DisplayName("Avisa al cliente de que vuelve a minorista")
    void avisa_al_cliente_de_que_vuelve_a_minorista(CapturedOutput output) {
        listener.on(new CustomerSegmentChanged(accountId, "Ada Lovelace", CustomerSegment.RETAIL, Instant.now()));

        assertThat(output).contains("Aviso al cliente", accountId.toString(), "Ada Lovelace", "minorista");
    }
}
