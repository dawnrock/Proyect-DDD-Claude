package com.dawnrock.ddd.application.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.dawnrock.ddd.domain.model.CustomerSegment;
import com.dawnrock.ddd.domain.model.CustomerSegmentChanged;
import com.dawnrock.ddd.domain.model.MoneyDeposited;
import com.dawnrock.ddd.domain.model.MoneyWithdrawn;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.domain.model.WithdrawalRejected;

@ExtendWith(OutputCaptureExtension.class)
class CustomerNotificationListenerTests {

    private final CustomerNotificationListener listener = new CustomerNotificationListener();
    private final UUID accountId = UUID.randomUUID();

    @Test
    @DisplayName("Avisa al cliente de un ingreso con su importe")
    void notifiesTheCustomerOfADeposit(CapturedOutput output) {
        listener.on(new MoneyDeposited(accountId, UUID.randomUUID(), MonetaryAmount.euros("25.00"), Instant.now()));

        assertThat(output).contains("Aviso al cliente", accountId.toString(), "ingresado", "25.00 EUR");
    }

    @Test
    @DisplayName("Avisa al cliente de una retirada con su importe")
    void notifiesTheCustomerOfAWithdrawal(CapturedOutput output) {
        listener.on(new MoneyWithdrawn(accountId, UUID.randomUUID(), MonetaryAmount.euros("200.00"), Instant.now()));

        assertThat(output).contains("Aviso al cliente", accountId.toString(), "retirado", "200.00 EUR");
    }

    @Test
    @DisplayName("Avisa al cliente de que se ha rechazado una retirada")
    void notifiesTheCustomerOfARejectedWithdrawal(CapturedOutput output) {
        listener.on(new WithdrawalRejected(accountId, UUID.randomUUID(), MonetaryAmount.euros("600.00"), Instant.now()));

        assertThat(output).contains("Aviso al cliente", accountId.toString(), "rechazado", "600.00 EUR");
    }

    @Test
    @DisplayName("Avisa al cliente de que pasa a alto patrimonio")
    void notifiesTheCustomerOfThePromotionToHighNetWorth(CapturedOutput output) {
        listener.on(new CustomerSegmentChanged(accountId, "Ada Lovelace", CustomerSegment.HIGH_NET_WORTH, Instant.now()));

        assertThat(output).contains("Aviso al cliente", accountId.toString(), "Ada Lovelace", "alto patrimonio");
    }

    @Test
    @DisplayName("Avisa al cliente de que vuelve a minorista")
    void notifiesTheCustomerOfTheDemotionToRetail(CapturedOutput output) {
        listener.on(new CustomerSegmentChanged(accountId, "Ada Lovelace", CustomerSegment.RETAIL, Instant.now()));

        assertThat(output).contains("Aviso al cliente", accountId.toString(), "Ada Lovelace", "minorista");
    }
}
