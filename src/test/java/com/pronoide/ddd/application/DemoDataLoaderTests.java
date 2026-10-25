package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import com.pronoide.ddd.persistence.InMemoryAccountOwnershipRepository;
import com.pronoide.ddd.persistence.InMemoryCheckingAccountRepository;

class DemoDataLoaderTests {

    private final InMemoryAccountOwnershipRepository ownership = new InMemoryAccountOwnershipRepository();
    private final CheckingAccountService service =
            new CheckingAccountService(new InMemoryCheckingAccountRepository(), ownership);

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
}
