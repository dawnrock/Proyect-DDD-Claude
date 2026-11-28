package com.dawnrock.ddd.adapter.in.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.application.AccountDetails;
import com.dawnrock.ddd.application.AccountOwnershipRepository;
import com.dawnrock.ddd.application.AccountSummary;
import com.dawnrock.ddd.application.Caller;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.CheckingAccountQueryService;
import com.dawnrock.ddd.application.MoneyMovementCommands;
import com.dawnrock.ddd.application.TransactionSummary;
import com.dawnrock.ddd.domain.model.CheckingAccountFactory;
import com.dawnrock.ddd.support.ApplicationServicesFixture;

@ExtendWith(OutputCaptureExtension.class)
class DomainModelRunnerTests {

    private final AccountOwnershipRepository ownership = PersistenceTestAdapters.ownership();
    private final ApplicationServicesFixture services = ApplicationServicesFixture.builder().ownership(ownership).build();
    private final CheckingAccountQueryService service = services.queryService();
    /** El runner solo recibe los puertos de entrada; el servicio concreto se usa aquí también para consultar. */
    private final AccountOpeningCommands commands = services.accountOpeningService();
    private final MoneyMovementCommands movements = services.moneyMovementService();

    @Test
    @DisplayName("Al arrancar abre tres cuentas de demostración con el caso de uso, una de ellas de alto patrimonio")
    void onStartupItOpensThreeDemoAccountsWithTheUseCase() {
        new DomainModelRunner(commands, movements, service).run(new DefaultApplicationArguments());

        List<AccountSummary> accounts = service.listAccounts(Caller.system());
        assertThat(accounts).hasSize(3);
        assertThat(accounts)
                .map(account -> service.findAccount(Caller.system(), account.id()).segment())
                .containsExactlyInAnyOrder("RETAIL", "RETAIL", "HIGH_NET_WORTH");
    }

    @Test
    @DisplayName("Cada cuenta de demostración queda a nombre de su usuario: ada, grace y alan")
    void eachDemoAccountBelongsToItsUser() {
        new DomainModelRunner(commands, movements, service).run(new DefaultApplicationArguments());

        assertThat(service.listAccounts(Caller.system()))
                .extracting(AccountSummary::holderName, account -> ownership.ownerOf(account.id()).orElse(null))
                .containsExactlyInAnyOrder(
                        tuple("Ada Lovelace", "ada"),
                        tuple("Grace Hopper", "grace"),
                        tuple("Alan Turing", "alan"));
    }

    @Test
    @DisplayName("El segmento lo decide la agencia tributaria: sin ingresos verificados las tres cuentas son minoristas")
    void theTaxAgencyDecidesTheSegment() {
        ApplicationServicesFixture withoutIncomesServices = ApplicationServicesFixture.builder()
                .ownership(ownership)
                .factory(new CheckingAccountFactory(PersistenceTestAdapters.accountNumbers(), taxId -> Optional.empty()))
                .build();
        CheckingAccountQueryService withoutIncomes = withoutIncomesServices.queryService();

        new DomainModelRunner(withoutIncomesServices.accountOpeningService(),
                withoutIncomesServices.moneyMovementService(), withoutIncomes).run(new DefaultApplicationArguments());

        assertThat(withoutIncomes.listAccounts(Caller.system()))
                .extracting(AccountSummary::segment).containsOnly("RETAIL");
    }

    @Test
    @DisplayName("Las cuentas de demostración llevan un IBAN generado y distinto")
    void theDemoAccountsCarryAGeneratedIban() {
        new DomainModelRunner(commands, movements, service).run(new DefaultApplicationArguments());

        assertThat(service.listAccounts(Caller.system())).extracting(AccountSummary::accountNumber)
                .doesNotHaveDuplicates().allMatch(number -> number.startsWith("ES") && number.startsWith("99990001", 4));
    }

    @Test
    @DisplayName("Deja una traza legible de cada apertura: IBAN generado, titular, segmento, saldo y propietario")
    void itLogsAReadableLineForEachOpening(CapturedOutput output) {
        new DomainModelRunner(commands, movements, service).run(new DefaultApplicationArguments());

        assertThat(output)
                .containsPattern("\\[demo\\] Abrir cuenta · ES\\d{22} · Ada Lovelace · Minorista · 1000\\.00 EUR · propietario ada")
                .containsPattern("\\[demo\\] Abrir cuenta · ES\\d{22} · Grace Hopper · Alto patrimonio · 5000\\.00 EUR · propietario grace")
                .containsPattern("\\[demo\\] Abrir cuenta · ES\\d{22} · Alan Turing · Minorista · 250\\.00 EUR · propietario alan");
    }

    @Test
    @DisplayName("Termina con un resumen de cuántas cuentas de demostración ha abierto")
    void itEndsWithASummaryOfTheOpenedAccounts(CapturedOutput output) {
        new DomainModelRunner(commands, movements, service).run(new DefaultApplicationArguments());

        assertThat(output).contains("[demo] 3 cuentas de demostración abiertas");
    }

    /** Detalle de la cuenta de demostración de un titular, con sus movimientos. */
    private AccountDetails detailsOf(String holderName) {
        return service.listAccounts(Caller.system()).stream()
                .filter(account -> account.holderName().equals(holderName))
                .map(account -> service.findAccount(Caller.system(), account.id()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("Tras abrir las cuentas, la demo ingresa en la de Ada y retira de la de Alan una vez con éxito y otra sin saldo")
    void afterOpeningTheAccountsTheDemoDepositsAndWithdraws() {
        new DomainModelRunner(commands, movements, service).run(new DefaultApplicationArguments());

        assertThat(detailsOf("Ada Lovelace").transactions())
                .extracting(TransactionSummary::type, TransactionSummary::status, t -> t.amount().toPlainString())
                .contains(tuple("DEPOSIT", "APPROVED", "200.00"));
        assertThat(detailsOf("Alan Turing").transactions())
                .extracting(TransactionSummary::type, TransactionSummary::status, t -> t.amount().toPlainString())
                .containsExactly(
                        tuple("DEPOSIT", "APPROVED", "250.00"),
                        tuple("WITHDRAWAL", "APPROVED", "50.00"),
                        tuple("WITHDRAWAL", "REJECTED", "1000.00"));
        assertThat(detailsOf("Alan Turing").balance()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("Deja una traza de cada ingreso y retirada con el IBAN, el importe y su resultado")
    void itLogsEachDepositAndWithdrawal(CapturedOutput output) {
        new DomainModelRunner(commands, movements, service).run(new DefaultApplicationArguments());

        assertThat(output)
                .containsPattern("\\[demo\\] Ingresar · ES\\d{22} · 200\\.00 EUR · aprobado")
                .containsPattern("\\[demo\\] Retirar · ES\\d{22} · 50\\.00 EUR · aprobada")
                .containsPattern("\\[demo\\] Retirar · ES\\d{22} · 1000\\.00 EUR · rechazada");
    }

    @Test
    @DisplayName("La demo transfiere de Grace a Ada usando el IBAN generado de Ada")
    void theDemoTransfersFromGraceToAdaWithTheGeneratedIban() {
        new DomainModelRunner(commands, movements, service).run(new DefaultApplicationArguments());

        assertThat(detailsOf("Grace Hopper").balance()).isEqualByComparingTo("4700.00");
        assertThat(detailsOf("Ada Lovelace").balance()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("Deja una traza de la transferencia con los dos IBAN, el importe y su resultado")
    void itLogsTheTransfer(CapturedOutput output) {
        new DomainModelRunner(commands, movements, service).run(new DefaultApplicationArguments());

        String adaIban = detailsOf("Ada Lovelace").accountNumber();
        String graceIban = detailsOf("Grace Hopper").accountNumber();
        assertThat(output).contains(
                "[demo] Transferir · %s → %s · 300.00 EUR · aprobada".formatted(graceIban, adaIban));
    }

    @Test
    @DisplayName("Si ya hay cuentas, por ejemplo tras un reinicio con la base persistente, no vuelve a cargar la demo (#150)")
    void whenThereAreAlreadyAccountsTheDemoIsNotLoadedAgain(CapturedOutput output) {
        new DomainModelRunner(commands, movements, service).run(new DefaultApplicationArguments());

        new DomainModelRunner(commands, movements, service).run(new DefaultApplicationArguments());

        assertThat(service.listAccounts(Caller.system())).hasSize(3);
        assertThat(detailsOf("Ada Lovelace").balance()).isEqualByComparingTo("1500.00");
        assertThat(detailsOf("Grace Hopper").balance()).isEqualByComparingTo("4700.00");
        assertThat(detailsOf("Alan Turing").balance()).isEqualByComparingTo("200.00");
        assertThat(output).contains("[demo] datos ya cargados");
    }
}
