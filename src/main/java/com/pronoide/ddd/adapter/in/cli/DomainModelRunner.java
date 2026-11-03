package com.pronoide.ddd.adapter.in.cli;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.CallerDef;
import com.pronoide.ddd.application.CheckingAccountCommands;
import com.pronoide.ddd.application.OpenAccountCommand;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de entrada de línea de comandos (#19): al arrancar con el perfil {@code demo}, que
 * {@code ./mvnw spring-boot:run} activa por defecto, abre las cuentas de demostración para poder probar la aplicación
 * sin preparar datos. Como la API REST y la web, solo usa el puerto de entrada {@link CheckingAccountCommands}: los
 * datos cumplen las mismas reglas que los reales (el IBAN lo genera la numeración y el segmento lo decide la agencia
 * tributaria a partir del NIF; Grace Hopper es de alto patrimonio por sus ingresos verificados). Los tests no lo
 * cargan.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DomainModelRunner implements ApplicationRunner {

    private static final List<OpenAccountCommand> DEMO_ACCOUNTS = List.of(
            account("Ada Lovelace", "12345678Z", "1000.00", "ada"),
            account("Grace Hopper", "87654321X", "5000.00", "grace"),
            account("Alan Turing", "11111111H", "250.00", "alan"));

    private final CheckingAccountCommands commands;

    @Override
    public void run(ApplicationArguments args) {
        DEMO_ACCOUNTS.forEach(command -> {
            AccountSummary account = commands.openAccount(CallerDef.system(), command);
            log.info("Cuenta de demostración abierta: {} de {} con {} {}", account.accountNumber(),
                    account.holderName(), account.balance(), account.currency());
        });
    }

    private static OpenAccountCommand account(String holderName, String holderTaxId, String initialDeposit,
                                              String owner) {
        return OpenAccountCommand.builder()
                .holderName(holderName)
                .holderTaxId(holderTaxId)
                .initialDeposit(new BigDecimal(initialDeposit))
                .ownerUsername(owner)
                .build();
    }
}
