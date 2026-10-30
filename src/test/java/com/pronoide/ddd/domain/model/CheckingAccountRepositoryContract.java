package com.pronoide.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.support.TestAccounts;

/**
 * Contrato del puerto {@link CheckingAccountRepository}: lo que cumple cualquier implementación. Cada adaptador lo
 * hereda con un test que crea un repositorio vacío y añade solo lo propio de su tecnología.
 */
public abstract class CheckingAccountRepositoryContract {

    protected CheckingAccountRepository repository;

    /** Un repositorio vacío, nuevo en cada test. */
    protected abstract CheckingAccountRepository createRepository();

    @BeforeEach
    void createEmptyRepository() {
        repository = createRepository();
    }

    @Nested
    @DisplayName("Guardar y recuperar por id")
    class FindById {

        @Test
        @DisplayName("Una cuenta guardada se recupera por su id")
        void una_cuenta_guardada_se_recupera_por_su_id() {
            CheckingAccount cuenta = TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open();

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
            CheckingAccount cuenta = TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open();

            repository.save(cuenta);
            repository.save(cuenta);

            assertThat(repository.findAll()).containsExactly(cuenta);
        }
    }

    @Nested
    @DisplayName("Argumentos obligatorios")
    class RequiredArguments {

        @Test
        @DisplayName("No se puede guardar una cuenta nula")
        void no_se_puede_guardar_una_cuenta_nula() {
            assertThatNullPointerException()
                    .isThrownBy(() -> repository.save(null));
        }

        @Test
        @DisplayName("No se puede buscar por un id nulo")
        void no_se_puede_buscar_por_un_id_nulo() {
            assertThatNullPointerException()
                    .isThrownBy(() -> repository.findById(null));
        }

        @Test
        @DisplayName("No se puede buscar por un número de cuenta nulo")
        void no_se_puede_buscar_por_un_numero_de_cuenta_nulo() {
            assertThatNullPointerException()
                    .isThrownBy(() -> repository.findByAccountNumber(null));
        }
    }

    @Nested
    @DisplayName("Buscar por número de cuenta")
    class FindByAccountNumber {

        @Test
        @DisplayName("Una cuenta guardada se recupera por su número aunque se escriba con espacios")
        void una_cuenta_guardada_se_recupera_por_su_numero_aunque_se_escriba_con_espacios() {
            CheckingAccount cuenta = TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open();
            repository.save(cuenta);

            assertThat(repository.findByAccountNumber(new AccountNumber("ES91 2100 0418 4502 0005 1332")))
                    .containsSame(cuenta);
        }

        @Test
        @DisplayName("Buscar un número de cuenta inexistente no devuelve nada")
        void buscar_un_numero_de_cuenta_inexistente_no_devuelve_nada() {
            repository.save(TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open());

            assertThat(repository.findByAccountNumber(new AccountNumber("ES7620770024003102575766"))).isEmpty();
        }

        @Test
        @DisplayName("No se puede guardar otra cuenta con un número ya usado")
        void no_se_puede_guardar_otra_cuenta_con_un_numero_ya_usado() {
            repository.save(TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open());
            CheckingAccount otra = TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open();

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> repository.save(otra))
                    .withMessageContaining("ES9121000418450200051332");
            assertThat(repository.findById(otra.id())).isEmpty();
        }

        @Test
        @DisplayName("Volver a guardar la misma cuenta no se considera un número duplicado")
        void volver_a_guardar_la_misma_cuenta_no_se_considera_un_numero_duplicado() {
            CheckingAccount cuenta = TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open();
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
            CheckingAccount tercera =
                    repository.save(TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open());
            CheckingAccount primera =
                    repository.save(TestAccounts.anAccount().accountNumber("ES1000492352082414205416").open());
            CheckingAccount segunda =
                    repository.save(TestAccounts.anAccount().accountNumber("ES7620770024003102575766").open());

            assertThat(repository.findAll()).containsExactly(primera, segunda, tercera);
        }

        @Test
        @DisplayName("La lista de cuentas no puede modificarse desde fuera del repositorio")
        void la_lista_de_cuentas_no_puede_modificarse_desde_fuera_del_repositorio() {
            repository.save(TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open());

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> repository.findAll().clear());
            assertThat(repository.findAll()).hasSize(1);
        }
    }
}
