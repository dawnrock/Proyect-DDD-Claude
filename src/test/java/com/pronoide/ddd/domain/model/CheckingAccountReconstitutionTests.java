package com.pronoide.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reconstitución de una cuenta guardada (#150): la usan los adaptadores de persistencia al leerla. */
class CheckingAccountReconstitutionTests {

    private static final UUID ID = UUID.randomUUID();
    private static final AccountNumber IBAN = new AccountNumber("ES9121000418450200051332");
    private static final AccountNumber OTHER = new AccountNumber("ES8099990001450000000001");
    private static final Instant DAY = Instant.parse("2026-11-03T10:15:30Z");

    private static AccountHolder holder(CustomerSegment segment) {
        return AccountHolder.reconstitute(UUID.randomUUID(), "Ada Lovelace", new TaxId("12345678Z"), segment);
    }

    private static Transaction movement(UUID accountId, TransactionType type, String amount, TransactionStatus status,
                                        AccountNumber counterpart) {
        return Transaction.reconstitute(UUID.randomUUID(), accountId, type, MonetaryAmount.euros(amount), DAY,
                counterpart, status);
    }

    /** Apertura con 1000, retirada de 50 aprobada, retirada de 5000 rechazada y transferencia recibida de 300. */
    private static List<Transaction> history(UUID accountId) {
        return List.of(
                movement(accountId, TransactionType.DEPOSIT, "1000.00", TransactionStatus.APPROVED, null),
                movement(accountId, TransactionType.WITHDRAWAL, "50.00", TransactionStatus.APPROVED, null),
                movement(accountId, TransactionType.WITHDRAWAL, "5000.00", TransactionStatus.REJECTED, null),
                movement(accountId, TransactionType.DEPOSIT, "300.00", TransactionStatus.APPROVED, OTHER));
    }

    @Test
    @DisplayName("Una cuenta reconstituida conserva su identidad, su número, su titular, su saldo y sus movimientos")
    void una_cuenta_reconstituida_conserva_todos_sus_datos() {
        AccountHolder titular = holder(CustomerSegment.RETAIL);
        List<Transaction> movimientos = history(ID);

        CheckingAccount cuenta = CheckingAccount.reconstitute(ID, IBAN, titular, MonetaryAmount.euros("1250.00"),
                null, movimientos);

        assertThat(cuenta.id()).isEqualTo(ID);
        assertThat(cuenta.accountNumber()).isEqualTo(IBAN);
        assertThat(cuenta.primaryHolder()).isEqualTo(titular);
        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("1250.00"));
        assertThat(cuenta.transactions()).containsExactlyElementsOf(movimientos);
        assertThat(cuenta.nextSegmentReview()).isEmpty();
    }

    @Test
    @DisplayName("Una cuenta reconstituida conserva la fecha de su próxima revisión de segmento")
    void una_cuenta_reconstituida_conserva_la_fecha_de_su_proxima_revision() {
        Instant review = Instant.parse("2027-05-03T10:15:30Z");

        CheckingAccount cuenta = CheckingAccount.reconstitute(ID, IBAN, holder(CustomerSegment.HIGH_NET_WORTH),
                MonetaryAmount.euros("1250.00"), review, history(ID));

        assertThat(cuenta.nextSegmentReview()).contains(review);
    }

    @Test
    @DisplayName("Reconstituir una cuenta no registra eventos de dominio")
    void reconstituir_una_cuenta_no_registra_eventos_de_dominio() {
        CheckingAccount cuenta = CheckingAccount.reconstitute(ID, IBAN, holder(CustomerSegment.RETAIL),
                MonetaryAmount.euros("1250.00"), null, history(ID));

        assertThat(cuenta.pullDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("Una cuenta reconstituida sigue operando con sus reglas y registra los eventos nuevos")
    void una_cuenta_reconstituida_sigue_operando_con_sus_reglas() {
        CheckingAccount cuenta = CheckingAccount.reconstitute(ID, IBAN, holder(CustomerSegment.RETAIL),
                MonetaryAmount.euros("1250.00"), null, history(ID));

        cuenta.deposit(MonetaryAmount.euros("50.00"));
        Transaction rechazada = cuenta.tryWithdraw(MonetaryAmount.euros("2000.00"));

        assertThat(cuenta.currentBalance()).isEqualTo(MonetaryAmount.euros("1300.00"));
        assertThat(rechazada.isRejected()).isTrue();
        assertThat(cuenta.transactions()).hasSize(6);
        assertThat(cuenta.pullDomainEvents()).hasExactlyElementsOfTypes(MoneyDeposited.class,
                WithdrawalRejected.class);
    }

    @Test
    @DisplayName("Una cuenta no se reconstituye si su saldo no cuadra con sus movimientos aprobados")
    void una_cuenta_no_se_reconstituye_si_su_saldo_no_cuadra_con_sus_movimientos() {
        assertThatThrownBy(() -> CheckingAccount.reconstitute(ID, IBAN, holder(CustomerSegment.RETAIL),
                MonetaryAmount.euros("1300.00"), null, history(ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1250.00 EUR");
    }

    @Test
    @DisplayName("Una cuenta no se reconstituye con movimientos de otra cuenta")
    void una_cuenta_no_se_reconstituye_con_movimientos_de_otra_cuenta() {
        assertThatThrownBy(() -> CheckingAccount.reconstitute(ID, IBAN, holder(CustomerSegment.RETAIL),
                MonetaryAmount.euros("1250.00"), null, history(UUID.randomUUID())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Un movimiento de la cuenta reconstituida pertenece a otra cuenta");
    }

    @Test
    @DisplayName("Una cuenta no se reconstituye con un movimiento pendiente")
    void una_cuenta_no_se_reconstituye_con_un_movimiento_pendiente() {
        List<Transaction> movimientos = List.of(
                movement(ID, TransactionType.DEPOSIT, "100.00", TransactionStatus.PENDING, null));

        assertThatThrownBy(() -> CheckingAccount.reconstitute(ID, IBAN, holder(CustomerSegment.RETAIL),
                MonetaryAmount.euros("0.00"), null, movimientos))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Una cuenta guardada no tiene movimientos pendientes");
    }

    @Test
    @DisplayName("Los datos obligatorios de una cuenta no pueden faltar al reconstituirla")
    void los_datos_obligatorios_de_una_cuenta_no_pueden_faltar_al_reconstituirla() {
        AccountHolder titular = holder(CustomerSegment.RETAIL);
        MonetaryAmount saldo = MonetaryAmount.euros("1250.00");
        List<Transaction> movimientos = history(ID);
        assertThatThrownBy(() -> CheckingAccount.reconstitute(null, IBAN, titular, saldo, null, movimientos))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CheckingAccount.reconstitute(ID, null, titular, saldo, null, movimientos))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CheckingAccount.reconstitute(ID, IBAN, null, saldo, null, movimientos))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CheckingAccount.reconstitute(ID, IBAN, titular, null, null, movimientos))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CheckingAccount.reconstitute(ID, IBAN, titular, saldo, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Una cuenta minorista no se reconstituye con una revisión de segmento pendiente")
    void una_cuenta_minorista_no_se_reconstituye_con_una_revision_pendiente() {
        assertThatThrownBy(() -> CheckingAccount.reconstitute(ID, IBAN, holder(CustomerSegment.RETAIL),
                MonetaryAmount.euros("1250.00"), Instant.parse("2027-05-03T10:15:30Z"), history(ID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Solo una cuenta de alto patrimonio tiene revisión de segmento pendiente");
    }

    @Test
    @DisplayName("Una cuenta no se reconstituye con dos movimientos con el mismo id")
    void una_cuenta_no_se_reconstituye_con_movimientos_repetidos() {
        Transaction deposito = movement(ID, TransactionType.DEPOSIT, "100.00", TransactionStatus.APPROVED, null);

        assertThatThrownBy(() -> CheckingAccount.reconstitute(ID, IBAN, holder(CustomerSegment.RETAIL),
                MonetaryAmount.euros("200.00"), null, List.of(deposito, deposito)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Una cuenta guardada no repite movimientos");
    }
}
