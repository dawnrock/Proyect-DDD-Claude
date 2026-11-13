package com.pronoide.ddd.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.pronoide.ddd.adapter.out.persistence.PersistenceTestAdapters;
import com.pronoide.ddd.application.CheckingAccountCommands;
import com.pronoide.ddd.application.CheckingAccountQueries;
import com.pronoide.ddd.application.listener.AuditLogListener;
import com.pronoide.ddd.application.listener.CustomerNotificationListener;
import com.pronoide.ddd.domain.model.AccountNumber;
import com.pronoide.ddd.domain.model.CheckingAccount;
import com.pronoide.ddd.domain.model.CheckingAccountFactory;
import com.pronoide.ddd.domain.model.DomainEventPublisher;
import com.pronoide.ddd.domain.model.MonetaryAmount;
import com.pronoide.ddd.domain.model.TaxId;
import com.pronoide.ddd.domain.service.MoneyTransferService;

/** La configuración construye el núcleo sin Spring: se prueba llamando a sus métodos {@code @Bean} directamente. */
class DomainConfigurationTests {

    private final DomainConfiguration configuration = new DomainConfiguration();

    @Test
    @DisplayName("Construye la factoría de cuentas con los puertos de numeración y de verificación de ingresos")
    void construye_la_factoria_con_sus_dos_puertos() {
        AccountNumber number = new AccountNumber("ES9121000418450200051332");
        CheckingAccountFactory factory =
                configuration.checkingAccountFactory(() -> number, taxId -> Optional.empty());

        CheckingAccount account =
                factory.open("Ada Lovelace", new TaxId("12345678Z"), MonetaryAmount.euros("100.00"));

        assertThat(account.accountNumber()).isEqualTo(number);
    }

    @Test
    @DisplayName("Construye el servicio de dominio de las transferencias")
    void construye_el_servicio_de_transferencias() {
        assertThat(configuration.moneyTransferService()).isInstanceOf(MoneyTransferService.class);
    }

    @Test
    @DisplayName("Construye el servicio de revisión del segmento y el reloj en UTC")
    void construye_el_servicio_de_revision_del_segmento_y_el_reloj() {
        assertThat(configuration.segmentReviewService(taxId -> Optional.empty())).isNotNull();
        assertThat(configuration.clock().getZone()).isEqualTo(java.time.ZoneOffset.UTC);
    }

    @Test
    @DisplayName("Construye el servicio de aplicación, que ofrece los dos puertos de entrada")
    void construye_el_servicio_de_aplicacion_con_los_dos_puertos_de_entrada() {
        CheckingAccountFactory factory = configuration.checkingAccountFactory(
                PersistenceTestAdapters.accountNumbers(), taxId -> Optional.empty());

        Object service = configuration.checkingAccountService(PersistenceTestAdapters.accounts(),
                PersistenceTestAdapters.ownership(), mock(DomainEventPublisher.class), factory,
                configuration.moneyTransferService(), configuration.segmentReviewService(taxId -> Optional.empty()),
                configuration.clock(), PersistenceTestAdapters.unitOfWork());

        assertThat(service).isInstanceOf(CheckingAccountCommands.class).isInstanceOf(CheckingAccountQueries.class);
    }

    @Test
    @DisplayName("Construye los consumidores de eventos de auditoría y de aviso al cliente")
    void construye_los_consumidores_de_eventos() {
        assertThat(configuration.auditLogListener()).isInstanceOf(AuditLogListener.class);
        assertThat(configuration.customerNotificationListener()).isInstanceOf(CustomerNotificationListener.class);
    }
}
