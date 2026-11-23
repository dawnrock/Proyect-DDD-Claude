package com.dawnrock.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Test
    @DisplayName("La factoría ofrece un número de cuenta nuevo de la numeración")
    void la_factoria_ofrece_un_numero_de_cuenta_nuevo() {
        assertThat(factoryWithIncome(null).nextAccountNumber()).isEqualTo(GENERATED);
    }

    @Nested
    @DisplayName("Abrir una cuenta para un cliente")
    class OpenForCustomer {

        private final Customer ada =
                Customer.register("Ada Lovelace", TAX_ID, new EmailAddress("ada@example.com"));

        @Test
        @DisplayName("El titular copia el nombre y el documento del cliente")
        void el_titular_copia_el_nombre_y_el_documento_del_cliente() {
            CheckingAccount account = factoryWithIncome("40000.00").open(ada, MonetaryAmount.euros("100.00"));

            assertThat(account.accountNumber()).isEqualTo(GENERATED);
            assertThat(account.primaryHolder().fullName()).isEqualTo("Ada Lovelace");
            assertThat(account.primaryHolder().taxId()).isEqualTo(TAX_ID);
            assertThat(account.currentBalance()).isEqualTo(MonetaryAmount.euros("100.00"));
        }

        @Test
        @DisplayName("El segmento del titular sale de los ingresos verificados del cliente")
        void el_segmento_sale_de_los_ingresos_verificados() {
            assertThat(factoryWithIncome("100000.01").open(ada, MonetaryAmount.euros("100.00"))
                    .primaryHolder().segment()).isEqualTo(CustomerSegment.HIGH_NET_WORTH);
            assertThat(factoryWithIncome("100000.00").open(ada, MonetaryAmount.euros("100.00"))
                    .primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
            assertThat(factoryWithIncome(null).open(ada, MonetaryAmount.euros("100.00"))
                    .primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
        }

        @Test
        @DisplayName("El depósito mínimo sigue aplicándose")
        void el_deposito_minimo_sigue_aplicandose() {
            assertThatExceptionOfType(InsufficientInitialDepositException.class)
                    .isThrownBy(() -> factoryWithIncome("40000.00").open(ada, MonetaryAmount.euros("49.99")));
        }

        @Test
        @DisplayName("El cliente es obligatorio")
        void el_cliente_es_obligatorio() {
            assertThatExceptionOfType(NullPointerException.class)
                    .isThrownBy(() -> factoryWithIncome(null).open((Customer) null, MonetaryAmount.euros("100.00")))
                    .withMessage("El cliente es obligatorio");
        }
    }
}
