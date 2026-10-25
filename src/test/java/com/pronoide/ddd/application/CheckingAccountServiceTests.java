package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CurrencyMismatchException;
import com.pronoide.ddd.domain.model.DuplicateAccountNumberException;
import com.pronoide.ddd.domain.model.InsufficientInitialDepositException;
import com.pronoide.ddd.domain.model.SameAccountTransferException;
import com.pronoide.ddd.persistence.InMemoryAccountOwnershipRepository;
import com.pronoide.ddd.persistence.InMemoryCheckingAccountRepository;

/** Casos de uso con el repositorio en memoria real: es concreto y no hace falta simularlo. */
class CheckingAccountServiceTests {

    private static final String IBAN = "ES9121000418450200051332";
    private static final String OTHER_IBAN = "ES7921000813610123456789";

    /** Los casos de uso se piden como administrador; la propiedad se prueba en CheckingAccountServiceOwnershipTests. */
    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();

    private InMemoryCheckingAccountRepository repository;
    private CheckingAccountService service;

    @BeforeEach
    void setUp() {
        repository = spy(new InMemoryCheckingAccountRepository());
        service = new CheckingAccountService(repository, new InMemoryAccountOwnershipRepository());
    }

    private static OpenAccountCommand.Builder command(String accountNumber) {
        return OpenAccountCommand.builder()
                .accountNumber(accountNumber)
                .holderName("Ada Lovelace")
                .segment("RETAIL")
                .initialDeposit(new BigDecimal("100"))
                .ownerUsername("ada");
    }

    @Nested
    @DisplayName("Abrir cuenta")
    class OpenAccount {

        @Test
        @DisplayName("Un depósito válido abre la cuenta, la guarda y devuelve su resumen")
        void un_deposito_valido_abre_la_cuenta_la_guarda_y_devuelve_su_resumen() {
            AccountSummary summary = service.openAccount(ADMIN, command("es91 2100 0418 4502 0005 1332").build());

            assertThat(summary.accountNumber()).isEqualTo(IBAN);
            assertThat(summary.holderName()).isEqualTo("Ada Lovelace");
            assertThat(summary.balance()).isEqualByComparingTo("100.00");
            assertThat(summary.balance().scale()).isEqualTo(2);
            assertThat(summary.currency()).isEqualTo("EUR");
            assertThat(repository.findById(summary.id())).isPresent();
        }

        @Test
        @DisplayName("Un depósito por debajo del mínimo se rechaza y no se guarda")
        void un_deposito_por_debajo_del_minimo_se_rechaza_y_no_se_guarda() {
            OpenAccountCommand tooLow = command(IBAN).initialDeposit(new BigDecimal("49.99")).build();

            assertThatExceptionOfType(InsufficientInitialDepositException.class)
                    .isThrownBy(() -> service.openAccount(ADMIN, tooLow));
            assertThat(repository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Un depósito en otra divisa se rechaza y no se guarda")
        void un_deposito_en_otra_divisa_se_rechaza_y_no_se_guarda() {
            OpenAccountCommand inDollars = command(IBAN).currency("USD").build();

            assertThatExceptionOfType(CurrencyMismatchException.class)
                    .isThrownBy(() -> service.openAccount(ADMIN, inDollars));
            assertThat(repository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Un número de cuenta en uso se rechaza aunque venga con espacios o en minúsculas")
        void un_numero_de_cuenta_en_uso_se_rechaza_aunque_venga_con_espacios_o_en_minusculas() {
            service.openAccount(ADMIN, command(IBAN).build());
            OpenAccountCommand duplicate = command("es91 2100 0418 4502 0005 1332").holderName("Grace Hopper").build();

            assertThatExceptionOfType(DuplicateAccountNumberException.class)
                    .isThrownBy(() -> service.openAccount(ADMIN, duplicate))
                    .withMessageContaining(IBAN);
            assertThat(repository.findAll()).hasSize(1);
            assertThat(repository.findByAccountNumber(new AccountNumber(IBAN)).orElseThrow().primaryHolder().fullName())
                    .isEqualTo("Ada Lovelace");
        }

        @Test
        @DisplayName("Un cliente de alto patrimonio abre su cuenta con su segmento")
        void un_cliente_de_alto_patrimonio_abre_su_cuenta_con_su_segmento() {
            AccountSummary summary = service.openAccount(ADMIN, command(IBAN).segment("HIGH_NET_WORTH").build());

            assertThat(repository.findById(summary.id()).orElseThrow().primaryHolder().isHighNetWorth()).isTrue();
        }
    }

    @Nested
    @DisplayName("Consultar cuenta")
    class FindAccount {

        @Test
        @DisplayName("Una cuenta existente se devuelve con el depósito inicial entre sus movimientos")
        void una_cuenta_existente_se_devuelve_con_el_deposito_inicial_entre_sus_movimientos() {
            AccountSummary opened = service.openAccount(ADMIN, command(IBAN).segment("HIGH_NET_WORTH").build());

            AccountDetails details = service.findAccount(ADMIN, opened.id());

            assertThat(details.id()).isEqualTo(opened.id());
            assertThat(details.accountNumber()).isEqualTo(IBAN);
            assertThat(details.holderName()).isEqualTo("Ada Lovelace");
            assertThat(details.segment()).isEqualTo("HIGH_NET_WORTH");
            assertThat(details.balance()).isEqualByComparingTo("100.00");
            assertThat(details.currency()).isEqualTo("EUR");
            assertThat(details.transactions()).singleElement().satisfies(transaction -> {
                assertThat(transaction.type()).isEqualTo("DEPOSIT");
                assertThat(transaction.amount()).isEqualByComparingTo("100.00");
                assertThat(transaction.currency()).isEqualTo("EUR");
                assertThat(transaction.status()).isEqualTo("APPROVED");
                assertThat(transaction.occurredOn()).isNotNull();
            });
        }

        @Test
        @DisplayName("Una cuenta desconocida lanza AccountNotFoundException")
        void una_cuenta_desconocida_lanza_account_not_found_exception() {
            UUID unknown = UUID.randomUUID();

            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.findAccount(ADMIN, unknown))
                    .withMessageContaining(unknown.toString());
        }
    }

    @Nested
    @DisplayName("Listar cuentas")
    class ListAccounts {

        @Test
        @DisplayName("Sin cuentas devuelve una lista vacía")
        void sin_cuentas_devuelve_una_lista_vacia() {
            assertThat(service.listAccounts(ADMIN)).isEmpty();
        }

        @Test
        @DisplayName("Varias cuentas se devuelven ordenadas por número de cuenta")
        void varias_cuentas_se_devuelven_ordenadas_por_numero_de_cuenta() {
            service.openAccount(ADMIN, command(OTHER_IBAN).holderName("Grace Hopper").build());
            service.openAccount(ADMIN, command(IBAN).build());

            assertThat(service.listAccounts(ADMIN))
                    .extracting(AccountSummary::accountNumber, AccountSummary::holderName)
                    .containsExactly(
                            tuple(OTHER_IBAN, "Grace Hopper"),
                            tuple(IBAN, "Ada Lovelace"));
        }
    }

    @Nested
    @DisplayName("Ingresar")
    class Deposit {

        @Test
        @DisplayName("Un ingreso aprobado actualiza el saldo y aparece entre los movimientos")
        void un_ingreso_aprobado_actualiza_el_saldo_y_aparece_entre_los_movimientos() {
            UUID id = service.openAccount(ADMIN, command(IBAN).build()).id();

            TransactionSummary transaction = service.deposit(ADMIN, 
                    DepositCommand.builder().accountId(id).amount(new BigDecimal("25.50")).build());

            assertThat(transaction.type()).isEqualTo("DEPOSIT");
            assertThat(transaction.status()).isEqualTo("APPROVED");
            assertThat(transaction.amount()).isEqualByComparingTo("25.50");
            assertThat(transaction.currency()).isEqualTo("EUR");
            AccountDetails details = service.findAccount(ADMIN, id);
            assertThat(details.balance()).isEqualByComparingTo("125.50");
            assertThat(details.transactions()).extracting(TransactionSummary::id).last().isEqualTo(transaction.id());
            assertThat(details.transactions()).hasSize(2);
        }

        @Test
        @DisplayName("Una cuenta desconocida lanza AccountNotFoundException")
        void una_cuenta_desconocida_lanza_account_not_found_exception() {
            UUID unknown = UUID.randomUUID();
            DepositCommand command = DepositCommand.builder().accountId(unknown).amount(BigDecimal.TEN).build();

            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.deposit(ADMIN, command))
                    .withMessageContaining(unknown.toString());
        }

        @Test
        @DisplayName("Un ingreso en otra divisa lanza CurrencyMismatchException y no cambia el saldo")
        void un_ingreso_en_otra_divisa_lanza_currency_mismatch_exception_y_no_cambia_el_saldo() {
            UUID id = service.openAccount(ADMIN, command(IBAN).build()).id();
            DepositCommand dollars =
                    DepositCommand.builder().accountId(id).amount(BigDecimal.TEN).currency("USD").build();

            assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> service.deposit(ADMIN, dollars));

            AccountDetails details = service.findAccount(ADMIN, id);
            assertThat(details.balance()).isEqualByComparingTo("100.00");
            assertThat(details.transactions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Retirar")
    class Withdraw {

        @ParameterizedTest(name = "{0} con 100.00 retira {1}: {2}, saldo {3}")
        @DisplayName("Las retiradas en el límite del descubierto se aprueban o se rechazan según el segmento")
        @CsvSource({
                "RETAIL, 100.00, APPROVED, 0.00",
                "RETAIL, 100.01, REJECTED, 100.00",
                "HIGH_NET_WORTH, 1100.00, APPROVED, -1000.00",
                "HIGH_NET_WORTH, 1100.01, REJECTED, 100.00"
        })
        void las_retiradas_en_el_limite_del_descubierto_dependen_del_segmento(
                String segment, String amount, String status, String finalBalance) {
            UUID id = service.openAccount(ADMIN, command(IBAN).segment(segment).build()).id();

            TransactionSummary transaction = service.withdraw(ADMIN, 
                    WithdrawCommand.builder().accountId(id).amount(new BigDecimal(amount)).build());

            assertThat(transaction.type()).isEqualTo("WITHDRAWAL");
            assertThat(transaction.status()).isEqualTo(status);
            assertThat(transaction.amount()).isEqualByComparingTo(amount);
            assertThat(service.findAccount(ADMIN, id).balance()).isEqualByComparingTo(finalBalance);
        }

        @Test
        @DisplayName("Una retirada rechazada no cambia el saldo pero queda registrada")
        void una_retirada_rechazada_no_cambia_el_saldo_pero_queda_registrada() {
            UUID id = service.openAccount(ADMIN, command(IBAN).build()).id();

            TransactionSummary transaction = service.withdraw(ADMIN, 
                    WithdrawCommand.builder().accountId(id).amount(new BigDecimal("500")).build());

            AccountDetails details = service.findAccount(ADMIN, id);
            assertThat(transaction.status()).isEqualTo("REJECTED");
            assertThat(details.balance()).isEqualByComparingTo("100.00");
            assertThat(details.transactions()).hasSize(2);
            assertThat(details.transactions()).last().satisfies(last -> {
                assertThat(last.id()).isEqualTo(transaction.id());
                assertThat(last.status()).isEqualTo("REJECTED");
            });
        }

        @Test
        @DisplayName("Una cuenta desconocida lanza AccountNotFoundException")
        void una_cuenta_desconocida_lanza_account_not_found_exception() {
            UUID unknown = UUID.randomUUID();
            WithdrawCommand command = WithdrawCommand.builder().accountId(unknown).amount(BigDecimal.TEN).build();

            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.withdraw(ADMIN, command))
                    .withMessageContaining(unknown.toString());
        }

        @Test
        @DisplayName("Una retirada en otra divisa lanza CurrencyMismatchException y no cambia el saldo")
        void una_retirada_en_otra_divisa_lanza_currency_mismatch_exception_y_no_cambia_el_saldo() {
            UUID id = service.openAccount(ADMIN, command(IBAN).build()).id();
            WithdrawCommand dollars =
                    WithdrawCommand.builder().accountId(id).amount(BigDecimal.TEN).currency("USD").build();

            assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> service.withdraw(ADMIN, dollars));

            AccountDetails details = service.findAccount(ADMIN, id);
            assertThat(details.balance()).isEqualByComparingTo("100.00");
            assertThat(details.transactions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Transferir")
    class Transfer {

        private UUID sourceId;
        private UUID targetId;

        @BeforeEach
        void openAccounts() {
            sourceId = service.openAccount(ADMIN, command(IBAN).initialDeposit(new BigDecimal("500")).build()).id();
            targetId = service.openAccount(ADMIN, command(OTHER_IBAN).initialDeposit(new BigDecimal("100")).build()).id();
            clearInvocations(repository);
        }

        private TransferCommand transfer(UUID source, String targetAccountNumber, String amount) {
            return TransferCommand.builder()
                    .sourceAccountId(source)
                    .targetAccountNumber(targetAccountNumber)
                    .amount(new BigDecimal(amount))
                    .build();
        }

        @Test
        @DisplayName("Una transferencia aprobada actualiza los saldos de las dos cuentas y guarda ambas")
        void una_transferencia_aprobada_actualiza_los_saldos_y_guarda_ambas_cuentas() {
            TransferSummary summary = service.transfer(ADMIN, transfer(sourceId, OTHER_IBAN, "200"));

            assertThat(summary.status()).isEqualTo("APPROVED");
            assertThat(summary.sourceAccountId()).isEqualTo(sourceId);
            assertThat(summary.sourceAccountNumber()).isEqualTo(IBAN);
            assertThat(summary.targetAccountId()).isEqualTo(targetId);
            assertThat(summary.targetAccountNumber()).isEqualTo(OTHER_IBAN);
            assertThat(summary.amount()).isEqualByComparingTo("200.00");
            assertThat(summary.amount().scale()).isEqualTo(2);
            assertThat(summary.currency()).isEqualTo("EUR");
            assertThat(summary.occurredOn()).isNotNull();
            assertThat(service.findAccount(ADMIN, sourceId).balance()).isEqualByComparingTo("300.00");
            assertThat(service.findAccount(ADMIN, targetId).balance()).isEqualByComparingTo("300.00");
            verify(repository, times(2)).save(any(CheckingAccount.class));
            CheckingAccount source = repository.findById(sourceId).orElseThrow();
            CheckingAccount target = repository.findById(targetId).orElseThrow();
            verify(repository).save(source);
            verify(repository).save(target);
        }

        @Test
        @DisplayName("Una transferencia rechazada se devuelve como REJECTED y no cambia ningún saldo")
        void una_transferencia_rechazada_se_devuelve_como_rejected_y_no_cambia_los_saldos() {
            TransferSummary summary = service.transfer(ADMIN, transfer(sourceId, OTHER_IBAN, "600"));

            assertThat(summary.status()).isEqualTo("REJECTED");
            assertThat(service.findAccount(ADMIN, sourceId).balance()).isEqualByComparingTo("500.00");
            assertThat(service.findAccount(ADMIN, sourceId).transactions()).hasSize(2);
            assertThat(service.findAccount(ADMIN, targetId).balance()).isEqualByComparingTo("100.00");
            assertThat(service.findAccount(ADMIN, targetId).transactions()).hasSize(1);
            verify(repository, times(2)).save(any(CheckingAccount.class));
        }

        @Test
        @DisplayName("Una cuenta de origen desconocida lanza AccountNotFoundException y no guarda nada")
        void una_cuenta_de_origen_desconocida_lanza_account_not_found_exception() {
            UUID unknown = UUID.randomUUID();

            assertThatExceptionOfType(AccountNotFoundException.class)
                    .isThrownBy(() -> service.transfer(ADMIN, transfer(unknown, OTHER_IBAN, "10")))
                    .withMessageContaining(unknown.toString());
            verify(repository, never()).save(any(CheckingAccount.class));
        }

        @Test
        @DisplayName("Un número de cuenta de destino desconocido lanza TargetAccountNotFoundException y no mueve dinero")
        void un_numero_de_cuenta_de_destino_desconocido_lanza_target_account_not_found_exception() {
            assertThatExceptionOfType(TargetAccountNotFoundException.class)
                    .isThrownBy(() -> service.transfer(ADMIN, transfer(sourceId, "es00 0000 0000 0000 0000 0099", "10")))
                    .withMessageContaining("ES0000000000000000000099");
            assertThat(service.findAccount(ADMIN, sourceId).balance()).isEqualByComparingTo("500.00");
            assertThat(service.findAccount(ADMIN, sourceId).transactions()).hasSize(1);
            verify(repository, never()).save(any(CheckingAccount.class));
        }

        @Test
        @DisplayName("Transferir a la misma cuenta lanza SameAccountTransferException y no registra movimientos")
        void transferir_a_la_misma_cuenta_lanza_same_account_transfer_exception() {
            assertThatExceptionOfType(SameAccountTransferException.class)
                    .isThrownBy(() -> service.transfer(ADMIN, transfer(sourceId, IBAN, "10")));
            assertThat(service.findAccount(ADMIN, sourceId).transactions()).hasSize(1);
            verify(repository, never()).save(any(CheckingAccount.class));
        }

        @Test
        @DisplayName("Una transferencia en otra divisa lanza CurrencyMismatchException y no cambia los saldos")
        void una_transferencia_en_otra_divisa_lanza_currency_mismatch_exception() {
            TransferCommand dollars = TransferCommand.builder()
                    .sourceAccountId(sourceId)
                    .targetAccountNumber(OTHER_IBAN)
                    .amount(BigDecimal.TEN)
                    .currency("USD")
                    .build();

            assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> service.transfer(ADMIN, dollars));
            assertThat(service.findAccount(ADMIN, sourceId).balance()).isEqualByComparingTo("500.00");
            assertThat(service.findAccount(ADMIN, targetId).balance()).isEqualByComparingTo("100.00");
        }
    }
}
