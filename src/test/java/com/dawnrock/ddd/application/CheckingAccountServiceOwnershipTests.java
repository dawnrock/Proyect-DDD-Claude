package com.dawnrock.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.domain.model.CheckingAccountRepository;
import com.dawnrock.ddd.support.TestAccounts;
import com.dawnrock.ddd.support.ApplicationServicesFixture;

/**
 * Reglas de propiedad de las cuentas (#50): un administrador accede a todo; un usuario solo a sus cuentas, y una ajena
 * se trata como inexistente. Repositorios en memoria reales.
 */
class CheckingAccountServiceOwnershipTests {

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();
    private static final Caller ADA = Caller.builder().username("ada").admin(false).build();
    private static final Caller GRACE = Caller.builder().username("grace").admin(false).build();

    private CheckingAccountRepository accounts;
    private AccountOwnershipRepository owners;
    private CheckingAccountService service;
    private AccountOpeningCommands opening;
    private MoneyMovementCommands movements;

    @BeforeEach
    void setUp() {
        accounts = PersistenceTestAdapters.accounts();
        owners = PersistenceTestAdapters.ownership();
        ApplicationServicesFixture fixture = ApplicationServicesFixture.builder().accounts(accounts).ownership(owners).build();
        service = fixture.service();
        opening = fixture.accountOpeningService();
        movements = fixture.moneyMovementService();
    }

    /** Un cliente por propietario: cada usuario es de un único cliente (R30). */
    private static OpenAccountCommand.Builder command(String owner) {
        return OpenAccountCommand.builder()
                .holderName("Titular")
                .holderTaxId("grace".equals(owner) ? "87654321X" : "12345678Z")
                .holderEmail("titular@example.com")
                .initialDeposit(new BigDecimal("500"))
                .ownerUsername(owner);
    }

    /** Cuenta guardada directamente en el repositorio, sin propietario registrado: solo la ve un administrador. */
    private AccountSummary openWithoutOwner(String accountNumber) {
        UUID id = accounts.save(TestAccounts.anAccount().accountNumber(accountNumber).initialDeposit("500.00").open())
                .id();
        return service.listAccounts(ADMIN).stream()
                .filter(account -> account.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("Abrir cuenta con propietario")
    class OpenAccount {

        @Test
        @DisplayName("Un administrador abre la cuenta y queda registrada a nombre del usuario propietario")
        void anAdministratorOpensTheAccountInTheOwnersName() {
            AccountSummary summary = opening.openAccount(ADMIN, command("ada").build());

            assertThat(summary.accountNumber()).startsWith("ES").hasSize(24);
            assertThat(owners.ownerOf(summary.id())).contains("ada");
        }

        @Test
        @DisplayName("Un usuario que no es administrador no puede abrir cuentas y no se guarda nada")
        void aNonAdministratorCannotOpenAccounts() {
            OpenAccountCommand open = command("ada").build();

            assertThatExceptionOfType(ForbiddenOperationException.class)
                    .isThrownBy(() -> opening.openAccount(ADA, open));
            assertThat(accounts.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Abrir una cuenta sin usuario propietario lanza IllegalArgumentException y no se guarda nada")
        void openingAnAccountWithoutAnOwnerThrowsIllegalArgumentException() {
            OpenAccountCommand withoutOwner = command("").build();

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> opening.openAccount(ADMIN, withoutOwner))
                    .withMessageContaining("usuario del titular es obligatorio");
            assertThat(accounts.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Un nombre de usuario inválido lo rechaza el command al construirse")
        void anInvalidUsernameIsRejectedByTheCommand() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> command("Ada Lovelace").build());
        }
    }

    @Nested
    @DisplayName("Listar y consultar solo las cuentas propias")
    class ListAndFind {

        private AccountSummary adas;
        private AccountSummary graces;
        private AccountSummary orphan;

        @BeforeEach
        void openAccounts() {
            adas = opening.openAccount(ADMIN, command("ada").build());
            graces = opening.openAccount(ADMIN, command("grace").build());
            orphan = openWithoutOwner("ES6000491500051234567892");
        }

        @Test
        @DisplayName("Un administrador lista todas las cuentas, también las que no tienen propietario")
        void anAdministratorListsAllAccounts() {
            assertThat(service.listAccounts(ADMIN)).extracting(AccountSummary::id)
                    .containsExactlyInAnyOrder(adas.id(), graces.id(), orphan.id());
        }

        @Test
        @DisplayName("Un usuario lista solo sus cuentas, ordenadas por número de cuenta")
        void aUserListsOnlyTheirAccounts() {
            AccountSummary second = opening.openAccount(ADMIN, command("ada").build());

            assertThat(service.listAccounts(ADA)).extracting(AccountSummary::accountNumber)
                    .containsExactly(adas.accountNumber(), second.accountNumber());
        }

        @Test
        @DisplayName("Un usuario sin cuentas obtiene una lista vacía")
        void aUserWithoutAccountsGetsAnEmptyList() {
            assertThat(service.listAccounts(Caller.builder().username("alan").admin(false).build())).isEmpty();
        }

        @Test
        @DisplayName("Un administrador consulta cualquier cuenta")
        void anAdministratorViewsAnyAccount() {
            assertThat(service.findAccount(ADMIN, graces.id()).accountNumber()).isEqualTo(graces.accountNumber());
            assertThat(service.findAccount(ADMIN, orphan.id()).id()).isEqualTo(orphan.id());
        }

        @Test
        @DisplayName("Un usuario consulta su cuenta")
        void aUserViewsTheirAccount() {
            assertThat(service.findAccount(ADA, adas.id()).accountNumber()).isEqualTo(adas.accountNumber());
        }

        @Test
        @DisplayName("Una cuenta ajena no se encuentra: AccountNotFoundException")
        void anotherUsersAccountIsNotFound() {
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.findAccount(ADA, graces.id()))
                    .withMessageContaining(graces.id().toString());
        }

        @Test
        @DisplayName("Una cuenta sin propietario no se encuentra para un usuario")
        void anAccountWithoutOwnerIsNotFoundForAUser() {
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.findAccount(ADA, orphan.id()));
        }

        @Test
        @DisplayName("Una cuenta inexistente no se encuentra, ni para un administrador")
        void aMissingAccountIsNotFound() {
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.findAccount(ADMIN, UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("Operar solo las cuentas propias")
    class Operate {

        private AccountSummary adas;
        private AccountSummary graces;
        private AccountSummary orphan;

        @BeforeEach
        void openAccounts() {
            adas = opening.openAccount(ADMIN, command("ada").build());
            graces = opening.openAccount(ADMIN, command("grace").build());
            orphan = openWithoutOwner("ES6000491500051234567892");
        }

        private DepositCommand deposit(AccountSummary account) {
            return DepositCommand.builder().accountId(account.id()).amount(new BigDecimal("100")).build();
        }

        private WithdrawCommand withdraw(AccountSummary account) {
            return WithdrawCommand.builder().accountId(account.id()).amount(new BigDecimal("100")).build();
        }

        private TransferCommand transfer(AccountSummary source, String targetNumber) {
            return TransferCommand.builder()
                    .sourceAccountId(source.id())
                    .targetAccountNumber(targetNumber)
                    .amount(new BigDecimal("100"))
                    .build();
        }

        /** El IBAN en minúsculas y en grupos de cuatro, como lo escribiría una persona. */
        private String spaced(String iban) {
            return iban.toLowerCase().replaceAll("(.{4})", "$1 ").trim();
        }

        private BigDecimal balanceOf(AccountSummary account) {
            return service.findAccount(ADMIN, account.id()).balance();
        }

        @Test
        @DisplayName("El llamador de sistema (la demo) ingresa, retira y transfiere en cuentas de otros usuarios")
        void theSystemCallerOperatesOtherUsersAccounts() {
            Caller system = CallerDef.system();

            movements.deposit(system, deposit(adas));
            movements.withdraw(system, withdraw(graces));
            movements.transfer(system, transfer(adas, graces.accountNumber()));

            assertThat(balanceOf(adas)).isEqualByComparingTo("500.00");
            assertThat(balanceOf(graces)).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("Un usuario ingresa en su cuenta")
        void aUserDepositsIntoTheirAccount() {
            assertThat(movements.deposit(ADA, deposit(adas)).status()).isEqualTo("APPROVED");
            assertThat(balanceOf(adas)).isEqualByComparingTo("600.00");
        }

        @Test
        @DisplayName("Un usuario retira de su cuenta")
        void aUserWithdrawsFromTheirAccount() {
            assertThat(movements.withdraw(ADA, withdraw(adas)).status()).isEqualTo("APPROVED");
            assertThat(balanceOf(adas)).isEqualByComparingTo("400.00");
        }

        @Test
        @DisplayName("Un administrador opera en cualquier cuenta, también sin propietario")
        void anAdministratorOperatesOnAnyAccount() {
            movements.deposit(ADMIN, deposit(graces));
            movements.withdraw(ADMIN, withdraw(orphan));

            assertThat(balanceOf(graces)).isEqualByComparingTo("600.00");
            assertThat(balanceOf(orphan)).isEqualByComparingTo("400.00");
        }

        @Test
        @DisplayName("Ingresar en una cuenta ajena lanza AccountNotFoundException y no cambia el saldo")
        void depositingIntoAnotherUsersAccountThrowsAccountNotFoundException() {
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> movements.deposit(ADA, deposit(graces)));
            assertThat(balanceOf(graces)).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("Retirar de una cuenta ajena o sin propietario lanza AccountNotFoundException")
        void withdrawingFromAnotherUsersAccountThrowsAccountNotFoundException() {
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> movements.withdraw(ADA, withdraw(graces)));
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> movements.withdraw(ADA, withdraw(orphan)));
            assertThat(balanceOf(graces)).isEqualByComparingTo("500.00");
            assertThat(balanceOf(orphan)).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("Un usuario transfiere de su cuenta al IBAN de otro cliente, escrito con espacios y minúsculas")
        void aUserTransfersToAnotherCustomersIban() {
            TransferSummary summary = movements.transfer(ADA, transfer(adas, spaced(graces.accountNumber())));

            assertThat(summary.status()).isEqualTo("APPROVED");
            assertThat(summary.targetAccountNumber()).isEqualTo(graces.accountNumber());
            assertThat(balanceOf(adas)).isEqualByComparingTo("400.00");
            assertThat(balanceOf(graces)).isEqualByComparingTo("600.00");
        }

        @Test
        @DisplayName("Un administrador transfiere desde cualquier cuenta")
        void anAdministratorTransfersFromAnyAccount() {
            assertThat(movements.transfer(ADMIN, transfer(graces, adas.accountNumber())).status()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("Transferir desde una cuenta ajena lanza AccountNotFoundException y no mueve dinero")
        void transferringFromAnotherUsersAccountThrowsAccountNotFoundException() {
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> movements.transfer(ADA, transfer(graces, adas.accountNumber())));
            assertThat(balanceOf(adas)).isEqualByComparingTo("500.00");
            assertThat(balanceOf(graces)).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("Transferir a un IBAN inexistente lanza TargetAccountNotFoundException y no mueve dinero")
        void transferringToAMissingIbanThrowsTargetAccountNotFoundException() {
            assertThatExceptionOfType(TargetAccountNotFoundException.class)
                    .isThrownBy(() -> movements.transfer(ADA, transfer(adas, "ES1000000000000000000001")))
                    .withMessageContaining("ES1000000000000000000001");
            assertThat(balanceOf(adas)).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("Con origen ajeno y destino inexistente gana el origen: AccountNotFoundException")
        void withAForeignSourceAndAMissingTargetTheSourceErrorWins() {
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> movements.transfer(ADA, transfer(graces, "ES1000000000000000000001")))
                    .isNotInstanceOf(TargetAccountNotFoundException.class);
        }

        @Test
        @DisplayName("Transferir a la propia cuenta sigue siendo una regla del dominio")
        void transferringToTheSameAccountIsStillADomainRule() {
            assertThatExceptionOfType(com.dawnrock.ddd.domain.model.SameAccountTransferException.class)
                    .isThrownBy(() -> movements.transfer(ADA, transfer(adas, adas.accountNumber())));
        }
    }
}
