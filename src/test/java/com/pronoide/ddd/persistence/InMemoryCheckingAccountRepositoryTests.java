package com.pronoide.ddd.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

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

    @Nested
    @DisplayName("Buscar por número de cuenta")
    class FindByAccountNumber {

        @Test
        @DisplayName("Una cuenta guardada se recupera por su número aunque se escriba con espacios")
        void una_cuenta_guardada_se_recupera_por_su_numero_aunque_se_escriba_con_espacios() {
            CheckingAccount cuenta = cuentaCon("ES9121000418450200051332");
            repository.save(cuenta);

            assertThat(repository.findByAccountNumber(new AccountNumber("ES91 2100 0418 4502 0005 1332")))
                    .containsSame(cuenta);
        }

        @Test
        @DisplayName("Buscar un número de cuenta inexistente no devuelve nada")
        void buscar_un_numero_de_cuenta_inexistente_no_devuelve_nada() {
            repository.save(cuentaCon("ES9121000418450200051332"));

            assertThat(repository.findByAccountNumber(new AccountNumber("ES7620770024003102575766"))).isEmpty();
        }

        @Test
        @DisplayName("No se puede guardar otra cuenta con un número ya usado")
        void no_se_puede_guardar_otra_cuenta_con_un_numero_ya_usado() {
            repository.save(cuentaCon("ES9121000418450200051332"));
            CheckingAccount otra = cuentaCon("ES9121000418450200051332");

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> repository.save(otra))
                    .withMessageContaining("ES9121000418450200051332");
            assertThat(repository.findById(otra.id())).isEmpty();
        }

        @Test
        @DisplayName("Volver a guardar la misma cuenta no se considera un número duplicado")
        void volver_a_guardar_la_misma_cuenta_no_se_considera_un_numero_duplicado() {
            CheckingAccount cuenta = cuentaCon("ES9121000418450200051332");
            repository.save(cuenta);

            assertThatNoException().isThrownBy(() -> repository.save(cuenta));
        }
    }

    @Nested
    @DisplayName("Listar todas las cuentas")
    class FindAll {

        @Test
        @DisplayName("Sin cuentas guardadas la lista está vacía")
        void sin_cuentas_guardadas_la_lista_esta_vacia() {
            assertThat(repository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Las cuentas se listan ordenadas por número de cuenta")
        void las_cuentas_se_listan_ordenadas_por_numero_de_cuenta() {
            CheckingAccount tercera = repository.save(cuentaCon("ES9121000418450200051332"));
            CheckingAccount primera = repository.save(cuentaCon("ES1000492352082414205416"));
            CheckingAccount segunda = repository.save(cuentaCon("ES7620770024003102575766"));

            assertThat(repository.findAll()).containsExactly(primera, segunda, tercera);
        }

        @Test
        @DisplayName("La lista de cuentas no puede modificarse desde fuera del repositorio")
        void la_lista_de_cuentas_no_puede_modificarse_desde_fuera_del_repositorio() {
            repository.save(cuentaCon("ES9121000418450200051332"));

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> repository.findAll().clear());
            assertThat(repository.findAll()).hasSize(1);
        }
    }

    static CheckingAccount cuentaCon(String iban) {
        AccountHolder titular = AccountHolder.register("Ada Lovelace", CustomerSegment.RETAIL);
        return CheckingAccount.open(new AccountNumber(iban), titular, MonetaryAmount.euros("100.00"));
    }
}
