package com.dawnrock.ddd.application;

import static com.dawnrock.ddd.application.ServiceTestSupport.ADMIN;
import static com.dawnrock.ddd.application.ServiceTestSupport.highNetWorthOpening;
import static com.dawnrock.ddd.application.ServiceTestSupport.retailOpening;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.support.ApplicationServicesFixture;

/** Consultas de cuentas con los adaptadores en memoria reales detrás de los puertos: no hace falta simularlos. */
class CheckingAccountServiceTests {

    private CheckingAccountService service;
    private AccountOpeningCommands opening;

    @BeforeEach
    void setUp() {
        ApplicationServicesFixture fixture = ApplicationServicesFixture.builder().build();
        service = fixture.service();
        opening = fixture.accountOpeningService();
    }

    @Test
    @DisplayName("El servicio ofrece las consultas por su puerto de entrada")
    void theServiceOffersTheQueriesThroughItsInputPort() {
        assertThat(service).isInstanceOf(CheckingAccountQueries.class);
    }

    @Nested
    @DisplayName("Consultar cuenta")
    class FindAccount {

        @Test
        @DisplayName("Una cuenta existente se devuelve con el depósito inicial entre sus movimientos")
        void anExistingAccountIsReturnedWithTheInitialDepositAmongItsTransactions() {
            AccountSummary opened = opening.openAccount(ADMIN, highNetWorthOpening().build());

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
        void anUnknownAccountThrowsAccountNotFoundException() {
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
        void withNoAccountsReturnsAnEmptyList() {
            assertThat(service.listAccounts(ADMIN)).isEmpty();
        }

        @Test
        @DisplayName("Varias cuentas se devuelven ordenadas por número de cuenta, con su segmento")
        void severalAccountsAreReturnedSortedByAccountNumber() {
            AccountSummary grace = opening.openAccount(ADMIN, highNetWorthOpening().build());
            AccountSummary ada = opening.openAccount(ADMIN, retailOpening().build());

            // El orden lo decide el IBAN completo, dígitos de control incluidos, no el orden de apertura
            assertThat(service.listAccounts(ADMIN))
                    .isSortedAccordingTo(java.util.Comparator.comparing(AccountSummary::accountNumber))
                    .extracting(AccountSummary::accountNumber, AccountSummary::holderName, AccountSummary::segment)
                    .containsExactlyInAnyOrder(
                            tuple(grace.accountNumber(), "Grace Hopper", "HIGH_NET_WORTH"),
                            tuple(ada.accountNumber(), "Ada Lovelace", "RETAIL"));
        }
    }

}
