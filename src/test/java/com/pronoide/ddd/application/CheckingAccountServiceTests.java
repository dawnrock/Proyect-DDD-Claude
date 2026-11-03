package com.pronoide.ddd.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CheckingAccountRepository;
import com.pronoide.ddd.domain.model.CheckingAccountOpened;
import com.pronoide.ddd.domain.model.CurrencyMismatchException;
import com.pronoide.ddd.domain.model.DomainEvent;
import com.pronoide.ddd.domain.model.DomainEventPublisher;
import com.pronoide.ddd.domain.model.DuplicateAccountNumberException;
import com.pronoide.ddd.domain.model.InsufficientInitialDepositException;
import com.pronoide.ddd.domain.model.MoneyDeposited;
import com.pronoide.ddd.domain.model.MoneyWithdrawn;
import com.pronoide.ddd.domain.model.SameAccountTransferException;
import com.pronoide.ddd.domain.model.WithdrawalRejected;
import com.pronoide.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.pronoide.ddd.adapter.out.taxagency.TaxAgencyTestAdapters;

/** Casos de uso con el adaptador en memoria real detrás de los puertos: no hace falta simularlo. */
class CheckingAccountServiceTests {

    private static final String IBAN = "ES9121000418450200051332";

    /** Los casos de uso se piden como administrador; la propiedad se prueba en CheckingAccountServiceOwnershipTests. */
    private static final Caller ADMIN = Caller.builder().username("admin").admin(true).build();

    private CheckingAccountRepository repository;
    private DomainEventPublisher publisher;
    private CheckingAccountService service;

    @BeforeEach
    void setUp() {
        repository = spy(PersistenceTestAdapters.accounts());
        publisher = mock(DomainEventPublisher.class);
        service = new CheckingAccountService(repository, PersistenceTestAdapters.ownership(), publisher,
                PersistenceTestAdapters.accountNumbers(), TaxAgencyTestAdapters.incomeVerifier());
    }

    /** Eventos publicados en cada llamada a {@code publishAll}, en orden. */
    @SuppressWarnings("unchecked")
    private List<List<DomainEvent>> publishedBatches() {
        ArgumentCaptor<List<DomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(publisher, atLeast(0)).publishAll(captor.capture());
        return captor.getAllValues();
    }

    /** Titular minorista por NIF (40.000 € verificados); el IBAN lo genera la factoría. */
    private static OpenAccountCommand.Builder command() {
        return OpenAccountCommand.builder()
                .holderName("Ada Lovelace")
                .holderTaxId("12345678Z")
                .initialDeposit(new BigDecimal("100"))
                .ownerUsername("ada");
    }

    /** Titular de alto patrimonio por NIF (250.000 € verificados). */
    private static OpenAccountCommand.Builder highNetWorthCommand() {
        return command().holderName("Grace Hopper").holderTaxId("87654321X");
    }

    /** Camino antiguo con IBAN y segmento escritos; se elimina en T12 junto con los campos obsoletos. */
    @SuppressWarnings("deprecation")
    private static OpenAccountCommand.Builder legacyCommand(String accountNumber) {
        return OpenAccountCommand.builder()
                .accountNumber(accountNumber)
                .holderName("Ada Lovelace")
                .segment("RETAIL")
                .initialDeposit(new BigDecimal("100"))
                .ownerUsername("ada");
    }

    @Test
    @DisplayName("El servicio ofrece los casos de uso por sus puertos de entrada de commands y de queries")
    void el_servicio_ofrece_los_casos_de_uso_por_sus_puertos_de_entrada_de_commands_y_de_queries() {
        assertThat(service).isInstanceOf(CheckingAccountCommands.class).isInstanceOf(CheckingAccountQueries.class);
    }

    @Nested
    @DisplayName("Abrir cuenta con IBAN y segmento escritos (camino antiguo, hasta T12)")
    class OpenAccountLegacy {

        @Test
        @DisplayName("Un depósito válido abre la cuenta, la guarda y devuelve su resumen")
        void un_deposito_valido_abre_la_cuenta_la_guarda_y_devuelve_su_resumen() {
            AccountSummary summary = service.openAccount(ADMIN, legacyCommand("es91 2100 0418 4502 0005 1332").build());

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
            OpenAccountCommand tooLow = legacyCommand(IBAN).initialDeposit(new BigDecimal("49.99")).build();

            assertThatExceptionOfType(InsufficientInitialDepositException.class)
                    .isThrownBy(() -> service.openAccount(ADMIN, tooLow));
            assertThat(repository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Un depósito en otra divisa se rechaza y no se guarda")
        void un_deposito_en_otra_divisa_se_rechaza_y_no_se_guarda() {
            OpenAccountCommand inDollars = legacyCommand(IBAN).currency("USD").build();

            assertThatExceptionOfType(CurrencyMismatchException.class)
                    .isThrownBy(() -> service.openAccount(ADMIN, inDollars));
            assertThat(repository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("Un número de cuenta en uso se rechaza aunque venga con espacios o en minúsculas")
        void un_numero_de_cuenta_en_uso_se_rechaza_aunque_venga_con_espacios_o_en_minusculas() {
            service.openAccount(ADMIN, legacyCommand(IBAN).build());
            OpenAccountCommand duplicate = legacyCommand("es91 2100 0418 4502 0005 1332").holderName("Grace Hopper").build();

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
            AccountSummary summary = service.openAccount(ADMIN, legacyCommand(IBAN).segment("HIGH_NET_WORTH").build());

            assertThat(repository.findById(summary.id()).orElseThrow().primaryHolder().isHighNetWorth()).isTrue();
        }
    }

    @Nested
    @DisplayName("Abrir cuenta con la factoría")
    class OpenAccountThroughFactory {

        private OpenAccountCommand.Builder byTaxId(String taxId) {
            return OpenAccountCommand.builder()
                    .holderName("Grace Hopper")
                    .holderTaxId(taxId)
                    .initialDeposit(new BigDecimal("100"))
                    .ownerUsername("grace");
        }

        @Test
        @DisplayName("Con NIF la cuenta recibe un IBAN generado y el resumen lo devuelve")
        void con_nif_la_cuenta_recibe_un_iban_generado() {
            AccountSummary summary = service.openAccount(ADMIN, byTaxId("87654321X").build());

            assertThat(summary.accountNumber()).startsWith("ES").hasSize(24);
            assertThat(repository.findByAccountNumber(new AccountNumber(summary.accountNumber()))).isPresent();
        }

        @Test
        @DisplayName("Con ingresos verificados por encima del umbral el resumen es de alto patrimonio")
        void con_ingresos_altos_el_resumen_es_de_alto_patrimonio() {
            AccountSummary summary = service.openAccount(ADMIN, byTaxId("87654321X").build());

            assertThat(summary.segment()).isEqualTo("HIGH_NET_WORTH");
        }

        @Test
        @DisplayName("Con ingresos bajos o un NIF desconocido el resumen es minorista")
        void con_ingresos_bajos_o_nif_desconocido_el_resumen_es_minorista() {
            assertThat(service.openAccount(ADMIN, byTaxId("12345678Z").build()).segment()).isEqualTo("RETAIL");
            assertThat(service.openAccount(ADMIN, byTaxId("99999999R").build()).segment()).isEqualTo("RETAIL");
        }

        @Test
        @DisplayName("Con NIF se ignoran el IBAN y el segmento obsoletos del command")
        @SuppressWarnings("deprecation")
        void con_nif_se_ignoran_los_campos_obsoletos() {
            AccountSummary summary = service.openAccount(ADMIN,
                    byTaxId("12345678Z").accountNumber(IBAN).segment("HIGH_NET_WORTH").build());

            assertThat(summary.accountNumber()).isNotEqualTo(IBAN);
            assertThat(summary.segment()).isEqualTo("RETAIL");
        }

        @Test
        @DisplayName("Un número generado que ya está en uso se rechaza y no se guarda")
        void un_numero_generado_en_uso_se_rechaza() {
            service.openAccount(ADMIN, legacyCommand(IBAN).build());
            CheckingAccountService faulty = new CheckingAccountService(repository, PersistenceTestAdapters.ownership(),
                    publisher, () -> new AccountNumber(IBAN), TaxAgencyTestAdapters.incomeVerifier());

            assertThatExceptionOfType(DuplicateAccountNumberException.class)
                    .isThrownBy(() -> faulty.openAccount(ADMIN, byTaxId("87654321X").build()))
                    .withMessageContaining(IBAN);
            assertThat(repository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("Con NIF se publican los eventos de apertura y se asigna el propietario")
        void con_nif_se_publican_los_eventos_y_se_asigna_el_propietario() {
            clearInvocations(publisher);

            AccountSummary summary = service.openAccount(ADMIN, byTaxId("87654321X").build());

            assertThat(publishedBatches()).singleElement().satisfies(events -> assertThat(events)
                    .hasAtLeastOneElementOfType(CheckingAccountOpened.class));
            assertThat(service.listAccounts(Caller.builder().username("grace").admin(false).build()))
                    .extracting(AccountSummary::id).containsExactly(summary.id());
        }

        @Test
        @DisplayName("Un depósito por debajo del mínimo se rechaza también con NIF")
        void un_deposito_bajo_se_rechaza_tambien_con_nif() {
            OpenAccountCommand tooLow = byTaxId("87654321X").initialDeposit(new BigDecimal("49.99")).build();

            assertThatExceptionOfType(InsufficientInitialDepositException.class)
                    .isThrownBy(() -> service.openAccount(ADMIN, tooLow));
            assertThat(repository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Consultar cuenta")
    class FindAccount {

        @Test
        @DisplayName("Una cuenta existente se devuelve con el depósito inicial entre sus movimientos")
        void una_cuenta_existente_se_devuelve_con_el_deposito_inicial_entre_sus_movimientos() {
            AccountSummary opened = service.openAccount(ADMIN, highNetWorthCommand().build());

            AccountDetails details = service.findAccount(ADMIN, opened.id());

            assertThat(details.id()).isEqualTo(opened.id());
            assertThat(details.accountNumber()).isEqualTo(opened.accountNumber());
            assertThat(details.holderName()).isEqualTo("Grace Hopper");
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
        @DisplayName("Varias cuentas se devuelven ordenadas por número de cuenta, con su segmento")
        void varias_cuentas_se_devuelven_ordenadas_por_numero_de_cuenta() {
            AccountSummary grace = service.openAccount(ADMIN, highNetWorthCommand().build());
            AccountSummary ada = service.openAccount(ADMIN, command().build());

            assertThat(service.listAccounts(ADMIN))
                    .extracting(AccountSummary::accountNumber, AccountSummary::holderName, AccountSummary::segment)
                    .containsExactly(
                            tuple(grace.accountNumber(), "Grace Hopper", "HIGH_NET_WORTH"),
                            tuple(ada.accountNumber(), "Ada Lovelace", "RETAIL"));
        }
    }

    @Nested
    @DisplayName("Ingresar")
    class Deposit {

        @Test
        @DisplayName("Un ingreso aprobado actualiza el saldo y aparece entre los movimientos")
        void un_ingreso_aprobado_actualiza_el_saldo_y_aparece_entre_los_movimientos() {
            UUID id = service.openAccount(ADMIN, command().build()).id();

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
            UUID id = service.openAccount(ADMIN, command().build()).id();
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
            UUID id = service.openAccount(ADMIN,
                    ("HIGH_NET_WORTH".equals(segment) ? highNetWorthCommand() : command()).build()).id();

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
            UUID id = service.openAccount(ADMIN, command().build()).id();

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
            UUID id = service.openAccount(ADMIN, command().build()).id();
            WithdrawCommand dollars =
                    WithdrawCommand.builder().accountId(id).amount(BigDecimal.TEN).currency("USD").build();

            assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> service.withdraw(ADMIN, dollars));

            AccountDetails details = service.findAccount(ADMIN, id);
            assertThat(details.balance()).isEqualByComparingTo("100.00");
            assertThat(details.transactions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Publicar los eventos de dominio")
    class PublishDomainEvents {

        @Test
        @DisplayName("Abrir una cuenta publica cuenta abierta y el ingreso inicial después de guardarla")
        void abrir_una_cuenta_publica_sus_eventos_despues_de_guardarla() {
            service.openAccount(ADMIN, command().build());

            InOrder order = inOrder(repository, publisher);
            order.verify(repository).save(any(CheckingAccount.class));
            order.verify(publisher).publishAll(any());
            assertThat(publishedBatches()).singleElement().satisfies(events -> assertThat(events)
                    .map(DomainEvent::getClass).containsExactly(CheckingAccountOpened.class, MoneyDeposited.class));
        }

        @Test
        @DisplayName("Un ingreso publica el evento de dinero ingresado después de guardar la cuenta")
        void un_ingreso_publica_su_evento_despues_de_guardar() {
            UUID id = service.openAccount(ADMIN, command().build()).id();
            clearInvocations(repository, publisher);

            service.deposit(ADMIN, DepositCommand.builder().accountId(id).amount(BigDecimal.TEN).build());

            InOrder order = inOrder(repository, publisher);
            order.verify(repository).save(any(CheckingAccount.class));
            order.verify(publisher).publishAll(any());
            assertThat(publishedBatches()).singleElement().satisfies(events -> assertThat(events)
                    .map(DomainEvent::getClass).containsExactly(MoneyDeposited.class));
        }

        @Test
        @DisplayName("Una retirada rechazada publica el evento de retirada rechazada")
        void una_retirada_rechazada_publica_su_evento() {
            UUID id = service.openAccount(ADMIN, command().build()).id();
            clearInvocations(publisher);

            service.withdraw(ADMIN, WithdrawCommand.builder().accountId(id).amount(new BigDecimal("5000")).build());

            assertThat(publishedBatches()).singleElement().satisfies(events -> assertThat(events)
                    .map(DomainEvent::getClass).containsExactly(WithdrawalRejected.class));
        }

        @Test
        @DisplayName("Si guardar la cuenta falla, no se publica ningún evento")
        void si_guardar_la_cuenta_falla_no_se_publica_ningun_evento() {
            UUID id = service.openAccount(ADMIN, command().build()).id();
            clearInvocations(publisher);
            doThrow(new IllegalStateException("fallo al guardar")).when(repository).save(any(CheckingAccount.class));

            assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() ->
                    service.deposit(ADMIN, DepositCommand.builder().accountId(id).amount(BigDecimal.TEN).build()));
            verify(publisher, never()).publishAll(any());
        }

        @Test
        @DisplayName("Un caso de uso que falla antes de guardar no publica nada")
        void un_caso_de_uso_que_falla_antes_de_guardar_no_publica_nada() {
            String used = service.openAccount(ADMIN, command().build()).accountNumber();
            CheckingAccountService faulty = new CheckingAccountService(repository, PersistenceTestAdapters.ownership(),
                    publisher, () -> new AccountNumber(used), TaxAgencyTestAdapters.incomeVerifier());
            clearInvocations(publisher);

            assertThatExceptionOfType(DuplicateAccountNumberException.class)
                    .isThrownBy(() -> faulty.openAccount(ADMIN, command().build()));
            verify(publisher, never()).publishAll(any());
        }
    }

    @Nested
    @DisplayName("Transferir")
    class Transfer {

        private UUID sourceId;
        private UUID targetId;
        private String sourceNumber;
        private String targetNumber;

        @BeforeEach
        void openAccounts() {
            AccountSummary source = service.openAccount(ADMIN, command().initialDeposit(new BigDecimal("500")).build());
            AccountSummary target = service.openAccount(ADMIN, command().initialDeposit(new BigDecimal("100")).build());
            sourceId = source.id();
            targetId = target.id();
            sourceNumber = source.accountNumber();
            targetNumber = target.accountNumber();
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
        @DisplayName("Una transferencia aprobada publica la retirada del origen y el ingreso del destino tras guardarlas")
        void una_transferencia_aprobada_publica_sus_eventos_tras_guardar_ambas_cuentas() {
            clearInvocations(publisher);

            service.transfer(ADMIN, transfer(sourceId, targetNumber, "200"));

            InOrder order = inOrder(repository, publisher);
            order.verify(repository, times(2)).save(any(CheckingAccount.class));
            order.verify(publisher, times(2)).publishAll(any());
            assertThat(publishedBatches()).flatMap(events -> events)
                    .extracting(DomainEvent::getClass, DomainEvent::accountId)
                    .containsExactly(tuple(MoneyWithdrawn.class, sourceId), tuple(MoneyDeposited.class, targetId));
        }

        @Test
        @DisplayName("Una transferencia rechazada solo publica la retirada rechazada del origen")
        void una_transferencia_rechazada_solo_publica_la_retirada_rechazada() {
            clearInvocations(publisher);

            service.transfer(ADMIN, transfer(sourceId, targetNumber, "5000"));

            assertThat(publishedBatches()).flatMap(events -> events)
                    .extracting(DomainEvent::getClass, DomainEvent::accountId)
                    .containsExactly(tuple(WithdrawalRejected.class, sourceId));
        }

        @Test
        @DisplayName("Una transferencia aprobada actualiza los saldos de las dos cuentas y guarda ambas")
        void una_transferencia_aprobada_actualiza_los_saldos_y_guarda_ambas_cuentas() {
            TransferSummary summary = service.transfer(ADMIN, transfer(sourceId, targetNumber, "200"));

            assertThat(summary.status()).isEqualTo("APPROVED");
            assertThat(summary.sourceAccountId()).isEqualTo(sourceId);
            assertThat(summary.sourceAccountNumber()).isEqualTo(sourceNumber);
            assertThat(summary.targetAccountId()).isEqualTo(targetId);
            assertThat(summary.targetAccountNumber()).isEqualTo(targetNumber);
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
            TransferSummary summary = service.transfer(ADMIN, transfer(sourceId, targetNumber, "600"));

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
                    .isThrownBy(() -> service.transfer(ADMIN, transfer(unknown, targetNumber, "10")))
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
                    .isThrownBy(() -> service.transfer(ADMIN, transfer(sourceId, sourceNumber, "10")));
            assertThat(service.findAccount(ADMIN, sourceId).transactions()).hasSize(1);
            verify(repository, never()).save(any(CheckingAccount.class));
        }

        @Test
        @DisplayName("Una transferencia en otra divisa lanza CurrencyMismatchException y no cambia los saldos")
        void una_transferencia_en_otra_divisa_lanza_currency_mismatch_exception() {
            TransferCommand dollars = TransferCommand.builder()
                    .sourceAccountId(sourceId)
                    .targetAccountNumber(targetNumber)
                    .amount(BigDecimal.TEN)
                    .currency("USD")
                    .build();

            assertThatExceptionOfType(CurrencyMismatchException.class).isThrownBy(() -> service.transfer(ADMIN, dollars));
            assertThat(service.findAccount(ADMIN, sourceId).balance()).isEqualByComparingTo("500.00");
            assertThat(service.findAccount(ADMIN, targetId).balance()).isEqualByComparingTo("100.00");
        }
    }
}
