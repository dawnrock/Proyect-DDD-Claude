package com.pronoide.ddd.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CheckingAccountFactory;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.TaxId;
import com.pronoide.ddd.domain.service.MoneyTransferService;

/** La configuración construye el núcleo sin Spring: se prueba llamando a sus métodos {@code @Bean} directamente. */
class DomainConfigurationTests {

    private final DomainConfiguration configuration = new DomainConfiguration();

    @Test
    @DisplayName("Construye la factoría de cuentas con los puertos de numeración y de verificación de ingresos")
    void construye_la_factoria_con_sus_dos_puertos() {
        AccountNumber number = new AccountNumber("ES9121000418450200051332");
        CheckingAccountFactory factory =
                configuration.checkingAccountFactory(() -> number, taxId -> Optional.empty());

        CheckingAccount account =
                factory.open("Ada Lovelace", new TaxId("12345678Z"), MonetaryAmount.euros("100.00"));

        assertThat(account.accountNumber()).isEqualTo(number);
    }

    @Test
    @DisplayName("Construye el servicio de dominio de las transferencias")
    void construye_el_servicio_de_transferencias() {
        assertThat(configuration.moneyTransferService()).isInstanceOf(MoneyTransferService.class);
    }
}
