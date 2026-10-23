package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CurrencyMismatchException;
import com.pronoide.ddd.domain.model.DuplicateAccountNumberException;
import com.pronoide.ddd.domain.model.InsufficientInitialDepositException;
import com.pronoide.ddd.persistence.InMemoryCheckingAccountRepository;

/** Casos de uso con el repositorio en memoria real: es concreto y no hace falta simularlo. */
class CheckingAccountServiceTests {

    private static final String IBAN = "ES9121000418450200051332";
    private static final String OTHER_IBAN = "ES7921000813610123456789";

    private InMemoryCheckingAccountRepository repository;
    private CheckingAccountService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCheckingAccountRepository();
        service = new CheckingAccountService(repository);
    }

    private static OpenAccountCommand.Builder command(String accountNumber) {
        return OpenAccountCommand.builder()
                .accountNumber(accountNumber)
                .holderName("Ada Lovelace")
                .segment("RETAIL")
                .initialDeposit(new BigDecimal("100"));
    }

    @Nested
    @DisplayName("Abrir cuenta")
    class OpenAccount {

        @Test
        @DisplayName("Un depósito válido abre la cuenta, la guarda y devuelve su resumen")
        void un_deposito_valido_abre_la_cuenta_la_guarda_y_devuelve_su_resumen() {
            AccountSummary summary = service.openAccount(command("es91 2100 0418 4502 0005 1332").build());

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
                    .isThrownBy(() -> service.openAccount(tooLow));
            assertThat(repository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Un depósito en otra divisa se rechaza y no se guarda")
        void un_deposito_en_otra_divisa_se_rechaza_y_no_se_guarda() {
            OpenAccountCommand inDollars = command(IBAN).currency("USD").build();

            assertThatExceptionOfType(CurrencyMismatchException.class)
                    .isThrownBy(() -> service.openAccount(inDollars));
            assertThat(repository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Un número de cuenta en uso se rechaza aunque venga con espacios o en minúsculas")
        void un_numero_de_cuenta_en_uso_se_rechaza_aunque_venga_con_espacios_o_en_minusculas() {
            service.openAccount(command(IBAN).build());
            OpenAccountCommand duplicate = command("es91 2100 0418 4502 0005 1332").holderName("Grace Hopper").build();

            assertThatExceptionOfType(DuplicateAccountNumberException.class)
                    .isThrownBy(() -> service.openAccount(duplicate))
                    .withMessageContaining(IBAN);
            assertThat(repository.findAll()).hasSize(1);
            assertThat(repository.findByAccountNumber(new AccountNumber(IBAN)).orElseThrow().primaryHolder().fullName())
                    .isEqualTo("Ada Lovelace");
        }

        @Test
        @DisplayName("Un cliente de alto patrimonio abre su cuenta con su segmento")
        void un_cliente_de_alto_patrimonio_abre_su_cuenta_con_su_segmento() {
            AccountSummary summary = service.openAccount(command(IBAN).segment("HIGH_NET_WORTH").build());

            assertThat(repository.findById(summary.id()).orElseThrow().primaryHolder().isHighNetWorth()).isTrue();
        }
    }

    @Nested
    @DisplayName("Consultar cuenta")
    class FindAccount {

        @Test
        @DisplayName("Una cuenta existente se devuelve con el depósito inicial entre sus movimientos")
        void una_cuenta_existente_se_devuelve_con_el_deposito_inicial_entre_sus_movimientos() {
            AccountSummary opened = service.openAccount(command(IBAN).segment("HIGH_NET_WORTH").build());

            AccountDetails details = service.findAccount(opened.id());

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
                    .isThrownBy(() -> service.findAccount(unknown))
                    .withMessageContaining(unknown.toString());
        }
    }

    @Nested
    @DisplayName("Listar cuentas")
    class ListAccounts {

        @Test
        @DisplayName("Sin cuentas devuelve una lista vacía")
        void sin_cuentas_devuelve_una_lista_vacia() {
            assertThat(service.listAccounts()).isEmpty();
        }

        @Test
        @DisplayName("Varias cuentas se devuelven ordenadas por número de cuenta")
        void varias_cuentas_se_devuelven_ordenadas_por_numero_de_cuenta() {
            service.openAccount(command(OTHER_IBAN).holderName("Grace Hopper").build());
            service.openAccount(command(IBAN).build());

            assertThat(service.listAccounts())
                    .extracting(AccountSummary::accountNumber, AccountSummary::holderName)
                    .containsExactly(
                            tuple(OTHER_IBAN, "Grace Hopper"),
                            tuple(IBAN, "Ada Lovelace"));
        }
    }
}
