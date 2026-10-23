package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAccountCommandTests {

    private static final String IBAN = "ES9121000418450200051332";

    private static OpenAccountCommand.Builder validCommand() {
        return OpenAccountCommand.builder()
                .accountNumber(IBAN)
                .holderName("Ada Lovelace")
                .segment("RETAIL")
                .initialDeposit(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Una orden con todos los campos válidos se construye y usa EUR por defecto")
    void una_orden_con_todos_los_campos_validos_se_construye_y_usa_eur_por_defecto() {
        OpenAccountCommand command = validCommand().build();

        assertThat(command.accountNumber()).isEqualTo(IBAN);
        assertThat(command.holderName()).isEqualTo("Ada Lovelace");
        assertThat(command.segment()).isEqualTo("RETAIL");
        assertThat(command.initialDeposit()).isEqualByComparingTo("100.00");
        assertThat(command.currency()).isEqualTo("EUR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RETAIL", "HIGH_NET_WORTH"})
    @DisplayName("Los dos segmentos permitidos se aceptan")
    void los_dos_segmentos_permitidos_se_aceptan(String segment) {
        assertThat(validCommand().segment(segment).build().segment()).isEqualTo(segment);
    }

    @Test
    @DisplayName("Una divisa ISO 4217 distinta de EUR se acepta: es el dominio quien la rechaza")
    void una_divisa_iso_4217_distinta_de_eur_se_acepta() {
        assertThat(validCommand().currency("USD").build().currency()).isEqualTo("USD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("El número de cuenta no puede estar en blanco")
    void el_numero_de_cuenta_no_puede_estar_en_blanco(String accountNumber) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().accountNumber(accountNumber).build())
                .withMessage("El número de cuenta es obligatorio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("El nombre del titular no puede estar en blanco")
    void el_nombre_del_titular_no_puede_estar_en_blanco(String holderName) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().holderName(holderName).build())
                .withMessage("El nombre del titular es obligatorio");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "retail", "VIP", "HIGH NET WORTH"})
    @DisplayName("El segmento solo puede ser RETAIL o HIGH_NET_WORTH")
    void el_segmento_solo_puede_ser_retail_o_high_net_worth(String segment) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().segment(segment).build())
                .withMessage("El segmento debe ser RETAIL o HIGH_NET_WORTH");
    }

    @ParameterizedTest
    @CsvSource({"0", "0.00", "-1", "-50.00"})
    @DisplayName("El depósito inicial debe ser positivo")
    void el_deposito_inicial_debe_ser_positivo(String deposit) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().initialDeposit(new BigDecimal(deposit)).build())
                .withMessage("El depósito inicial debe ser positivo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "eur", "XYZ", "EU", "EURO"})
    @DisplayName("La divisa debe ser un código ISO 4217 válido")
    void la_divisa_debe_ser_un_codigo_iso_4217_valido(String currency) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validCommand().currency(currency).build())
                .withMessage("La divisa %s no es un código ISO 4217 válido".formatted(currency));
    }
}
