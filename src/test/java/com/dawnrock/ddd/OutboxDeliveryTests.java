package com.dawnrock.ddd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

import com.dawnrock.ddd.adapter.out.event.OutboxRelay;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.MoneyMovementCommands;
import com.dawnrock.ddd.application.DepositCommand;
import com.dawnrock.ddd.application.OpenAccountCommand;
import com.dawnrock.ddd.domain.model.MoneyDeposited;

/**
 * Un consumidor de eventos que falla no rompe el caso de uso (#165, ADR 0021): con el outbox, el caso de uso solo guarda
 * los eventos; el relé los entrega después y cuenta los fallos. El relé programado se espacia a una hora para que solo
 * lo ejecute el test: si corriera cada 2 s, podría sumar un intento entre la llamada y la comprobación.
 */
@SpringBootTest(properties = "app.events.outbox.relay-interval=1h")
@Import(OutboxDeliveryTests.FailingDepositConsumer.class)
class OutboxDeliveryTests {

    private static final Caller ADMIN = Caller.administrator("admin");

    @Autowired
    private AccountOpeningCommands commands;

    @Autowired
    private MoneyMovementCommands movements;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Un consumidor que falla no hace fallar el ingreso: su evento queda pendiente con un intento y los demás se entregan")
    void aFailingConsumerDoesNotBreakTheDepositAndItsEventStaysPending() {
        UUID accountId = commands.openAccount(ADMIN, OpenAccountCommand.of("Ada Lovelace", "12345678Z",
                "ada@example.com", new BigDecimal("100"), "ada")).id();

        assertThatNoException().isThrownBy(() -> movements.deposit(ADMIN,
                DepositCommand.of(accountId, new BigDecimal("25"))));

        relay.relayPending();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select event_type, published_at, attempts from domain_event_outbox where account_id = ? order by id",
                accountId);
        assertThat(rows).extracting(row -> row.get("event_type"))
                .containsExactly("CheckingAccountOpened", "MoneyDeposited", "MoneyDeposited");
        assertThat(rows.get(0).get("published_at")).as("cuenta abierta entregada").isNotNull();
        assertThat(rows.subList(1, rows.size())).allSatisfy(row -> {
            assertThat(row.get("published_at")).as("ingreso pendiente").isNull();
            assertThat(row.get("attempts")).isEqualTo(1);
        });
    }

    /** Consumidor de prueba que siempre falla con los ingresos, como un servicio de correo caído. */
    @TestConfiguration
    static class FailingDepositConsumer {

        @EventListener
        void on(MoneyDeposited event) {
            throw new IllegalStateException("el servicio de avisos no está disponible");
        }
    }
}
