package com.pronoide.ddd.application;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Abre cuentas de demostración al arrancar, para poder probar la aplicación sin preparar datos. Usa el caso de uso
 * {@code openAccount}, nunca el repositorio, así que los datos cumplen las mismas reglas que los reales. Solo se activa
 * con el perfil {@code demo}, que {@code ./mvnw spring-boot:run} activa por defecto; los tests no lo cargan.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataLoader implements ApplicationRunner {

    private static final List<OpenAccountCommand> DEMO_ACCOUNTS = List.of(
            account("ES9121000418450200051332", "Ada Lovelace", "RETAIL", "1000.00"),
            account("ES7921000813610123456789", "Grace Hopper", "HIGH_NET_WORTH", "5000.00"),
            account("ES6000491500051234567892", "Alan Turing", "RETAIL", "250.00"));

    private final CheckingAccountService service;

    @Override
    public void run(ApplicationArguments args) {
        DEMO_ACCOUNTS.forEach(command -> {
            AccountSummary account = service.openAccount(command);
            log.info("Cuenta de demostración abierta: {} de {} con {} {}", account.accountNumber(),
                    account.holderName(), account.balance(), account.currency());
        });
    }

    private static OpenAccountCommand account(String accountNumber, String holderName, String segment,
                                              String initialDeposit) {
        return OpenAccountCommand.builder()
                .accountNumber(accountNumber)
                .holderName(holderName)
                .segment(segment)
                .initialDeposit(new BigDecimal(initialDeposit))
                .build();
    }
}
