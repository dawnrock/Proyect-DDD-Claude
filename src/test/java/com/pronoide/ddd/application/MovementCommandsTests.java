package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Validación de {@link DepositCommand} y {@link WithdrawCommand}, que comparten reglas. */
class MovementCommandsTests {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    private static DepositCommand deposit(String amount, String currency) {
        DepositCommand.Builder builder = DepositCommand.builder().accountId(ACCOUNT_ID).amount(new BigDecimal(amount));
        return currency == null ? builder.build() : builder.currency(currency).build();
    }

    private static WithdrawCommand withdraw(String amount, String currency) {
        WithdrawCommand.Builder builder = WithdrawCommand.builder().accountId(ACCOUNT_ID).amount(new BigDecimal(amount));
        return currency == null ? builder.build() : builder.currency(currency).build();
    }

    @Test
    @DisplayName("Un ingreso válido se construye y usa EUR por defecto")
    void un_ingreso_valido_se_construye_y_usa_eur_por_defecto() {
        DepositCommand command = deposit("25.50", null);

        assertThat(command.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(command.amount()).isEqualByComparingTo("25.50");
        assertThat(command.currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Una retirada válida se construye y usa EUR por defecto")
    void una_retirada_valida_se_construye_y_usa_eur_por_defecto() {
        WithdrawCommand command = withdraw("25.50", null);

        assertThat(command.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(command.amount()).isEqualByComparingTo("25.50");
        assertThat(command.currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Una divisa ISO 4217 distinta de EUR se acepta: es el dominio quien la rechaza")
    void una_divisa_iso_distinta_de_eur_se_acepta() {
        assertThat(deposit("10", "USD").currency()).isEqualTo("USD");
        assertThat(withdraw("10", "USD").currency()).isEqualTo("USD");
    }

    @ParameterizedTest
    @CsvSource({"0", "0.00", "-1", "-50.00", "0.001", "0.004", "-0.001"})
    @DisplayName("El importe debe ser positivo después de redondear a los decimales de la divisa")
    void el_importe_debe_ser_positivo_despues_de_redondear(String amount) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> deposit(amount, null))
                .withMessage("El importe debe ser positivo");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> withdraw(amount, null))
                .withMessage("El importe debe ser positivo");
    }

    @Test
    @DisplayName("Un importe que redondea hacia arriba al mínimo de la divisa se acepta")
    void un_importe_que_redondea_al_minimo_de_la_divisa_se_acepta() {
        assertThat(deposit("0.005", null).amount()).isEqualByComparingTo("0.005");
        assertThat(withdraw("0.005", null).amount()).isEqualByComparingTo("0.005");
    }

    @Test
    @DisplayName("Los decimales mínimos dependen de la divisa: 0.4 JPY se rechaza y 0.5 JPY se acepta")
    void los_decimales_minimos_dependen_de_la_divisa() {
        assertThatIllegalArgumentException().isThrownBy(() -> deposit("0.4", "JPY"));
        assertThat(deposit("0.5", "JPY").currency()).isEqualTo("JPY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "eur", "XYZ", "EU", "EURO"})
    @DisplayName("La divisa debe ser un código ISO 4217 válido")
    void la_divisa_debe_ser_un_codigo_iso_4217_valido(String currency) {
        String message = "La divisa %s no es un código ISO 4217 válido".formatted(currency);

        assertThatIllegalArgumentException().isThrownBy(() -> deposit("10", currency)).withMessage(message);
        assertThatIllegalArgumentException().isThrownBy(() -> withdraw("10", currency)).withMessage(message);
    }
}
