package com.dawnrock.ddd.application;

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
 * {@code openAccount}, nunca el repositorio, así que los datos cumplen las mismas reglas que los reales. Los NIF son los
 * de la tabla de la agencia tributaria simulada ({@code TaxAgencyClient}): Grace Hopper es de alto patrimonio por sus
 * ingresos verificados, aunque su depósito no llegue al patrimonio mínimo. Cada titular queda registrado como cliente
 * de Mon-A Bank (con su correo y su usuario) y cada cuenta recibe un IBAN del banco: siempre los mismos, porque la
 * numeración empieza en {@code 0000000001} con cada arranque. Solo se activa con el perfil {@code demo},
 * que {@code ./mvnw spring-boot:run} activa por defecto; los tests no lo cargan.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataLoader implements ApplicationRunner {

    private static final List<OpenAccountCommand> DEMO_ACCOUNTS = List.of(
            account("Ada Lovelace", "12345678Z", "ada@example.com", "1000.00", "ada"),
            account("Grace Hopper", "87654321X", "grace@example.com", "5000.00", "grace"),
            account("Alan Turing", "11111111H", "alan@example.com", "250.00", "alan"));

    private final CheckingAccountService service;

    @Override
    public void run(ApplicationArguments args) {
        DEMO_ACCOUNTS.forEach(command -> {
            AccountSummary account = service.openAccount(CallerDef.system(), command);
            log.info("Cuenta de demostración abierta: {} de {} con {} {}", account.accountNumber(),
                    account.holderName(), account.balance(), account.currency());
        });
    }

    private static OpenAccountCommand account(String holderName, String holderTaxId, String holderEmail,
                                              String initialDeposit, String owner) {
        return OpenAccountCommand.builder()
                .holderName(holderName)
                .holderTaxId(holderTaxId)
                .holderEmail(holderEmail)
                .initialDeposit(new BigDecimal(initialDeposit))
                .ownerUsername(owner)
                .build();
    }
}
