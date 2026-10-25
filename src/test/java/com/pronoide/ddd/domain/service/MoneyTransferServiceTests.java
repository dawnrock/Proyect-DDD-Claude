package com.pronoide.ddd.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.pronoide.ddd.domain.model.AccountHolder;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CustomerSegment;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.SameAccountTransferException;
import com.pronoide.ddd.domain.model.Transaction;
import com.pronoide.ddd.domain.model.TransactionType;

class MoneyTransferServiceTests {

    private static final String SOURCE_IBAN = "ES91 2100 0418 4502 0005 1332";
    private static final String TARGET_IBAN = "ES79 2100 0813 6101 2345 6789";

    private final MoneyTransferService service = new MoneyTransferService();

    private static CheckingAccount account(String iban, CustomerSegment segment, String initialDeposit) {
        return CheckingAccount.open(new AccountNumber(iban), AccountHolder.register("Titular", segment),
                MonetaryAmount.euros(initialDeposit));
    }

    @Nested
    @DisplayName("Transferencia aprobada")
    class ApprovedTransfer {

        @Test
        @DisplayName("Una transferencia aprobada resta el importe del origen y lo suma al destino")
        void una_transferencia_aprobada_mueve_el_importe_entre_las_cuentas() {
            CheckingAccount source = account(SOURCE_IBAN, CustomerSegment.RETAIL, "500.00");
            CheckingAccount target = account(TARGET_IBAN, CustomerSegment.RETAIL, "100.00");

            service.transfer(source, target, MonetaryAmount.euros("200.00"));

            assertThat(source.currentBalance()).isEqualTo(MonetaryAmount.euros("300.00"));
            assertThat(target.currentBalance()).isEqualTo(MonetaryAmount.euros("300.00"));
        }

        @Test
        @DisplayName("Una transferencia aprobada registra una retirada en el origen y un ingreso en el destino")
        void una_transferencia_aprobada_registra_retirada_e_ingreso() {
            CheckingAccount source = account(SOURCE_IBAN, CustomerSegment.RETAIL, "500.00");
            CheckingAccount target = account(TARGET_IBAN, CustomerSegment.RETAIL, "100.00");

            service.transfer(source, target, MonetaryAmount.euros("200.00"));

            Transaction lastInSource = source.transactions().get(source.transactions().size() - 1);
            Transaction lastInTarget = target.transactions().get(target.transactions().size() - 1);
            assertThat(source.transactions()).hasSize(2);
            assertThat(target.transactions()).hasSize(2);
            assertThat(lastInSource.type()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(lastInTarget.type()).isEqualTo(TransactionType.DEPOSIT);
            assertThat(lastInTarget.isApproved()).isTrue();
            assertThat(lastInTarget.amount()).isEqualTo(MonetaryAmount.euros("200.00"));
        }

        @Test
        @DisplayName("La transferencia devuelve la retirada aprobada del origen")
        void la_transferencia_devuelve_la_retirada_aprobada_del_origen() {
            CheckingAccount source = account(SOURCE_IBAN, CustomerSegment.RETAIL, "500.00");
            CheckingAccount target = account(TARGET_IBAN, CustomerSegment.RETAIL, "100.00");

            Transaction result = service.transfer(source, target, MonetaryAmount.euros("200.00"));

            assertThat(result.type()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(result.isApproved()).isTrue();
            assertThat(result.accountId()).isEqualTo(source.id());
            assertThat(result.amount()).isEqualTo(MonetaryAmount.euros("200.00"));
        }
    }

    @Nested
    @DisplayName("Transferencia rechazada por el saldo del origen (R18)")
    class RejectedTransfer {

        @ParameterizedTest
        @CsvSource({"RETAIL, 501.00", "HIGH_NET_WORTH, 1501.00"})
        @DisplayName("Si la retirada se rechaza no se ingresa nada en el destino y los saldos no cambian")
        void si_la_retirada_se_rechaza_no_se_ingresa_nada_en_el_destino(CustomerSegment segment, String amount) {
            CheckingAccount source = account(SOURCE_IBAN, segment, "500.00");
            CheckingAccount target = account(TARGET_IBAN, CustomerSegment.RETAIL, "100.00");

            Transaction result = service.transfer(source, target, MonetaryAmount.euros(amount));

            assertThat(result.isRejected()).isTrue();
            assertThat(result.type()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(source.currentBalance()).isEqualTo(MonetaryAmount.euros("500.00"));
            assertThat(target.currentBalance()).isEqualTo(MonetaryAmount.euros("100.00"));
            assertThat(target.transactions()).hasSize(1);
            assertThat(source.transactions()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Transferencia a la misma cuenta (R17)")
    class SameAccountTransfer {

        @Test
        @DisplayName("Transferir a la misma cuenta se rechaza sin registrar ningún movimiento")
        void transferir_a_la_misma_cuenta_se_rechaza_sin_registrar_movimientos() {
            CheckingAccount account = account(SOURCE_IBAN, CustomerSegment.RETAIL, "500.00");

            assertThatExceptionOfType(SameAccountTransferException.class)
                    .isThrownBy(() -> service.transfer(account, account, MonetaryAmount.euros("100.00")))
                    .withMessageContaining("ES9121000418450200051332");
            assertThat(account.transactions()).hasSize(1);
            assertThat(account.currentBalance()).isEqualTo(MonetaryAmount.euros("500.00"));
        }
    }
}
