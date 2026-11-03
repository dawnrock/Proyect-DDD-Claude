package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import com.pronoide.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.pronoide.ddd.adapter.out.taxagency.TaxAgencyTestAdapters;
import com.pronoide.ddd.domain.model.DomainEventPublisher;

class DemoDataLoaderTests {

    private final AccountOwnershipRepository ownership = PersistenceTestAdapters.ownership();
    private final CheckingAccountService service =
            new CheckingAccountService(PersistenceTestAdapters.accounts(), ownership, mock(DomainEventPublisher.class),
                    PersistenceTestAdapters.accountNumbers(), TaxAgencyTestAdapters.incomeVerifier());

    @Test
    @DisplayName("Al arrancar abre tres cuentas de demostración con el caso de uso, una de ellas de alto patrimonio")
    void al_arrancar_abre_tres_cuentas_de_demostracion_con_el_caso_de_uso() {
        new DemoDataLoader(service).run(new DefaultApplicationArguments());

        List<AccountSummary> accounts = service.listAccounts(CallerDef.system());
        assertThat(accounts).hasSize(3);
        assertThat(accounts)
                .map(account -> service.findAccount(CallerDef.system(), account.id()).segment())
                .containsExactlyInAnyOrder("RETAIL", "RETAIL", "HIGH_NET_WORTH");
    }

    @Test
    @DisplayName("Cada cuenta de demostración queda a nombre de su usuario: ada, grace y alan")
    void cada_cuenta_de_demostracion_queda_a_nombre_de_su_usuario() {
        new DemoDataLoader(service).run(new DefaultApplicationArguments());

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

        new DemoDataLoader(withoutIncomes).run(new DefaultApplicationArguments());

        assertThat(withoutIncomes.listAccounts(CallerDef.system()))
                .extracting(AccountSummary::segment).containsOnly("RETAIL");
    }

    @Test
    @DisplayName("Las cuentas de demostración llevan un IBAN generado y distinto")
    void las_cuentas_de_demostracion_llevan_un_iban_generado() {
        new DemoDataLoader(service).run(new DefaultApplicationArguments());

        assertThat(service.listAccounts(CallerDef.system())).extracting(AccountSummary::accountNumber)
                .doesNotHaveDuplicates().allMatch(number -> number.startsWith("ES") && number.startsWith("2100", 4));
    }
}
