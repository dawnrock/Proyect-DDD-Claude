package com.pronoide.ddd.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.domain.model.AccountHolder;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CustomerSegment;
import com.pronoide.ddd.domain.model.MonetaryAmount;

class InMemoryCheckingAccountRepositoryTests {

    private final InMemoryCheckingAccountRepository repository = new InMemoryCheckingAccountRepository();

    @Nested
    @DisplayName("Guardar y recuperar por id")
    class FindById {

        @Test
        @DisplayName("Una cuenta guardada se recupera por su id")
        void una_cuenta_guardada_se_recupera_por_su_id() {
            CheckingAccount cuenta = cuentaCon("ES9121000418450200051332");

            repository.save(cuenta);

            assertThat(repository.findById(cuenta.id())).containsSame(cuenta);
        }

        @Test
        @DisplayName("Buscar una cuenta inexistente no devuelve nada")
        void buscar_una_cuenta_inexistente_no_devuelve_nada() {
            assertThat(repository.findById(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("Guardar dos veces la misma cuenta no la duplica")
        void guardar_dos_veces_la_misma_cuenta_no_la_duplica() {
            CheckingAccount cuenta = cuentaCon("ES9121000418450200051332");

            repository.save(cuenta);
            cuenta.deposit(MonetaryAmount.euros("25.00"));
            repository.save(cuenta);

            assertThat(repository.findById(cuenta.id()))
                    .hasValueSatisfying(guardada ->
                            assertThat(guardada.currentBalance()).isEqualTo(MonetaryAmount.euros("125.00")));
        }
    }

    static CheckingAccount cuentaCon(String iban) {
        AccountHolder titular = AccountHolder.register("Ada Lovelace", CustomerSegment.RETAIL);
        return CheckingAccount.open(new AccountNumber(iban), titular, MonetaryAmount.euros("100.00"));
    }
}
