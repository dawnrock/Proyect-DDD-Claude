package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Las vistas de la aplicación como {@code record} (#219): sus valores por defecto y sus factorías se comportan como los
 * de Immutables.
 */
class ApplicationRecordsTests {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Nested
    @DisplayName("El detalle de una cuenta")
    class Details {

        @Test
        @DisplayName("sin documento, correo ni movimientos los deja vacíos")
        void withoutTheOptionalFieldsLeavesThemEmpty() {
            AccountDetails details = AccountDetails.of(ID, "ES8099990001450000000001", "Ada Lovelace", "RETAIL",
                    BigDecimal.TEN, "EUR");

            assertThat(details.holderTaxId()).isEmpty();
            assertThat(details.holderEmail()).isEmpty();
            assertThat(details.transactions()).isEmpty();
        }

        @Test
        @DisplayName("con documento y correo nulos los toma como vacíos, como el valor por defecto de Immutables")
        void takesNullTaxIdAndEmailAsEmpty() {
            AccountDetails details = new AccountDetails(ID, "ES8099990001450000000001", "Ada Lovelace", null, null,
                    "RETAIL", BigDecimal.TEN, "EUR", List.of());

            assertThat(details.holderTaxId()).isEmpty();
            assertThat(details.holderEmail()).isEmpty();
        }

        @Test
        @DisplayName("guarda una copia que no se puede modificar de sus movimientos")
        void keepsAnUnmodifiableCopyOfTheTransactions() {
            List<TransactionSummary> transactions = new ArrayList<>();
            transactions.add(TransactionSummary.of(ID, "DEPOSIT", BigDecimal.ONE, "EUR", Instant.EPOCH, "APPROVED"));
            AccountDetails details = new AccountDetails(ID, "ES8099990001450000000001", "Ada Lovelace", "", "",
                    "RETAIL", BigDecimal.TEN, "EUR", transactions);

            transactions.clear();

            assertThat(details.transactions()).hasSize(1);
        }

        @Test
        @DisplayName("no admite un titular nulo")
        void rejectsANullHolderName() {
            assertThatNullPointerException().isThrownBy(() -> AccountDetails.of(ID, "ES8099990001450000000001", null,
                    "RETAIL", BigDecimal.TEN, "EUR")).withMessage("holderName");
        }
    }

    @Nested
    @DisplayName("Quien pide un caso de uso")
    class Callers {

        @Test
        @DisplayName("es un cliente o un administrador según su factoría, y system es un administrador")
        void isACustomerOrAnAdministratorByItsFactory() {
            assertThat(Caller.customer("ada")).isEqualTo(new Caller("ada", false));
            assertThat(Caller.administrator("admin")).isEqualTo(new Caller("admin", true));
            assertThat(Caller.system()).isEqualTo(new Caller("system", true));
        }
    }
}
