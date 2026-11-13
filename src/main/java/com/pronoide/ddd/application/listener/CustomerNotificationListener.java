package com.pronoide.ddd.application.listener;

import org.springframework.context.event.EventListener;

import com.pronoide.ddd.domain.model.CustomerSegment;
import com.pronoide.ddd.domain.model.CustomerSegmentChanged;
import com.pronoide.ddd.domain.model.MoneyDeposited;
import com.pronoide.ddd.domain.model.MoneyWithdrawn;
import com.pronoide.ddd.domain.model.WithdrawalRejected;

import lombok.extern.slf4j.Slf4j;

/**
 * Aviso al cliente de los movimientos de su cuenta. Simulado: escribe el aviso en el log en lugar de enviarlo. No avisa
 * de la apertura, que el cliente ya conoce porque la pide.
 *
 * <p>La declara {@code config.DomainConfiguration} (#20) y conserva {@code @EventListener}, como
 * {@link AuditLogListener}.
 */
@Slf4j
public class CustomerNotificationListener {

    @EventListener
    public void on(MoneyDeposited event) {
        log.info("Aviso al cliente (simulado) · cuenta {}: se han ingresado {}", event.accountId(), event.amount());
    }

    @EventListener
    public void on(MoneyWithdrawn event) {
        log.info("Aviso al cliente (simulado) · cuenta {}: se han retirado {}", event.accountId(), event.amount());
    }

    @EventListener
    public void on(WithdrawalRejected event) {
        log.info("Aviso al cliente (simulado) · cuenta {}: se ha rechazado una retirada de {}", event.accountId(),
                event.amount());
    }

    @EventListener
    public void on(CustomerSegmentChanged event) {
        String segment = event.segment() == CustomerSegment.HIGH_NET_WORTH ? "alto patrimonio" : "minorista";
        log.info("Aviso al cliente (simulado) · cuenta {}: {}, su segmento pasa a ser {}", event.accountId(),
                event.holderName(), segment);
    }
}
