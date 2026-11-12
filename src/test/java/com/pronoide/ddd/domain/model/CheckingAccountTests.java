package com.pronoide.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
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
        AccountHolder titular = AccountHolder.register("Ada Lovelace");
        
        assertThatExceptionOfType(InsufficientInitialDepositException.class)
                .isThrownBy(() -> CheckingAccount.open(IBAN, titular, MonetaryAmount.euros("25.00")))
                .withMessageContaining("50.00 EUR");
    }

    @Test
    @DisplayName("Al abrir la cuenta, el depósito inicial queda registrado como movimiento aprobado")
    void al_abrir_la_cuenta_el_deposito_inicial_queda_registrado() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();

        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("500.00"));
        assertThat(cuenta.transactions()).hasSize(1);
        assertThat(cuenta.transactions().get(0).type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(cuenta.transactions().get(0).isApproved()).isTrue();
    }

    @Test
    @DisplayName("Una retirada con saldo suficiente se aprueba y actualiza el saldo")
    void una_retirada_con_saldo_suficiente_se_aprueba() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();
        Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("200.00"));
        
        assertThat(retirada.isApproved()).isTrue();
        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("300.00"));
        assertThat(cuenta.transactions()).hasSize(2);
    }

    @Test
    @DisplayName("Un cliente minorista no puede dejar la cuenta en descubierto")
    void un_cliente_minorista_no_puede_dejar_la_cuenta_en_descubierto() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();
        Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("600.00"));

        assertThat(retirada.isRejected()).isTrue();
        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("500.00"));
        assertThat(cuenta.transactions()).hasSize(2);
    }

    @Test
    @DisplayName("Un cliente de alto patrimonio puede dejar la cuenta en descubierto")
    void un_cliente_de_alto_patrimonio_puede_dejar_la_cuenta_en_descubierto() {
        CheckingAccount cuenta =
                TestAccounts.anAccount().segment(CustomerSegment.HIGH_NET_WORTH).initialDeposit("500.00").open();
        Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("900.00"));

        assertThat(retirada.isApproved()).isTrue();
        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("-400.00"));
    }

    @Test
    @DisplayName("Ningún cliente puede superar el límite de descubierto")
    void ningun_cliente_puede_superar_el_limite_de_descubierto() {
        CheckingAccount cuenta =
                TestAccounts.anAccount().segment(CustomerSegment.HIGH_NET_WORTH).initialDeposit("500.00").open();
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
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> cuenta.deposit(MonetaryAmount.euros("0.00")));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> cuenta.tryWithdraw(MonetaryAmount.euros("-10.00")));

        assertThat(cuenta.transactions()).hasSize(1);
    }

    @Test
    @DisplayName("La cuenta sólo opera en la divisa con la que se abrió")
    void la_cuenta_solo_opera_en_la_divisa_con_la_que_se_abrio() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();
        MonetaryAmount dolares = new MonetaryAmount(new BigDecimal("100.00"), Currency.getInstance("USD"));
        assertThatExceptionOfType(CurrencyMismatchException.class)
                .isThrownBy(() -> cuenta.deposit(dolares));
    }

    @Test
    @DisplayName("Los movimientos no pueden manipularse desde fuera del agregado")
    void los_movimientos_no_pueden_manipularse_desde_fuera_del_agregado() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> cuenta.transactions().clear());
        assertThat(cuenta.transactions()).hasSize(1);
    }

    @Test
    @DisplayName("Un movimiento ya resuelto no puede volver a resolverse")
    void un_movimiento_ya_resuelto_no_puede_volver_a_resolverse() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();

        Transaction deposito = cuenta.transactions().get(0);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(deposito::approve)
                .withMessageContaining("APPROVED");
    }

    @Test
    @DisplayName("Un ingreso que lleva el saldo al patrimonio mínimo convierte al titular en alto patrimonio")
    void un_ingreso_que_alcanza_el_patrimonio_minimo_promociona_al_titular() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("99000.00").open();

        cuenta.deposit(MonetaryAmount.euros("1000.00"));

        assertThat(cuenta.primaryHolder().isHighNetWorth()).isTrue();
    }

    @Test
    @DisplayName("Un céntimo por debajo del patrimonio mínimo el titular sigue siendo minorista")
    void un_centimo_por_debajo_del_patrimonio_minimo_el_titular_sigue_siendo_minorista() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("99000.00").open();

        cuenta.deposit(MonetaryAmount.euros("999.99"));

        assertThat(cuenta.primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
    }

    @Test
    @DisplayName("Una cuenta abierta con el patrimonio mínimo nace como alto patrimonio")
    void una_cuenta_abierta_con_el_patrimonio_minimo_nace_como_alto_patrimonio() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("100000.00").open();

        assertThat(cuenta.primaryHolder().isHighNetWorth()).isTrue();
    }

    @Test
    @DisplayName("Una retirada aprobada que deja una cuenta de alto patrimonio bajo el mínimo anota desde cuándo")
    void una_retirada_que_deja_la_cuenta_bajo_el_patrimonio_minimo_anota_desde_cuando() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("150000.00").open();

        Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("60000.00"));

        assertThat(cuenta.belowMinimumNetWorthSince()).contains(retirada.occurredOn());
        assertThat(cuenta.primaryHolder().isHighNetWorth()).isTrue();
    }

    @Test
    @DisplayName("Una segunda retirada bajo el mínimo no mueve la fecha anotada")
    void una_segunda_retirada_bajo_el_minimo_no_mueve_la_fecha() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("150000.00").open();
        Transaction primera = cuenta.tryWithdraw(MonetaryAmount.euros("60000.00"));

        cuenta.tryWithdraw(MonetaryAmount.euros("10.00"));

        assertThat(cuenta.belowMinimumNetWorthSince()).contains(primera.occurredOn());
    }

    @Test
    @DisplayName("Un ingreso que recupera el patrimonio mínimo borra la fecha anotada")
    void un_ingreso_que_recupera_el_patrimonio_minimo_borra_la_fecha() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("150000.00").open();
        cuenta.tryWithdraw(MonetaryAmount.euros("60000.00"));

        cuenta.deposit(MonetaryAmount.euros("10000.00"));

        assertThat(cuenta.belowMinimumNetWorthSince()).isEmpty();
    }

    @Test
    @DisplayName("Una retirada rechazada no anota ninguna fecha")
    void una_retirada_rechazada_no_anota_ninguna_fecha() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("100000.00").open();

        Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("200000.00"));

        assertThat(retirada.isRejected()).isTrue();
        assertThat(cuenta.belowMinimumNetWorthSince()).isEmpty();
    }

    @Test
    @DisplayName("Una cuenta minorista no anota ninguna fecha al retirar")
    void una_cuenta_minorista_no_anota_ninguna_fecha_al_retirar() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();

        cuenta.tryWithdraw(MonetaryAmount.euros("100.00"));

        assertThat(cuenta.belowMinimumNetWorthSince()).isEmpty();
    }

    private static Instant unMesDespuesDe(Instant instante) {
        return instante.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
    }

    @Test
    @DisplayName("Antes de cumplirse un mes bajo el mínimo el titular sigue siendo de alto patrimonio")
    void antes_de_un_mes_bajo_el_minimo_el_titular_sigue_siendo_de_alto_patrimonio() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("150000.00").open();
        Instant desde = cuenta.tryWithdraw(MonetaryAmount.euros("60000.00")).occurredOn();

        cuenta.reviewSegment(unMesDespuesDe(desde).minusSeconds(1));

        assertThat(cuenta.primaryHolder().isHighNetWorth()).isTrue();
        assertThat(cuenta.belowMinimumNetWorthSince()).contains(desde);
    }

    @Test
    @DisplayName("Tras un mes bajo el patrimonio mínimo el titular vuelve a ser minorista")
    void tras_un_mes_bajo_el_patrimonio_minimo_el_titular_vuelve_a_ser_minorista() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("150000.00").open();
        Instant desde = cuenta.tryWithdraw(MonetaryAmount.euros("60000.00")).occurredOn();

        cuenta.reviewSegment(unMesDespuesDe(desde));

        assertThat(cuenta.primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
        assertThat(cuenta.belowMinimumNetWorthSince()).isEmpty();
    }

    @Test
    @DisplayName("Recuperar el patrimonio mínimo antes de un mes cancela la vuelta a minorista")
    void recuperar_el_patrimonio_minimo_cancela_la_vuelta_a_minorista() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("150000.00").open();
        Instant desde = cuenta.tryWithdraw(MonetaryAmount.euros("60000.00")).occurredOn();
        cuenta.deposit(MonetaryAmount.euros("10000.00"));

        cuenta.reviewSegment(unMesDespuesDe(desde));

        assertThat(cuenta.primaryHolder().isHighNetWorth()).isTrue();
    }

    @Test
    @DisplayName("Revisar el segmento de una cuenta minorista no cambia nada")
    void revisar_el_segmento_de_una_cuenta_minorista_no_cambia_nada() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();

        cuenta.reviewSegment(Instant.now().plusSeconds(10L * 365 * 24 * 3600));

        assertThat(cuenta.primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
    }

    @Test
    @DisplayName("Un titular que vuelve a minorista en descubierto conserva la deuda y no puede retirar más")
    void un_titular_que_vuelve_a_minorista_en_descubierto_conserva_la_deuda() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("100000.00").open();
        Instant desde = cuenta.tryWithdraw(MonetaryAmount.euros("100500.00")).occurredOn();

        cuenta.reviewSegment(unMesDespuesDe(desde));
        Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("10.00"));

        assertThat(cuenta.primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("-500.00"));
        assertThat(retirada.isRejected()).isTrue();
    }

    @Test
    @DisplayName("Un titular que volvió a minorista recupera el alto patrimonio al alcanzar de nuevo el mínimo")
    void un_titular_que_volvio_a_minorista_recupera_el_alto_patrimonio_al_alcanzar_de_nuevo_el_minimo() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("150000.00").open();
        Instant desde = cuenta.tryWithdraw(MonetaryAmount.euros("60000.00")).occurredOn();
        cuenta.reviewSegment(unMesDespuesDe(desde));

        cuenta.deposit(MonetaryAmount.euros("10000.00"));

        assertThat(cuenta.primaryHolder().isHighNetWorth()).isTrue();
        assertThat(cuenta.belowMinimumNetWorthSince()).isEmpty();
    }

    @Test
    @DisplayName("El segmento no se puede revisar sin indicar el momento")
    void el_segmento_no_se_puede_revisar_sin_indicar_el_momento() {
        CheckingAccount cuenta = TestAccounts.anAccount().open();

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> cuenta.reviewSegment(null))
                .withMessage("El momento de la revisión es obligatorio");
    }
}
