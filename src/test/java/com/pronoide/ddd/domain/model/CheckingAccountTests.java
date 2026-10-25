package com.pronoide.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.pronoide.ddd.support.TestAccounts;

class CheckingAccountTests {

    private static final AccountNumber IBAN = new AccountNumber("ES91 2100 0418 4502 0005 1332");

    @Test
    @DisplayName("Una cuenta no puede abrirse por debajo del depósito inicial mínimo")
    void una_cuenta_no_puede_abrirse_por_debajo_del_deposito_minimo() {
        AccountHolder titular = AccountHolder.register("Ada Lovelace", CustomerSegment.RETAIL);
        
        assertThatExceptionOfType(InsufficientInitialDepositException.class)
                .isThrownBy(() -> CheckingAccount.open(IBAN, titular, MonetaryAmount.euros("25.00")))
                .withMessageContaining("50.00 EUR");
    }

    @Test
    @DisplayName("Al abrir la cuenta, el depósito inicial queda registrado como movimiento aprobado")
    void al_abrir_la_cuenta_el_deposito_inicial_queda_registrado() {
        CheckingAccount cuenta = TestAccounts.anAccount().segment(CustomerSegment.RETAIL).initialDeposit("500.00").open();

        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("500.00"));
        assertThat(cuenta.transactions()).hasSize(1);
        assertThat(cuenta.transactions().get(0).type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(cuenta.transactions().get(0).isApproved()).isTrue();
    }

    @Test
    @DisplayName("Una retirada con saldo suficiente se aprueba y actualiza el saldo")
    void una_retirada_con_saldo_suficiente_se_aprueba() {
        CheckingAccount cuenta = TestAccounts.anAccount().segment(CustomerSegment.RETAIL).initialDeposit("500.00").open();
        Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("200.00"));
        
        assertThat(retirada.isApproved()).isTrue();
        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("300.00"));
        assertThat(cuenta.transactions()).hasSize(2);
    }

    @Test
    @DisplayName("Un cliente minorista no puede dejar la cuenta en descubierto")
    void un_cliente_minorista_no_puede_dejar_la_cuenta_en_descubierto() {
        CheckingAccount cuenta = TestAccounts.anAccount().segment(CustomerSegment.RETAIL).initialDeposit("500.00").open();
        Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("600.00"));

        assertThat(retirada.isRejected()).isTrue();
        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("500.00"));
        assertThat(cuenta.transactions()).hasSize(2);
    }

    @Test
    @DisplayName("Un cliente de alto patrimonio puede dejar la cuenta en descubierto")
    void un_cliente_de_alto_patrimonio_puede_dejar_la_cuenta_en_descubierto() {
        CheckingAccount cuenta = TestAccounts.anAccount().segment(CustomerSegment.HIGH_NET_WORTH).initialDeposit("500.00").open();
        Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("900.00"));

        assertThat(retirada.isApproved()).isTrue();
        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("-400.00"));
    }

    @Test
    @DisplayName("Ningún cliente puede superar el límite de descubierto")
    void ningun_cliente_puede_superar_el_limite_de_descubierto() {
        CheckingAccount cuenta = TestAccounts.anAccount().segment(CustomerSegment.HIGH_NET_WORTH).initialDeposit("500.00").open();
        Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("1600.00"));
        assertThat(retirada.isRejected()).isTrue();
        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("500.00"));
    }

    @ParameterizedTest(name = "{0} con 500.00 retira {1}: {2}, saldo {3}")
    @DisplayName("Las retiradas en el límite del descubierto se aprueban o se rechazan según el segmento")
    @CsvSource({
            "RETAIL, 500.00, APPROVED, 0.00",
            "RETAIL, 500.01, REJECTED, 500.00",
            "HIGH_NET_WORTH, 1500.00, APPROVED, -1000.00",
            "HIGH_NET_WORTH, 1500.01, REJECTED, 500.00"
    })
    void las_retiradas_en_el_limite_del_descubierto_dependen_del_segmento(
            CustomerSegment segmento, String importe, TransactionStatus estado, String saldoFinal) {
        CheckingAccount cuenta = TestAccounts.anAccount().segment(segmento).initialDeposit("500.00").open();

        Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros(importe));

        assertThat(retirada.status()).isEqualTo(estado);
        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros(saldoFinal));
    }

    @Test
    @DisplayName("Los importes de las operaciones deben ser positivos")
    void los_importes_de_las_operaciones_deben_ser_positivos() {
        CheckingAccount cuenta = TestAccounts.anAccount().segment(CustomerSegment.RETAIL).initialDeposit("500.00").open();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> cuenta.deposit(MonetaryAmount.euros("0.00")));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> cuenta.tryWithdraw(MonetaryAmount.euros("-10.00")));

        assertThat(cuenta.transactions()).hasSize(1);
    }

    @Test
    @DisplayName("La cuenta sólo opera en la divisa con la que se abrió")
    void la_cuenta_solo_opera_en_la_divisa_con_la_que_se_abrio() {
        CheckingAccount cuenta = TestAccounts.anAccount().segment(CustomerSegment.RETAIL).initialDeposit("500.00").open();
        MonetaryAmount dolares = new MonetaryAmount(new BigDecimal("100.00"), Currency.getInstance("USD"));
        assertThatExceptionOfType(CurrencyMismatchException.class)
                .isThrownBy(() -> cuenta.deposit(dolares));
    }

    @Test
    @DisplayName("Los movimientos no pueden manipularse desde fuera del agregado")
    void los_movimientos_no_pueden_manipularse_desde_fuera_del_agregado() {
        CheckingAccount cuenta = TestAccounts.anAccount().segment(CustomerSegment.RETAIL).initialDeposit("500.00").open();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> cuenta.transactions().clear());
        assertThat(cuenta.transactions()).hasSize(1);
    }

    @Test
    @DisplayName("Un movimiento ya resuelto no puede volver a resolverse")
    void un_movimiento_ya_resuelto_no_puede_volver_a_resolverse() {
        CheckingAccount cuenta = TestAccounts.anAccount().segment(CustomerSegment.RETAIL).initialDeposit("500.00").open();

        Transaction deposito = cuenta.transactions().get(0);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(deposito::approve)
                .withMessageContaining("APPROVED");
    }

}
