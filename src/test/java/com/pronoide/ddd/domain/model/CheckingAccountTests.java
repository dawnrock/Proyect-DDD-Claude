package com.pronoide.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.pronoide.ddd.support.TestAccounts;

class CheckingAccountTests {

    private static final AccountNumber IBAN = new AccountNumber("ES91 2100 0418 4502 0005 1332");

    @Test
    @DisplayName("Una cuenta no puede abrirse por debajo del depósito inicial mínimo")
    void una_cuenta_no_puede_abrirse_por_debajo_del_deposito_minimo() {
        AccountHolder titular = AccountHolder.register("Ada Lovelace", new TaxId("12345678Z"), CustomerSegment.RETAIL);
        
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

    @Nested
    @DisplayName("Eventos de dominio")
    class DomainEvents {

        @Test
        @DisplayName("Abrir una cuenta registra el evento de cuenta abierta seguido del ingreso del depósito inicial")
        void abrir_una_cuenta_registra_cuenta_abierta_y_el_ingreso_del_deposito_inicial() {
            AccountHolder titular = AccountHolder.register("Ada Lovelace", new TaxId("12345678Z"), CustomerSegment.RETAIL);

            CheckingAccount cuenta = CheckingAccount.open(IBAN, titular, MonetaryAmount.euros("500.00"));

            Transaction deposito = cuenta.transactions().get(0);
            assertThat(cuenta.pullDomainEvents()).containsExactly(
                    new CheckingAccountOpened(cuenta.id(), IBAN, "Ada Lovelace", MonetaryAmount.euros("500.00"),
                            deposito.occurredOn()),
                    new MoneyDeposited(cuenta.id(), deposito.id(), MonetaryAmount.euros("500.00"), deposito.occurredOn()));
        }

        @Test
        @DisplayName("Un ingreso registra el evento de dinero ingresado con su movimiento")
        void un_ingreso_registra_el_evento_de_dinero_ingresado_con_su_movimiento() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();
            cuenta.pullDomainEvents();

            Transaction ingreso = cuenta.deposit(MonetaryAmount.euros("25.00"));

            assertThat(cuenta.pullDomainEvents()).containsExactly(new MoneyDeposited(
                    cuenta.id(), ingreso.id(), MonetaryAmount.euros("25.00"), ingreso.occurredOn()));
        }

        @Test
        @DisplayName("Una retirada aprobada registra el evento de dinero retirado")
        void una_retirada_aprobada_registra_el_evento_de_dinero_retirado() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();
            cuenta.pullDomainEvents();

            Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("200.00"));

            assertThat(cuenta.pullDomainEvents()).containsExactly(new MoneyWithdrawn(
                    cuenta.id(), retirada.id(), MonetaryAmount.euros("200.00"), retirada.occurredOn()));
        }

        @Test
        @DisplayName("Una retirada rechazada registra el evento de retirada rechazada y no el de dinero retirado")
        void una_retirada_rechazada_registra_el_evento_de_retirada_rechazada() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();
            cuenta.pullDomainEvents();

            Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("600.00"));

            assertThat(cuenta.pullDomainEvents()).containsExactly(new WithdrawalRejected(
                    cuenta.id(), retirada.id(), MonetaryAmount.euros("600.00"), retirada.occurredOn()));
        }

        @Test
        @DisplayName("Recoger los eventos los vacía: una segunda recogida no devuelve nada")
        void recoger_los_eventos_los_vacia() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();
            cuenta.deposit(MonetaryAmount.euros("25.00"));

            assertThat(cuenta.pullDomainEvents()).isNotEmpty();
            assertThat(cuenta.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("Los eventos recogidos no pueden modificarse desde fuera del agregado")
        void los_eventos_recogidos_no_pueden_modificarse() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> cuenta.pullDomainEvents().clear());
        }
    }

    @Nested
    @DisplayName("Cuenta de contrapartida")
    class Counterpart {

        private static final AccountNumber OTHER_IBAN = new AccountNumber("ES79 2100 0813 6101 2345 6789");

        @Test
        @DisplayName("Una retirada para una transferencia aprobada guarda la cuenta de destino")
        void una_retirada_de_transferencia_aprobada_guarda_el_destino() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();

            Transaction retirada = cuenta.tryWithdrawForTransfer(MonetaryAmount.euros("200.00"), OTHER_IBAN);

            assertThat(retirada.isApproved()).isTrue();
            assertThat(retirada.type()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(retirada.counterpart()).contains(OTHER_IBAN);
            assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("300.00"));
        }

        @Test
        @DisplayName("Una retirada para una transferencia rechazada también guarda la cuenta de destino")
        void una_retirada_de_transferencia_rechazada_guarda_el_destino() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();

            Transaction retirada = cuenta.tryWithdrawForTransfer(MonetaryAmount.euros("600.00"), OTHER_IBAN);

            assertThat(retirada.isRejected()).isTrue();
            assertThat(retirada.counterpart()).contains(OTHER_IBAN);
            assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("500.00"));
        }

        @Test
        @DisplayName("Un ingreso de una transferencia guarda la cuenta de origen")
        void un_ingreso_de_transferencia_guarda_el_origen() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();

            Transaction ingreso = cuenta.depositFromTransfer(MonetaryAmount.euros("200.00"), OTHER_IBAN);

            assertThat(ingreso.isApproved()).isTrue();
            assertThat(ingreso.type()).isEqualTo(TransactionType.DEPOSIT);
            assertThat(ingreso.counterpart()).contains(OTHER_IBAN);
            assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("700.00"));
        }

        @Test
        @DisplayName("Los movimientos de transferencia exigen una cuenta de contrapartida")
        void los_movimientos_de_transferencia_exigen_contrapartida() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();

            assertThatExceptionOfType(NullPointerException.class)
                    .isThrownBy(() -> cuenta.tryWithdrawForTransfer(MonetaryAmount.euros("10.00"), null));
            assertThatExceptionOfType(NullPointerException.class)
                    .isThrownBy(() -> cuenta.depositFromTransfer(MonetaryAmount.euros("10.00"), null));
        }

        @Test
        @DisplayName("Una cuenta no puede ser su propia contrapartida y no registra nada (R17)")
        void una_cuenta_no_puede_ser_su_propia_contrapartida() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();
            cuenta.pullDomainEvents();
            AccountNumber propia = cuenta.accountNumber();

            assertThatExceptionOfType(SameAccountTransferException.class)
                    .isThrownBy(() -> cuenta.tryWithdrawForTransfer(MonetaryAmount.euros("10.00"), propia));
            assertThatExceptionOfType(SameAccountTransferException.class)
                    .isThrownBy(() -> cuenta.depositFromTransfer(MonetaryAmount.euros("10.00"), propia));

            assertThat(cuenta.transactions()).hasSize(1);
            assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("500.00"));
            assertThat(cuenta.pullDomainEvents()).isEmpty();
        }

        @Test
        @DisplayName("Un ingreso o una retirada normales y el depósito inicial no tienen contrapartida")
        void los_movimientos_normales_no_tienen_contrapartida() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();
            cuenta.deposit(MonetaryAmount.euros("10.00"));
            cuenta.tryWithdraw(MonetaryAmount.euros("5.00"));

            assertThat(cuenta.transactions()).hasSize(3).allSatisfy(t -> assertThat(t.counterpart()).isEmpty());
        }
    }

    @Nested
    @DisplayName("Alto patrimonio por saldo")
    class HighNetWorthByBalance {

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
        @DisplayName("Una cuenta abierta con el patrimonio mínimo por un titular minorista nace como alto patrimonio")
        void una_cuenta_abierta_con_el_patrimonio_minimo_nace_como_alto_patrimonio() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("100000.00").open();

            assertThat(cuenta.primaryHolder().isHighNetWorth()).isTrue();
        }
    }

    private static Instant mesesDespuesDe(Instant instante, int meses) {
        return instante.atZone(ZoneOffset.UTC).plusMonths(meses).toInstant();
    }

    @Nested
    @DisplayName("Próxima revisión del segmento")
    class NextSegmentReview {

        @Test
        @DisplayName("Una retirada aprobada que deja una cuenta de alto patrimonio bajo el mínimo programa la revisión a un mes")
        void una_retirada_que_deja_la_cuenta_bajo_el_minimo_programa_la_revision_a_un_mes() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("150000.00").open();

            Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("60000.00"));

            assertThat(cuenta.nextSegmentReview()).contains(mesesDespuesDe(retirada.occurredOn(), 1));
            assertThat(cuenta.primaryHolder().isHighNetWorth()).isTrue();
        }

        @Test
        @DisplayName("Una segunda retirada bajo el mínimo no mueve la revisión programada")
        void una_segunda_retirada_bajo_el_minimo_no_mueve_la_revision() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("150000.00").open();
            Transaction primera = cuenta.tryWithdraw(MonetaryAmount.euros("60000.00"));

            cuenta.tryWithdraw(MonetaryAmount.euros("10.00"));

            assertThat(cuenta.nextSegmentReview()).contains(mesesDespuesDe(primera.occurredOn(), 1));
        }

        @Test
        @DisplayName("Un ingreso que recupera el patrimonio mínimo cancela la revisión programada")
        void un_ingreso_que_recupera_el_patrimonio_minimo_cancela_la_revision() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("150000.00").open();
            cuenta.tryWithdraw(MonetaryAmount.euros("60000.00"));

            cuenta.deposit(MonetaryAmount.euros("10000.00"));

            assertThat(cuenta.nextSegmentReview()).isEmpty();
        }

        @Test
        @DisplayName("Una retirada rechazada no programa ninguna revisión")
        void una_retirada_rechazada_no_programa_ninguna_revision() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("100000.00").open();

            Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("200000.00"));

            assertThat(retirada.isRejected()).isTrue();
            assertThat(cuenta.nextSegmentReview()).isEmpty();
        }

        @Test
        @DisplayName("Una cuenta minorista no programa ninguna revisión al retirar")
        void una_cuenta_minorista_no_programa_ninguna_revision_al_retirar() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();

            cuenta.tryWithdraw(MonetaryAmount.euros("100.00"));

            assertThat(cuenta.nextSegmentReview()).isEmpty();
        }

        @Test
        @DisplayName("Un titular de alto patrimonio que abre bajo el mínimo tiene su primera revisión a los seis meses")
        void un_titular_de_alto_patrimonio_que_abre_bajo_el_minimo_se_revisa_a_los_seis_meses() {
            CheckingAccount cuenta = TestAccounts.anAccount()
                    .segment(CustomerSegment.HIGH_NET_WORTH).initialDeposit("5000.00").open();

            Instant apertura = cuenta.transactions().get(0).occurredOn();
            assertThat(cuenta.nextSegmentReview()).contains(mesesDespuesDe(apertura, 6));
        }

        @Test
        @DisplayName("Una retirada no adelanta la primera revisión semestral")
        void una_retirada_no_adelanta_la_primera_revision_semestral() {
            CheckingAccount cuenta = TestAccounts.anAccount()
                    .segment(CustomerSegment.HIGH_NET_WORTH).initialDeposit("5000.00").open();
            Instant apertura = cuenta.transactions().get(0).occurredOn();

            cuenta.tryWithdraw(MonetaryAmount.euros("100.00"));

            assertThat(cuenta.nextSegmentReview()).contains(mesesDespuesDe(apertura, 6));
        }
    }

    @Nested
    @DisplayName("Evento de cambio de segmento")
    class SegmentChangedEvent {

        @Test
        @DisplayName("La promoción a alto patrimonio registra un evento con la cuenta, el titular y el nuevo segmento")
        void la_promocion_registra_un_evento_de_cambio_de_segmento() {
            CheckingAccount cuenta = TestAccounts.anAccount().holderName("Ada Lovelace").initialDeposit("99000.00").open();
            cuenta.pullDomainEvents();

            Transaction ingreso = cuenta.deposit(MonetaryAmount.euros("1000.00"));

            assertThat(cuenta.pullDomainEvents()).filteredOn(CustomerSegmentChanged.class::isInstance)
                    .containsExactly(new CustomerSegmentChanged(cuenta.id(), "Ada Lovelace",
                            CustomerSegment.HIGH_NET_WORTH, ingreso.occurredOn()));
        }

        @Test
        @DisplayName("La vuelta a minorista registra un evento con el momento de la revisión")
        void la_vuelta_a_minorista_registra_un_evento_de_cambio_de_segmento() {
            CheckingAccount cuenta = TestAccounts.anAccount().holderName("Ada Lovelace").initialDeposit("150000.00").open();
            cuenta.tryWithdraw(MonetaryAmount.euros("60000.00"));
            Instant revision = cuenta.nextSegmentReview().orElseThrow();
            cuenta.pullDomainEvents();

            cuenta.resolveSegmentReview(revision, false);

            assertThat(cuenta.pullDomainEvents()).containsExactly(
                    new CustomerSegmentChanged(cuenta.id(), "Ada Lovelace", CustomerSegment.RETAIL, revision));
        }

        @Test
        @DisplayName("Un ingreso a un titular que ya es de alto patrimonio no registra ningún cambio de segmento")
        void un_ingreso_a_un_titular_que_ya_es_de_alto_patrimonio_no_registra_cambio_de_segmento() {
            CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("150000.00").open();
            cuenta.pullDomainEvents();

            cuenta.deposit(MonetaryAmount.euros("1000.00"));

            assertThat(cuenta.pullDomainEvents()).noneMatch(CustomerSegmentChanged.class::isInstance);
        }
    }
}
