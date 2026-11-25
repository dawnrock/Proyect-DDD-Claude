package com.dawnrock.ddd.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dawnrock.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.dawnrock.ddd.application.AccountOpeningCommands;
import com.dawnrock.ddd.application.CheckingAccountQueries;
import com.dawnrock.ddd.application.MoneyMovementCommands;
import com.dawnrock.ddd.application.SegmentReviewCommands;
import com.dawnrock.ddd.application.listener.AuditLogListener;
import com.dawnrock.ddd.application.listener.CustomerNotificationListener;
import com.dawnrock.ddd.domain.model.AccountNumber;
import com.dawnrock.ddd.domain.model.CheckingAccount;
import com.dawnrock.ddd.domain.model.CheckingAccountFactory;
import com.dawnrock.ddd.domain.model.DomainEventPublisher;
import com.dawnrock.ddd.domain.model.MonetaryAmount;
import com.dawnrock.ddd.domain.model.TaxId;
import com.dawnrock.ddd.domain.service.MoneyTransferService;

/** La configuración construye el núcleo sin Spring: se prueba llamando a sus métodos {@code @Bean} directamente. */
class DomainConfigurationTests {

    private final DomainConfiguration configuration = new DomainConfiguration();

    @Test
    @DisplayName("Construye la factoría de cuentas con los puertos de numeración y de verificación de ingresos")
    void buildsTheFactoryWithItsTwoPorts() {
        AccountNumber number = new AccountNumber("ES9121000418450200051332");
        CheckingAccountFactory factory =
                configuration.checkingAccountFactory(() -> number, taxId -> Optional.empty());

        CheckingAccount account =
                factory.open("Ada Lovelace", new TaxId("12345678Z"), MonetaryAmount.euros("100.00"));

        assertThat(account.accountNumber()).isEqualTo(number);
    }

    @Test
    @DisplayName("Construye el servicio de dominio de las transferencias")
    void buildsTheTransferService() {
        assertThat(configuration.moneyTransferService()).isInstanceOf(MoneyTransferService.class);
    }

    @Test
    @DisplayName("Construye el servicio de revisión del segmento y el reloj en UTC")
    void buildsTheSegmentReviewServiceAndTheClock() {
        assertThat(configuration.segmentReviewService(taxId -> Optional.empty())).isNotNull();
        assertThat(configuration.clock().getZone()).isEqualTo(java.time.ZoneOffset.UTC);
    }

    @Test
    @DisplayName("Construye la apertura de cuentas, que ofrece su puerto de entrada")
    void buildsTheAccountOpeningWithItsInputPort() {
        CheckingAccountFactory factory = configuration.checkingAccountFactory(
                PersistenceTestAdapters.accountNumbers(), taxId -> Optional.empty());

        Object service = configuration.accountOpeningService(PersistenceTestAdapters.accounts(),
                PersistenceTestAdapters.ownership(), mock(DomainEventPublisher.class), factory,
                PersistenceTestAdapters.unitOfWork(), PersistenceTestAdapters.customers(),
                PersistenceTestAdapters.customerOwnership());

        assertThat(service).isInstanceOf(AccountOpeningCommands.class);
    }

    @Test
    @DisplayName("Construye las consultas de cuentas, que ofrecen su puerto de entrada")
    void buildsTheAccountQueriesWithTheirInputPort() {
        Object service = configuration.checkingAccountService(PersistenceTestAdapters.accounts(),
                PersistenceTestAdapters.ownership(), PersistenceTestAdapters.unitOfWork(),
                PersistenceTestAdapters.customers(), PersistenceTestAdapters.customerOwnership());

        assertThat(service).isInstanceOf(CheckingAccountQueries.class);
    }

    @Test
    @DisplayName("Construye los movimientos de dinero, que ofrecen su puerto de entrada")
    void buildsTheMoneyMovementsWithTheirInputPort() {
        Object service = configuration.moneyMovementService(PersistenceTestAdapters.accounts(),
                PersistenceTestAdapters.ownership(), mock(DomainEventPublisher.class),
                configuration.moneyTransferService(), configuration.segmentReviewService(taxId -> Optional.empty()),
                configuration.clock(), PersistenceTestAdapters.unitOfWork());

        assertThat(service).isInstanceOf(MoneyMovementCommands.class);
    }

    @Test
    @DisplayName("Construye la revisión periódica de segmentos, que ofrece su puerto de entrada")
    void buildsTheSegmentReviewWithItsInputPort() {
        Object service = configuration.accountSegmentReviewService(PersistenceTestAdapters.accounts(),
                mock(DomainEventPublisher.class), configuration.segmentReviewService(taxId -> Optional.empty()),
                configuration.clock(), PersistenceTestAdapters.unitOfWork());

        assertThat(service).isInstanceOf(SegmentReviewCommands.class);
    }

    @Test
    @DisplayName("Construye los consumidores de eventos de auditoría y de aviso al cliente")
    void buildsTheEventConsumers() {
        assertThat(configuration.auditLogListener()).isInstanceOf(AuditLogListener.class);
        assertThat(configuration.customerNotificationListener()).isInstanceOf(CustomerNotificationListener.class);
    }
}
