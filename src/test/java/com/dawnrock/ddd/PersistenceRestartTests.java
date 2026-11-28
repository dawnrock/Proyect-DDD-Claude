package com.dawnrock.ddd;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;

import com.dawnrock.ddd.adapter.in.CustomerRegistration;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.CheckingAccountQueries;
import com.dawnrock.ddd.application.SignUpCommand;

/**
 * Los datos sobreviven a un reinicio (#150, ADR 0019): dos arranques sucesivos de la aplicación con el perfil
 * {@code demo} sobre la misma base. Es el equivalente local de reiniciar el servicio en Render con Neon.
 */
class PersistenceRestartTests {

    private final String database = "jdbc:h2:mem:restart-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(DddLabApplication.class)
                .profiles("demo")
                .web(WebApplicationType.SERVLET)
                .logStartupInfo(false)
                // Argumentos y no properties(): estas tienen la menor prioridad y ganaría la base aleatoria de los tests
                .run("--server.port=0", "--spring.datasource.url=" + database);
    }

    private static List<AccountSummary> accounts(ConfigurableApplicationContext context) {
        return context.getBean(CheckingAccountQueries.class).listAccounts(Caller.system());
    }

    @Test
    @DisplayName("Tras un reinicio siguen el cliente dado de alta, su cuenta y su usuario, y la demo no se duplica")
    void afterARestartTheCustomerTheirAccountAndTheirUserRemain() {
        AccountSummary signedUp;
        try (ConfigurableApplicationContext first = start()) {
            assertThat(accounts(first)).hasSize(3);
            signedUp = first.getBean(CustomerRegistration.class).register(SignUpCommand.of("Margaret Hamilton",
                    "00000000T", "margaret@example.com", "margaret", new BigDecimal("100.00")), "apolo-once", "203.0.113.7");
        }

        try (ConfigurableApplicationContext second = start()) {
            assertThat(accounts(second)).hasSize(4)
                    .extracting(AccountSummary::accountNumber).contains(signedUp.accountNumber());
            assertThat(second.getBean(CheckingAccountQueries.class)
                    .listAccounts(Caller.customer("margaret")))
                    .extracting(AccountSummary::id).containsExactly(signedUp.id());
            assertThat(second.getBean(PasswordEncoder.class).matches("apolo-once",
                    second.getBean(UserDetailsManager.class).loadUserByUsername("margaret").getPassword())).isTrue();
        }
    }
}
