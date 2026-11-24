package com.dawnrock.ddd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.support.TestAccounts;

/**
 * Contrato del puerto {@link CheckingAccountRepository}: lo que cumple cualquier implementación. Cada adaptador lo
 * hereda con un test que crea un repositorio vacío y añade solo lo propio de su tecnología. Compara las cuentas por
 * id, no por referencia: una implementación real devuelve otra instancia.
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
        void aSavedAccountIsFoundById() {
            CheckingAccount cuenta = TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open();

            repository.save(cuenta);

            assertThat(repository.findById(cuenta.id())).map(CheckingAccount::id).contains(cuenta.id());
        }

        @Test
        @DisplayName("Buscar una cuenta inexistente no devuelve nada")
        void findingAMissingAccountReturnsNothing() {
            assertThat(repository.findById(UUID.randomUUID())).isEmpty();
        }

        @Test
        @DisplayName("Guardar dos veces la misma cuenta no la duplica")
        void savingTheSameAccountTwiceDoesNotDuplicateIt() {
            CheckingAccount cuenta = TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open();

            repository.save(cuenta);
            repository.save(cuenta);

            assertThat(repository.findAll()).extracting(CheckingAccount::id).containsExactly(cuenta.id());
        }
    }

    @Nested
    @DisplayName("Argumentos obligatorios")
    class RequiredArguments {

        @Test
        @DisplayName("No se puede guardar una cuenta nula")
        void aNullAccountCannotBeSaved() {
            assertThatNullPointerException()
                    .isThrownBy(() -> repository.save(null));
        }

        @Test
        @DisplayName("No se puede buscar por un id nulo")
        void findingByANullIdIsRejected() {
            assertThatNullPointerException()
                    .isThrownBy(() -> repository.findById(null));
        }

        @Test
        @DisplayName("No se puede buscar por un número de cuenta nulo")
        void findingByANullAccountNumberIsRejected() {
            assertThatNullPointerException()
                    .isThrownBy(() -> repository.findByAccountNumber(null));
        }
    }

    @Nested
    @DisplayName("Buscar por número de cuenta")
    class FindByAccountNumber {

        @Test
        @DisplayName("Una cuenta guardada se recupera por su número aunque se escriba con espacios")
        void aSavedAccountIsFoundByItsNumberEvenWrittenWithSpaces() {
            CheckingAccount cuenta = TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open();
            repository.save(cuenta);

            assertThat(repository.findByAccountNumber(new AccountNumber("ES91 2100 0418 4502 0005 1332")))
                    .map(CheckingAccount::id).contains(cuenta.id());
        }

        @Test
        @DisplayName("Buscar un número de cuenta inexistente no devuelve nada")
        void findingAMissingAccountNumberReturnsNothing() {
            repository.save(TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open());

            assertThat(repository.findByAccountNumber(new AccountNumber("ES7620770024003102575766"))).isEmpty();
        }

        @Test
        @DisplayName("No se puede guardar otra cuenta con un número ya usado")
        void anotherAccountWithAnAlreadyUsedNumberCannotBeSaved() {
            repository.save(TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open());
            CheckingAccount otra = TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open();

            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> repository.save(otra))
                    .withMessageContaining("ES9121000418450200051332");
            assertThat(repository.findById(otra.id())).isEmpty();
        }

        @Test
        @DisplayName("Volver a guardar la misma cuenta no se considera un número duplicado")
        void savingTheSameAccountAgainIsNotADuplicateNumber() {
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
        void withNoSavedAccountsTheListIsEmpty() {
            assertThat(repository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Las cuentas se listan ordenadas por número de cuenta")
        void accountsAreListedSortedByAccountNumber() {
            CheckingAccount tercera =
                    repository.save(TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open());
            CheckingAccount primera =
                    repository.save(TestAccounts.anAccount().accountNumber("ES1000492352082414205416").open());
            CheckingAccount segunda =
                    repository.save(TestAccounts.anAccount().accountNumber("ES7620770024003102575766").open());

            assertThat(repository.findAll()).extracting(CheckingAccount::id)
                    .containsExactly(primera.id(), segunda.id(), tercera.id());
        }

        @Test
        @DisplayName("La lista de cuentas no puede modificarse desde fuera del repositorio")
        void theAccountListCannotBeModifiedOutsideTheRepository() {
            repository.save(TestAccounts.anAccount().accountNumber("ES9121000418450200051332").open());

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> repository.findAll().clear());
            assertThat(repository.findAll()).hasSize(1);
        }
    }

    @Test
    @DisplayName("Tras cambiar el número de una cuenta, el antiguo no se resuelve y el nuevo sí (R32)")
    void afterChangingAnAccountNumberTheOldOneNoLongerResolvesAndTheNewOneDoes() {
        AccountNumber old = new AccountNumber("ES9121000418450200051332");
        AccountNumber replacement = new AccountNumber("ES6699990001740000000001");
        CheckingAccount cuenta = repository.save(TestAccounts.anAccount().accountNumber(old.value()).open());
        cuenta.reassignAccountNumber(replacement);

        repository.save(cuenta);

        assertThat(repository.findByAccountNumber(old)).isEmpty();
        assertThat(repository.findByAccountNumber(replacement)).contains(cuenta);
        assertThat(repository.findAll()).containsExactly(cuenta);
    }
}
