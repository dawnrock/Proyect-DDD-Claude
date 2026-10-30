package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.pronoide.ddd.domain.model.CheckingAccountRepository;
import com.pronoide.ddd.support.TestAccounts;

/**
 * Reglas de propiedad de las cuentas (#50): un administrador accede a todo; un usuario solo a sus cuentas, y una ajena
 * se trata como inexistente. Repositorios en memoria reales.
 */
class CheckingAccountServiceOwnershipTests {

    private static final String ADA_IBAN = "ES9121000418450200051332";
    private static final String GRACE_IBAN = "ES7921000813610123456789";

    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();
    private static final Caller ADA = Caller.builder().username("ada").admin(false).build();
    private static final Caller GRACE = Caller.builder().username("grace").admin(false).build();

    private CheckingAccountRepository accounts;
    private AccountOwnershipRepository owners;
    private CheckingAccountService service;

    @BeforeEach
    void setUp() {
        accounts = PersistenceTestAdapters.accounts();
        owners = PersistenceTestAdapters.ownership();
        service = new CheckingAccountService(accounts, owners);
    }

    private static OpenAccountCommand.Builder command(String accountNumber, String owner) {
        return OpenAccountCommand.builder()
                .accountNumber(accountNumber)
                .holderName("Titular")
                .segment("RETAIL")
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
        void un_administrador_abre_la_cuenta_y_queda_registrada_a_nombre_del_propietario() {
            AccountSummary summary = service.openAccount(ADMIN, command(ADA_IBAN, "ada").build());

            assertThat(summary.accountNumber()).isEqualTo(ADA_IBAN);
            assertThat(owners.ownerOf(summary.id())).contains("ada");
        }

        @Test
        @DisplayName("Un usuario que no es administrador no puede abrir cuentas y no se guarda nada")
        void un_usuario_que_no_es_administrador_no_puede_abrir_cuentas() {
            OpenAccountCommand open = command(ADA_IBAN, "ada").build();

            assertThatExceptionOfType(ForbiddenOperationException.class)
                    .isThrownBy(() -> service.openAccount(ADA, open));
            assertThat(accounts.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Abrir una cuenta sin usuario propietario lanza IllegalArgumentException y no se guarda nada")
        void abrir_una_cuenta_sin_usuario_propietario_lanza_illegal_argument_exception() {
            OpenAccountCommand withoutOwner = command(ADA_IBAN, "").build();

            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> service.openAccount(ADMIN, withoutOwner))
                    .withMessageContaining("propietario");
            assertThat(accounts.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Un nombre de usuario inválido lo rechaza el command al construirse")
        void un_nombre_de_usuario_invalido_lo_rechaza_el_command() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> command(ADA_IBAN, "Ada Lovelace").build());
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
            adas = service.openAccount(ADMIN, command(ADA_IBAN, "ada").build());
            graces = service.openAccount(ADMIN, command(GRACE_IBAN, "grace").build());
            orphan = openWithoutOwner("ES6000491500051234567892");
        }

        @Test
        @DisplayName("Un administrador lista todas las cuentas, también las que no tienen propietario")
        void un_administrador_lista_todas_las_cuentas() {
            assertThat(service.listAccounts(ADMIN)).extracting(AccountSummary::id)
                    .containsExactlyInAnyOrder(adas.id(), graces.id(), orphan.id());
        }

        @Test
        @DisplayName("Un usuario lista solo sus cuentas, ordenadas por número de cuenta")
        void un_usuario_lista_solo_sus_cuentas() {
            AccountSummary second = service.openAccount(ADMIN, command("ES1000000000000000000001", "ada").build());

            assertThat(service.listAccounts(ADA)).extracting(AccountSummary::accountNumber)
                    .containsExactly(second.accountNumber(), ADA_IBAN);
        }

        @Test
        @DisplayName("Un usuario sin cuentas obtiene una lista vacía")
        void un_usuario_sin_cuentas_obtiene_una_lista_vacia() {
            assertThat(service.listAccounts(Caller.builder().username("alan").admin(false).build())).isEmpty();
        }

        @Test
        @DisplayName("Un administrador consulta cualquier cuenta")
        void un_administrador_consulta_cualquier_cuenta() {
            assertThat(service.findAccount(ADMIN, graces.id()).accountNumber()).isEqualTo(GRACE_IBAN);
            assertThat(service.findAccount(ADMIN, orphan.id()).id()).isEqualTo(orphan.id());
        }

        @Test
        @DisplayName("Un usuario consulta su cuenta")
        void un_usuario_consulta_su_cuenta() {
            assertThat(service.findAccount(ADA, adas.id()).accountNumber()).isEqualTo(ADA_IBAN);
        }

        @Test
        @DisplayName("Una cuenta ajena no se encuentra: AccountNotFoundException")
        void una_cuenta_ajena_no_se_encuentra() {
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.findAccount(ADA, graces.id()))
                    .withMessageContaining(graces.id().toString());
        }

        @Test
        @DisplayName("Una cuenta sin propietario no se encuentra para un usuario")
        void una_cuenta_sin_propietario_no_se_encuentra_para_un_usuario() {
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.findAccount(ADA, orphan.id()));
        }

        @Test
        @DisplayName("Una cuenta inexistente no se encuentra, ni para un administrador")
        void una_cuenta_inexistente_no_se_encuentra() {
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
            adas = service.openAccount(ADMIN, command(ADA_IBAN, "ada").build());
            graces = service.openAccount(ADMIN, command(GRACE_IBAN, "grace").build());
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

        private BigDecimal balanceOf(AccountSummary account) {
            return service.findAccount(ADMIN, account.id()).balance();
        }

        @Test
        @DisplayName("Un usuario ingresa en su cuenta")
        void un_usuario_ingresa_en_su_cuenta() {
            assertThat(service.deposit(ADA, deposit(adas)).status()).isEqualTo("APPROVED");
            assertThat(balanceOf(adas)).isEqualByComparingTo("600.00");
        }

        @Test
        @DisplayName("Un usuario retira de su cuenta")
        void un_usuario_retira_de_su_cuenta() {
            assertThat(service.withdraw(ADA, withdraw(adas)).status()).isEqualTo("APPROVED");
            assertThat(balanceOf(adas)).isEqualByComparingTo("400.00");
        }

        @Test
        @DisplayName("Un administrador opera en cualquier cuenta, también sin propietario")
        void un_administrador_opera_en_cualquier_cuenta() {
            service.deposit(ADMIN, deposit(graces));
            service.withdraw(ADMIN, withdraw(orphan));

            assertThat(balanceOf(graces)).isEqualByComparingTo("600.00");
            assertThat(balanceOf(orphan)).isEqualByComparingTo("400.00");
        }

        @Test
        @DisplayName("Ingresar en una cuenta ajena lanza AccountNotFoundException y no cambia el saldo")
        void ingresar_en_una_cuenta_ajena_lanza_account_not_found_exception() {
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.deposit(ADA, deposit(graces)));
            assertThat(balanceOf(graces)).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("Retirar de una cuenta ajena o sin propietario lanza AccountNotFoundException")
        void retirar_de_una_cuenta_ajena_lanza_account_not_found_exception() {
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.withdraw(ADA, withdraw(graces)));
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.withdraw(ADA, withdraw(orphan)));
            assertThat(balanceOf(graces)).isEqualByComparingTo("500.00");
            assertThat(balanceOf(orphan)).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("Un usuario transfiere de su cuenta al IBAN de otro cliente, escrito con espacios y minúsculas")
        void un_usuario_transfiere_al_iban_de_otro_cliente() {
            TransferSummary summary = service.transfer(ADA, transfer(adas, "es79 2100 0813 6101 2345 6789"));

            assertThat(summary.status()).isEqualTo("APPROVED");
            assertThat(summary.targetAccountNumber()).isEqualTo(GRACE_IBAN);
            assertThat(balanceOf(adas)).isEqualByComparingTo("400.00");
            assertThat(balanceOf(graces)).isEqualByComparingTo("600.00");
        }

        @Test
        @DisplayName("Un administrador transfiere desde cualquier cuenta")
        void un_administrador_transfiere_desde_cualquier_cuenta() {
            assertThat(service.transfer(ADMIN, transfer(graces, ADA_IBAN)).status()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("Transferir desde una cuenta ajena lanza AccountNotFoundException y no mueve dinero")
        void transferir_desde_una_cuenta_ajena_lanza_account_not_found_exception() {
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.transfer(ADA, transfer(graces, ADA_IBAN)));
            assertThat(balanceOf(adas)).isEqualByComparingTo("500.00");
            assertThat(balanceOf(graces)).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("Transferir a un IBAN inexistente lanza TargetAccountNotFoundException y no mueve dinero")
        void transferir_a_un_iban_inexistente_lanza_target_account_not_found_exception() {
            assertThatExceptionOfType(TargetAccountNotFoundException.class)
                    .isThrownBy(() -> service.transfer(ADA, transfer(adas, "ES1000000000000000000001")))
                    .withMessageContaining("ES1000000000000000000001");
            assertThat(balanceOf(adas)).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("Con origen ajeno y destino inexistente gana el origen: AccountNotFoundException")
        void con_origen_ajeno_y_destino_inexistente_gana_el_origen() {
            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.transfer(ADA, transfer(graces, "ES1000000000000000000001")))
                    .isNotInstanceOf(TargetAccountNotFoundException.class);
        }

        @Test
        @DisplayName("Transferir a la propia cuenta sigue siendo una regla del dominio")
        void transferir_a_la_propia_cuenta_sigue_siendo_una_regla_del_dominio() {
            assertThatExceptionOfType(com.pronoide.ddd.domain.model.SameAccountTransferException.class)
                    .isThrownBy(() -> service.transfer(ADA, transfer(adas, ADA_IBAN)));
        }
    }
}
