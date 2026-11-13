package com.pronoide.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CheckingAccountFactoryTests {

    private static final AccountNumber GENERATED = new AccountNumber("ES9121000418450200051332");
    private static final TaxId TAX_ID = new TaxId("12345678Z");

    private static CheckingAccountFactory factoryWithIncome(String income) {
        return new CheckingAccountFactory(() -> GENERATED,
                taxId -> Optional.ofNullable(income).map(MonetaryAmount::euros));
    }

    @Test
    @DisplayName("La cuenta abierta recibe el número que genera la numeración")
    void la_cuenta_abierta_recibe_el_numero_generado() {
        CheckingAccount account = factoryWithIncome("40000.00").open("Ada Lovelace", TAX_ID, MonetaryAmount.euros("100.00"));

        assertThat(account.accountNumber()).isEqualTo(GENERATED);
        assertThat(account.primaryHolder().fullName()).isEqualTo("Ada Lovelace");
        assertThat(account.currentBalance()).isEqualTo(MonetaryAmount.euros("100.00"));
    }

    @Test
    @DisplayName("Con ingresos verificados por encima de 100.000,00 € el titular es de alto patrimonio")
    void con_ingresos_por_encima_del_umbral_el_titular_es_de_alto_patrimonio() {
        CheckingAccount account = factoryWithIncome("100000.01").open("Grace Hopper", TAX_ID, MonetaryAmount.euros("100.00"));

        assertThat(account.primaryHolder().segment()).isEqualTo(CustomerSegment.HIGH_NET_WORTH);
    }

    @Test
    @DisplayName("Con ingresos exactamente en el umbral el titular es minorista")
    void con_ingresos_exactamente_en_el_umbral_el_titular_es_minorista() {
        CheckingAccount account = factoryWithIncome("100000.00").open("Ada Lovelace", TAX_ID, MonetaryAmount.euros("100.00"));

        assertThat(account.primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
    }

    @Test
    @DisplayName("Con ingresos por debajo del umbral el titular es minorista")
    void con_ingresos_por_debajo_del_umbral_el_titular_es_minorista() {
        CheckingAccount account = factoryWithIncome("40000.00").open("Ada Lovelace", TAX_ID, MonetaryAmount.euros("100.00"));

        assertThat(account.primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
    }

    @Test
    @DisplayName("Si la agencia no verifica los ingresos el titular es minorista")
    void si_los_ingresos_no_se_verifican_el_titular_es_minorista() {
        CheckingAccount account = factoryWithIncome(null).open("Ada Lovelace", TAX_ID, MonetaryAmount.euros("100.00"));

        assertThat(account.primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
    }

    @Test
    @DisplayName("La factoría consulta los ingresos del NIF indicado")
    void la_factoria_consulta_los_ingresos_del_nif_indicado() {
        TaxId[] asked = new TaxId[1];
        CheckingAccountFactory factory = new CheckingAccountFactory(() -> GENERATED, taxId -> {
            asked[0] = taxId;
            return Optional.empty();
        });

        factory.open("Ada Lovelace", TAX_ID, MonetaryAmount.euros("100.00"));

        assertThat(asked[0]).isEqualTo(TAX_ID);
    }

    @Test
    @DisplayName("Las reglas de la cuenta siguen aplicándose: el depósito inicial mínimo")
    void las_reglas_de_la_cuenta_siguen_aplicandose() {
        CheckingAccountFactory factory = factoryWithIncome("250000.00");

        assertThatExceptionOfType(InsufficientInitialDepositException.class)
                .isThrownBy(() -> factory.open("Grace Hopper", TAX_ID, MonetaryAmount.euros("49.99")));
    }

    @Test
    @DisplayName("El titular de la cuenta abierta conserva el NIF indicado")
    void el_titular_de_la_cuenta_abierta_conserva_el_nif_indicado() {
        CheckingAccount account = factoryWithIncome("40000.00").open("Ada Lovelace", TAX_ID, MonetaryAmount.euros("100.00"));

        assertThat(account.primaryHolder().taxId()).isEqualTo(TAX_ID);
    }
}
