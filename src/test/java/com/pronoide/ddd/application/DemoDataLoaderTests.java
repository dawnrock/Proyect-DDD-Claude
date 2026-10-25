package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import com.pronoide.ddd.persistence.InMemoryCheckingAccountRepository;

class DemoDataLoaderTests {

    private final CheckingAccountService service = new CheckingAccountService(new InMemoryCheckingAccountRepository(),
            new com.pronoide.ddd.persistence.InMemoryAccountOwnershipRepository());

    @Test
    @DisplayName("Al arrancar abre tres cuentas de demostración con el caso de uso, una de ellas de alto patrimonio")
    void al_arrancar_abre_tres_cuentas_de_demostracion_con_el_caso_de_uso() {
        new DemoDataLoader(service).run(new DefaultApplicationArguments());

        List<AccountSummary> accounts = service.listAccounts();
        assertThat(accounts).hasSize(3);
        assertThat(accounts)
                .map(account -> service.findAccount(account.id()).segment())
                .containsExactlyInAnyOrder("RETAIL", "RETAIL", "HIGH_NET_WORTH");
    }
}
