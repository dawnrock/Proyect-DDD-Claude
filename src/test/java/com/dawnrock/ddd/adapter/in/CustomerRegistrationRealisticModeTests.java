package com.dawnrock.ddd.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.application.SignUpCommand;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.support.ApplicationServicesFixture;

/**
 * Sin autenticación local ({@code app.auth.local=false}, #199) solo hay alta por Auth0: el alta con contraseña es una
 * segunda barrera por si algún adaptador la llamara.
 */
class CustomerRegistrationRealisticModeTests {

    @Test
    @DisplayName("Sin autenticación local el alta con contraseña se rechaza sin abrir nada ni crear el usuario")
    void withoutLocalAuthenticationThePasswordSignUpIsRefused() {
        CheckingAccountRepository accounts = PersistenceTestAdapters.accounts();
        InMemoryUserDetailsManager users = new InMemoryUserDetailsManager();
        ApplicationServicesFixture fixture = ApplicationServicesFixture.builder().accounts(accounts).build();
        CustomerRegistration registration = new CustomerRegistration(fixture.accountOpeningService(),
                fixture.queryService(), users, new SecuritySupport().passwordEncoder(),
                new SignUpLimit(new SignUpLimitProperties(1_000, 1_000, Duration.ofHours(1), 1_000), Clock.systemUTC()),
                new InMemoryExternalIdentities(users::userExists), Clock.systemUTC(), new AuthProperties(false));
        SignUpCommand command = SignUpCommand.of("Margaret Hamilton", "00000000T", "margaret@example.com", "margaret",
                new BigDecimal("100.00"));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> registration.register(command, "demo-1234", "203.0.113.5"))
                .withMessageContaining("Auth0");
        assertThat(accounts.findAll()).isEmpty();
        assertThat(users.userExists("margaret")).isFalse();
    }
}
