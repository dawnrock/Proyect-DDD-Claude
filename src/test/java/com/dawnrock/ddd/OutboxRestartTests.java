package com.dawnrock.ddd;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import com.dawnrock.ddd.adapter.out.event.OutboxRelay;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.MoneyMovementCommands;
import com.dawnrock.ddd.application.DepositCommand;
import com.dawnrock.ddd.application.OpenAccountCommand;

/**
 * Un evento confirmado sobrevive a un reinicio (#165, ADR 0021): dos arranques sucesivos sobre la misma base, como en
 * {@code PersistenceRestartTests}. El primero guarda un ingreso y se para antes de que el relé lo entregue (el relé
 * programado se espacia a una hora); el segundo lo entrega.
 */
@ExtendWith(OutputCaptureExtension.class)
class OutboxRestartTests {

    private static final Caller ADMIN = Caller.administrator("admin");

    private final String database = "jdbc:h2:mem:outbox-restart-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(DddLabApplication.class)
                .web(WebApplicationType.SERVLET)
                .logStartupInfo(false)
                // Argumentos y no properties(): estas tienen la menor prioridad y ganaría la base aleatoria de los tests
                .run("--server.port=0", "--spring.datasource.url=" + database, "--app.events.outbox.relay-interval=1h");
    }

    private static int pendingEvents(ConfigurableApplicationContext context) {
        return context.getBean(JdbcTemplate.class).queryForObject(
                "select count(*) from domain_event_outbox where published_at is null", Integer.class);
    }

    @Test
    @DisplayName("Un ingreso confirmado antes de un reinicio llega al consumidor de auditoría tras el reinicio")
    void aDepositCommittedBeforeARestartReachesTheAuditConsumerAfterIt(CapturedOutput output) {
        UUID accountId;
        try (ConfigurableApplicationContext first = start()) {
            AccountOpeningCommands commands = first.getBean(AccountOpeningCommands.class);
            accountId = commands.openAccount(ADMIN, OpenAccountCommand.builder()
                    .holderName("Grace Hopper")
                    .holderTaxId("87654321X")
                    .holderEmail("grace@example.com")
                    .initialDeposit(new BigDecimal("100"))
                    .ownerUsername("grace")
                    .build()).id();
            first.getBean(MoneyMovementCommands.class).deposit(ADMIN, DepositCommand.of(accountId, new BigDecimal("25")));

            assertThat(pendingEvents(first)).as("el primer arranque no ha entregado nada").isEqualTo(3);
        }
        assertThat(output).doesNotContain("Auditoría · MoneyDeposited · cuenta " + accountId);

        try (ConfigurableApplicationContext second = start()) {
            second.getBean(OutboxRelay.class).relayPending();

            assertThat(output).contains("Auditoría · MoneyDeposited · cuenta " + accountId);
            assertThat(pendingEvents(second)).isZero();
        }
    }
}
