package com.pronoide.ddd.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CustomerSegment;
import com.pronoide.ddd.domain.model.IncomeVerifier;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.TaxId;
import com.pronoide.ddd.domain.model.Transaction;
import com.pronoide.ddd.support.TestAccounts;

class SegmentReviewServiceTests {

    /** Agencia tributaria de prueba: devuelve los ingresos indicados y cuenta las consultas. */
    private static final class CountingVerifier implements IncomeVerifier {

        private String income;
        private int calls;

        private CountingVerifier(String income) {
            this.income = income;
        }

        @Override
        public Optional<MonetaryAmount> verifiedAnnualIncome(TaxId taxId) {
            calls++;
            return Optional.ofNullable(income).map(MonetaryAmount::euros);
        }
    }

    private static Instant mesesDespuesDe(Instant instante, int meses) {
        return instante.atZone(ZoneOffset.UTC).plusMonths(meses).toInstant();
    }

    /** Cuenta de alto patrimonio por saldo que una retirada deja bajo el mínimo; devuelve el instante de la retirada. */
    private static CheckingAccount cuentaBajoElMinimo() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("150000.00").open();
        cuenta.tryWithdraw(MonetaryAmount.euros("60000.00"));
        return cuenta;
    }

    private static Instant revision(CheckingAccount cuenta) {
        return cuenta.nextSegmentReview().orElseThrow();
    }

    @Test
    @DisplayName("Antes de cumplirse el mes no cambia nada y no se consulta a la agencia tributaria")
    void antes_de_cumplirse_el_mes_no_cambia_nada_y_no_se_consulta_a_la_agencia() {
        CountingVerifier agencia = new CountingVerifier("40000.00");
        CheckingAccount cuenta = cuentaBajoElMinimo();

        new SegmentReviewService(agencia).review(cuenta, revision(cuenta).minusSeconds(1));

        assertThat(cuenta.primaryHolder().isHighNetWorth()).isTrue();
        assertThat(agencia.calls).isZero();
    }

    @Test
    @DisplayName("Tras un mes bajo el mínimo, sin ingresos altos verificados el titular vuelve a minorista")
    void tras_un_mes_bajo_el_minimo_sin_ingresos_altos_el_titular_vuelve_a_minorista() {
        CountingVerifier agencia = new CountingVerifier("100000.00");
        CheckingAccount cuenta = cuentaBajoElMinimo();

        new SegmentReviewService(agencia).review(cuenta, revision(cuenta));

        assertThat(cuenta.primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
        assertThat(cuenta.nextSegmentReview()).isEmpty();
        assertThat(agencia.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("Si la agencia no puede verificar los ingresos, el titular vuelve a minorista")
    void si_la_agencia_no_verifica_los_ingresos_el_titular_vuelve_a_minorista() {
        CheckingAccount cuenta = cuentaBajoElMinimo();

        new SegmentReviewService(new CountingVerifier(null)).review(cuenta, revision(cuenta));

        assertThat(cuenta.primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
    }

    @Test
    @DisplayName("Con ingresos altos verificados el titular conserva el segmento y se le revisa de nuevo a los seis meses")
    void con_ingresos_altos_el_titular_conserva_el_segmento_y_se_revisa_a_los_seis_meses() {
        CountingVerifier agencia = new CountingVerifier("100000.01");
        SegmentReviewService servicio = new SegmentReviewService(agencia);
        CheckingAccount cuenta = cuentaBajoElMinimo();
        Instant primera = revision(cuenta);

        servicio.review(cuenta, primera);

        assertThat(cuenta.primaryHolder().isHighNetWorth()).isTrue();
        assertThat(cuenta.nextSegmentReview()).contains(mesesDespuesDe(primera, 6));
        assertThat(agencia.calls).isEqualTo(1);

        servicio.review(cuenta, mesesDespuesDe(primera, 6).minusSeconds(1));
        assertThat(agencia.calls).isEqualTo(1);

        agencia.income = "40000.00";
        servicio.review(cuenta, mesesDespuesDe(primera, 6));
        assertThat(agencia.calls).isEqualTo(2);
        assertThat(cuenta.primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
    }

    @Test
    @DisplayName("Recuperar el patrimonio mínimo antes del plazo evita la consulta a la agencia")
    void recuperar_el_patrimonio_minimo_antes_del_plazo_evita_la_consulta() {
        CountingVerifier agencia = new CountingVerifier("40000.00");
        CheckingAccount cuenta = cuentaBajoElMinimo();
        Instant plazo = revision(cuenta);
        cuenta.deposit(MonetaryAmount.euros("10000.00"));

        new SegmentReviewService(agencia).review(cuenta, plazo);

        assertThat(cuenta.primaryHolder().isHighNetWorth()).isTrue();
        assertThat(agencia.calls).isZero();
    }

    @Test
    @DisplayName("Una cuenta minorista nunca se revisa")
    void una_cuenta_minorista_nunca_se_revisa() {
        CountingVerifier agencia = new CountingVerifier("250000.00");
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("500.00").open();

        new SegmentReviewService(agencia).review(cuenta, Instant.now().plusSeconds(3600L * 24 * 3650));

        assertThat(cuenta.primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
        assertThat(agencia.calls).isZero();
    }

    @Test
    @DisplayName("Un titular que vuelve a minorista en descubierto conserva la deuda y no puede retirar más")
    void un_titular_que_vuelve_a_minorista_en_descubierto_conserva_la_deuda() {
        CheckingAccount cuenta = TestAccounts.anAccount().initialDeposit("100000.00").open();
        cuenta.tryWithdraw(MonetaryAmount.euros("100500.00"));

        new SegmentReviewService(new CountingVerifier("40000.00")).review(cuenta, revision(cuenta));
        Transaction retirada = cuenta.tryWithdraw(MonetaryAmount.euros("10.00"));

        assertThat(cuenta.primaryHolder().segment()).isEqualTo(CustomerSegment.RETAIL);
        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("-500.00"));
        assertThat(retirada.isRejected()).isTrue();
    }

    @Test
    @DisplayName("Un titular que volvió a minorista recupera el alto patrimonio al alcanzar de nuevo el mínimo")
    void un_titular_que_volvio_a_minorista_recupera_el_alto_patrimonio_al_alcanzar_el_minimo() {
        CheckingAccount cuenta = cuentaBajoElMinimo();
        new SegmentReviewService(new CountingVerifier("40000.00")).review(cuenta, revision(cuenta));

        cuenta.deposit(MonetaryAmount.euros("10000.00"));

        assertThat(cuenta.primaryHolder().isHighNetWorth()).isTrue();
        assertThat(cuenta.nextSegmentReview()).isEmpty();
    }

    @Test
    @DisplayName("Un titular de alto patrimonio por ingresos que abre bajo el mínimo conserva el segmento en su revisión semestral")
    void un_titular_por_ingresos_que_abre_bajo_el_minimo_conserva_el_segmento_en_su_revision_semestral() {
        CountingVerifier agencia = new CountingVerifier("250000.00");
        CheckingAccount cuenta = TestAccounts.anAccount()
                .segment(CustomerSegment.HIGH_NET_WORTH).initialDeposit("5000.00").open();
        Instant primera = revision(cuenta);

        new SegmentReviewService(agencia).review(cuenta, primera);

        assertThat(cuenta.primaryHolder().isHighNetWorth()).isTrue();
        assertThat(cuenta.nextSegmentReview()).contains(mesesDespuesDe(primera, 6));
        assertThat(agencia.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("El momento de la revisión es obligatorio y el agregado no admite resolver una revisión no vencida")
    void el_momento_es_obligatorio_y_no_se_resuelve_una_revision_no_vencida() {
        CheckingAccount cuenta = cuentaBajoElMinimo();

        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new SegmentReviewService(new CountingVerifier(null)).review(cuenta, null))
                .withMessage("El momento de la revisión es obligatorio");
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> cuenta.resolveSegmentReview(revision(cuenta).minusSeconds(1), false))
                .withMessage("La revisión del segmento aún no está vencida");
    }
}
