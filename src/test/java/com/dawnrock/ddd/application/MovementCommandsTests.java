package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

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

    private static TransferCommand transfer(String amount, String currency) {
        TransferCommand.Builder builder = TransferCommand.builder()
                .sourceAccountId(ACCOUNT_ID)
                .targetAccountNumber("ES7921000813610123456789")
                .amount(new BigDecimal(amount));
        return currency == null ? builder.build() : builder.currency(currency).build();
    }

    @Test
    @DisplayName("Una transferencia válida se construye y usa EUR por defecto")
    void aValidTransferIsBuiltAndUsesEurByDefault() {
        TransferCommand command = transfer("25.50", null);

        assertThat(command.sourceAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(command.amount()).isEqualByComparingTo("25.50");
        assertThat(command.currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Una transferencia con origen y destino iguales se construye: es una regla del dominio")
    void aTransferWithTheSameSourceAndTargetIsBuilt() {
        TransferCommand command = TransferCommand.builder()
                .sourceAccountId(ACCOUNT_ID)
                .targetAccountNumber("ES9121000418450200051332")
                .amount(new BigDecimal("10"))
                .build();

        assertThat(command.targetAccountNumber()).isEqualTo("ES9121000418450200051332");
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "0.001"})
    @DisplayName("El importe de una transferencia debe ser positivo después de redondear")
    void aTransferAmountMustBePositiveAfterRounding(String amount) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> transfer(amount, null))
                .withMessage("El importe debe ser positivo");
    }

    @Test
    @DisplayName("La divisa de una transferencia debe ser un código ISO 4217 válido")
    void aTransferCurrencyMustBeAValidIso4217Code() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> transfer("10", "XYZ"))
                .withMessage("La divisa XYZ no es un código ISO 4217 válido");
    }

    @Test
    @DisplayName("Un ingreso válido se construye y usa EUR por defecto")
    void aValidDepositIsBuiltAndUsesEurByDefault() {
        DepositCommand command = deposit("25.50", null);

        assertThat(command.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(command.amount()).isEqualByComparingTo("25.50");
        assertThat(command.currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Una retirada válida se construye y usa EUR por defecto")
    void aValidWithdrawalIsBuiltAndUsesEurByDefault() {
        WithdrawCommand command = withdraw("25.50", null);

        assertThat(command.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(command.amount()).isEqualByComparingTo("25.50");
        assertThat(command.currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Una divisa ISO 4217 distinta de EUR se acepta: es el dominio quien la rechaza")
    void anIsoCurrencyOtherThanEurIsAccepted() {
        assertThat(deposit("10", "USD").currency()).isEqualTo("USD");
        assertThat(withdraw("10", "USD").currency()).isEqualTo("USD");
    }

    @ParameterizedTest
    @CsvSource({"0", "0.00", "-1", "-50.00", "0.001", "0.004", "-0.001"})
    @DisplayName("El importe debe ser positivo después de redondear a los decimales de la divisa")
    void theAmountMustBePositiveAfterRounding(String amount) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> deposit(amount, null))
                .withMessage("El importe debe ser positivo");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> withdraw(amount, null))
                .withMessage("El importe debe ser positivo");
    }

    @Test
    @DisplayName("Un importe que redondea hacia arriba al mínimo de la divisa se acepta")
    void anAmountRoundingUpToTheCurrencyMinimumIsAccepted() {
        assertThat(deposit("0.005", null).amount()).isEqualByComparingTo("0.005");
        assertThat(withdraw("0.005", null).amount()).isEqualByComparingTo("0.005");
    }

    @ParameterizedTest
    @CsvSource({"999999999.99", "999999999.990"})
    @DisplayName("Un importe que no supera el máximo de 999.999.999,99 se acepta")
    void anAmountNotAboveTheMaximumIsAccepted(String amount) {
        assertThat(deposit(amount, null).amount()).isEqualByComparingTo(amount);
        assertThat(withdraw(amount, null).amount()).isEqualByComparingTo(amount);
        assertThat(transfer(amount, null).amount()).isEqualByComparingTo(amount);
    }

    @ParameterizedTest
    @CsvSource({"999999999.991", "1000000000.00", "1.7976931348623155E+308"})
    @DisplayName("Un importe que supera el máximo de 999.999.999,99, aunque sea en una fracción de céntimo, se rechaza")
    void anAmountAboveTheMaximumIsRejected(String amount) {
        String message = "El importe no puede superar 999999999.99";

        assertThatIllegalArgumentException().isThrownBy(() -> deposit(amount, null)).withMessage(message);
        assertThatIllegalArgumentException().isThrownBy(() -> withdraw(amount, null)).withMessage(message);
        assertThatIllegalArgumentException().isThrownBy(() -> transfer(amount, null)).withMessage(message);
    }

    @Test
    @DisplayName("Los decimales mínimos dependen de la divisa: 0.4 JPY se rechaza y 0.5 JPY se acepta")
    void theMinimumDecimalsDependOnTheCurrency() {
        assertThatIllegalArgumentException().isThrownBy(() -> deposit("0.4", "JPY"));
        assertThat(deposit("0.5", "JPY").currency()).isEqualTo("JPY");
    }

    @Test
    @DisplayName("Una transferencia por número de cuenta se construye con el destino tal como se escribió")
    void aTransferByAccountNumberIsBuilt() {
        TransferCommand command = TransferCommand.builder()
                .sourceAccountId(ACCOUNT_ID)
                .targetAccountNumber("es91 2100 0418 4502 0005 1332")
                .amount(new BigDecimal("10"))
                .build();

        assertThat(command.targetAccountNumber()).isEqualTo("es91 2100 0418 4502 0005 1332");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ES12", "XX9121000418450200051332", "ES91210004184502000513AB", "ES912100041845020005133"})
    @DisplayName("Un número de cuenta de destino mal formado se rechaza al construir el command")
    void aMalformedTargetAccountNumberIsRejected(String number) {
        assertThatIllegalArgumentException().isThrownBy(() -> TransferCommand.builder()
                .sourceAccountId(ACCOUNT_ID)
                .targetAccountNumber(number)
                .amount(new BigDecimal("10"))
                .build());
    }

    @Test
    @DisplayName("Una transferencia sin cuenta de destino no se puede construir")
    void aTransferWithoutTargetAccountCannotBeBuilt() {
        assertThatIllegalStateException().isThrownBy(() -> TransferCommand.builder()
                .sourceAccountId(ACCOUNT_ID).amount(new BigDecimal("10")).build())
                .withMessageContaining("targetAccountNumber");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "eur", "XYZ", "EU", "EURO"})
    @DisplayName("La divisa debe ser un código ISO 4217 válido")
    void theCurrencyMustBeAValidIso4217Code(String currency) {
        String message = "La divisa %s no es un código ISO 4217 válido".formatted(currency);

        assertThatIllegalArgumentException().isThrownBy(() -> deposit("10", currency)).withMessage(message);
        assertThatIllegalArgumentException().isThrownBy(() -> withdraw("10", currency)).withMessage(message);
    }
}
