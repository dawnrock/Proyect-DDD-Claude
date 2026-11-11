package com.pronoide.ddd.adapter.in.cli;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.pronoide.ddd.adapter.in.SegmentLabels;
import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.CallerDef;
import com.pronoide.ddd.application.CheckingAccountCommands;
import com.pronoide.ddd.application.DepositCommand;
import com.pronoide.ddd.application.OpenAccountCommand;
import com.pronoide.ddd.application.TransactionSummary;
import com.pronoide.ddd.application.TransferCommand;
import com.pronoide.ddd.application.TransferSummary;
import com.pronoide.ddd.application.WithdrawCommand;

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

    /**
     * Abre las cuentas de demostración y, con los IBAN generados que devuelven sus resúmenes, ingresa en la de Ada,
     * retira de la de Alan una vez con saldo y otra sin él, y transfiere de Grace a Ada por el IBAN de Ada. Opera como
     * {@code CallerDef.system()}, un administrador.
     */
    @Override
    public void run(ApplicationArguments args) {
        List<AccountSummary> opened = DEMO_ACCOUNTS.stream().map(this::open).toList();
        log.info("[demo] {} cuentas de demostración abiertas", opened.size());
        AccountSummary ada = opened.get(0);
        AccountSummary grace = opened.get(1);
        AccountSummary alan = opened.get(2);

        deposit(ada, "200.00");
        withdraw(alan, "50.00");
        withdraw(alan, "1000.00");
        transfer(grace, ada, "300.00");
    }

    private AccountSummary open(OpenAccountCommand command) {
        AccountSummary account = commands.openAccount(CallerDef.system(), command);
        log.info("[demo] Abrir cuenta · {} · {} · {} · {} {} · propietario {}", account.accountNumber(),
                account.holderName(), SegmentLabels.of(account.segment()), account.balance(), account.currency(),
                command.ownerUsername());
        return account;
    }

    private void deposit(AccountSummary account, String amount) {
        TransactionSummary deposit = commands.deposit(CallerDef.system(),
                DepositCommand.builder().accountId(account.id()).amount(new BigDecimal(amount)).build());
        log.info("[demo] Ingresar · {} · {} {} · {}", account.accountNumber(), deposit.amount(), deposit.currency(),
                deposit.approved() ? "aprobado" : "rechazado");
    }

    private void withdraw(AccountSummary account, String amount) {
        TransactionSummary withdrawal = commands.withdraw(CallerDef.system(),
                WithdrawCommand.builder().accountId(account.id()).amount(new BigDecimal(amount)).build());
        log.info("[demo] Retirar · {} · {} {} · {}", account.accountNumber(), withdrawal.amount(),
                withdrawal.currency(), withdrawal.approved() ? "aprobada" : "rechazada");
    }

    private void transfer(AccountSummary source, AccountSummary target, String amount) {
        TransferSummary transfer = commands.transfer(CallerDef.system(), TransferCommand.builder()
                .sourceAccountId(source.id())
                .targetAccountNumber(target.accountNumber())
                .amount(new BigDecimal(amount))
                .build());
        log.info("[demo] Transferir · {} → {} · {} {} · {}", transfer.sourceAccountNumber(),
                transfer.targetAccountNumber(), transfer.amount(), transfer.currency(),
                transfer.approved() ? "aprobada" : "rechazada");
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
