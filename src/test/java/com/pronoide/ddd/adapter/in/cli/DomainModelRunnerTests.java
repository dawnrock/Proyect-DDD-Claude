package com.pronoide.ddd.adapter.in.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.pronoide.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.pronoide.ddd.application.AccountDetails;
import com.pronoide.ddd.application.AccountOwnershipRepository;
import com.pronoide.ddd.application.AccountSummary;
import com.pronoide.ddd.application.CallerDef;
import com.pronoide.ddd.application.CheckingAccountCommands;
import com.pronoide.ddd.application.CheckingAccountService;
import com.pronoide.ddd.application.TransactionSummary;
import com.pronoide.ddd.adapter.out.taxagency.TaxAgencyTestAdapters;
import com.pronoide.ddd.domain.model.DomainEventPublisher;

@ExtendWith(OutputCaptureExtension.class)
class DomainModelRunnerTests {

    private final AccountOwnershipRepository ownership = PersistenceTestAdapters.ownership();
    private final CheckingAccountService service =
            new CheckingAccountService(PersistenceTestAdapters.accounts(), ownership, mock(DomainEventPublisher.class),
                    PersistenceTestAdapters.accountNumbers(), TaxAgencyTestAdapters.incomeVerifier());
    /** El runner solo recibe el puerto de commands; el servicio concreto se usa aquí solo para consultar. */
    private final CheckingAccountCommands commands = service;

    @Test
    @DisplayName("Al arrancar abre tres cuentas de demostración con el caso de uso, una de ellas de alto patrimonio")
    void al_arrancar_abre_tres_cuentas_de_demostracion_con_el_caso_de_uso() {
        new DomainModelRunner(commands).run(new DefaultApplicationArguments());

        List<AccountSummary> accounts = service.listAccounts(CallerDef.system());
        assertThat(accounts).hasSize(3);
        assertThat(accounts)
                .map(account -> service.findAccount(CallerDef.system(), account.id()).segment())
                .containsExactlyInAnyOrder("RETAIL", "RETAIL", "HIGH_NET_WORTH");
    }

    @Test
    @DisplayName("Cada cuenta de demostración queda a nombre de su usuario: ada, grace y alan")
    void cada_cuenta_de_demostracion_queda_a_nombre_de_su_usuario() {
        new DomainModelRunner(commands).run(new DefaultApplicationArguments());

        assertThat(service.listAccounts(CallerDef.system()))
                .extracting(AccountSummary::holderName, account -> ownership.ownerOf(account.id()).orElse(null))
                .containsExactlyInAnyOrder(
                        tuple("Ada Lovelace", "ada"),
                        tuple("Grace Hopper", "grace"),
                        tuple("Alan Turing", "alan"));
    }

    @Test
    @DisplayName("El segmento lo decide la agencia tributaria: sin ingresos verificados las tres cuentas son minoristas")
    void el_segmento_lo_decide_la_agencia_tributaria() {
        CheckingAccountService withoutIncomes = new CheckingAccountService(PersistenceTestAdapters.accounts(), ownership,
                mock(DomainEventPublisher.class), PersistenceTestAdapters.accountNumbers(), taxId -> Optional.empty());

        new DomainModelRunner(withoutIncomes).run(new DefaultApplicationArguments());

        assertThat(withoutIncomes.listAccounts(CallerDef.system()))
                .extracting(AccountSummary::segment).containsOnly("RETAIL");
    }

    @Test
    @DisplayName("Las cuentas de demostración llevan un IBAN generado y distinto")
    void las_cuentas_de_demostracion_llevan_un_iban_generado() {
        new DomainModelRunner(commands).run(new DefaultApplicationArguments());

        assertThat(service.listAccounts(CallerDef.system())).extracting(AccountSummary::accountNumber)
                .doesNotHaveDuplicates().allMatch(number -> number.startsWith("ES") && number.startsWith("2100", 4));
    }

    @Test
    @DisplayName("Deja una traza legible de cada apertura: IBAN generado, titular, segmento, saldo y propietario")
    void deja_una_traza_legible_de_cada_apertura(CapturedOutput output) {
        new DomainModelRunner(commands).run(new DefaultApplicationArguments());

        assertThat(output)
                .containsPattern("\\[demo\\] Abrir cuenta · ES\\d{22} · Ada Lovelace · Minorista · 1000\\.00 EUR · propietario ada")
                .containsPattern("\\[demo\\] Abrir cuenta · ES\\d{22} · Grace Hopper · Alto patrimonio · 5000\\.00 EUR · propietario grace")
                .containsPattern("\\[demo\\] Abrir cuenta · ES\\d{22} · Alan Turing · Minorista · 250\\.00 EUR · propietario alan");
    }

    @Test
    @DisplayName("Termina con un resumen de cuántas cuentas de demostración ha abierto")
    void termina_con_un_resumen_de_las_cuentas_abiertas(CapturedOutput output) {
        new DomainModelRunner(commands).run(new DefaultApplicationArguments());

        assertThat(output).contains("[demo] 3 cuentas de demostración abiertas");
    }

    /** Detalle de la cuenta de demostración de un titular, con sus movimientos. */
    private AccountDetails detailsOf(String holderName) {
        return service.listAccounts(CallerDef.system()).stream()
                .filter(account -> account.holderName().equals(holderName))
                .map(account -> service.findAccount(CallerDef.system(), account.id()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("Tras abrir las cuentas, la demo ingresa en la de Ada y retira de la de Alan una vez con éxito y otra sin saldo")
    void tras_abrir_las_cuentas_la_demo_ingresa_y_retira() {
        new DomainModelRunner(commands).run(new DefaultApplicationArguments());

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
    void deja_una_traza_de_cada_ingreso_y_retirada(CapturedOutput output) {
        new DomainModelRunner(commands).run(new DefaultApplicationArguments());

        assertThat(output)
                .containsPattern("\\[demo\\] Ingresar · ES\\d{22} · 200\\.00 EUR · aprobado")
                .containsPattern("\\[demo\\] Retirar · ES\\d{22} · 50\\.00 EUR · aprobada")
                .containsPattern("\\[demo\\] Retirar · ES\\d{22} · 1000\\.00 EUR · rechazada");
    }

    @Test
    @DisplayName("La demo transfiere de Grace a Ada usando el IBAN generado de Ada")
    void la_demo_transfiere_de_grace_a_ada_con_el_iban_generado() {
        new DomainModelRunner(commands).run(new DefaultApplicationArguments());

        assertThat(detailsOf("Grace Hopper").balance()).isEqualByComparingTo("4700.00");
        assertThat(detailsOf("Ada Lovelace").balance()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("Deja una traza de la transferencia con los dos IBAN, el importe y su resultado")
    void deja_una_traza_de_la_transferencia(CapturedOutput output) {
        new DomainModelRunner(commands).run(new DefaultApplicationArguments());

        String adaIban = detailsOf("Ada Lovelace").accountNumber();
        String graceIban = detailsOf("Grace Hopper").accountNumber();
        assertThat(output).contains(
                "[demo] Transferir · %s → %s · 300.00 EUR · aprobada".formatted(graceIban, adaIban));
    }
}
