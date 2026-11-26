package com.dawnrock.ddd.adapter.in.web;

import java.time.Clock;
import java.time.Instant;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.ObjectProvider;

import com.dawnrock.ddd.adapter.in.PendingIdentity;

/**
 * Acceso de la web a la {@link PendingIdentity} que espera en la sesión HTTP (#196, #198): leerla sin dejar pasar una
 * caducada, renovarla y quitarla. No es un bean: cada controlador la construye con el reloj de la aplicación.
 */
final class PendingIdentitySession {

    private final ObjectProvider<Clock> clock;

    PendingIdentitySession(ObjectProvider<Clock> clock) {
        this.clock = clock;
    }

    /** La identidad pendiente de la sesión, o {@code null} si no hay o ha caducado (en ese caso la quita). */
    PendingIdentity current(HttpSession session) {
        Object attribute = session.getAttribute(PendingIdentity.SESSION_ATTRIBUTE);
        if (!(attribute instanceof PendingIdentity pending)) {
            return null;
        }
        if (pending.isExpired(now())) {
            remove(session);
            return null;
        }
        return pending;
    }

    /**
     * Sustituye la identidad por otra igual con {@code createdAt} = ahora (#198): mientras el cliente completa pasos del
     * asistente, la ventana de 10 minutos se desliza.
     */
    void renew(HttpSession session, PendingIdentity pending) {
        session.setAttribute(PendingIdentity.SESSION_ATTRIBUTE, new PendingIdentity(pending.provider(),
                pending.subject(), pending.email(), now().instant()));
    }

    void remove(HttpSession session) {
        session.removeAttribute(PendingIdentity.SESSION_ATTRIBUTE);
    }

    /** El instante actual según el reloj de la aplicación. */
    Instant instant() {
        return now().instant();
    }

    private Clock now() {
        return clock.getIfAvailable(Clock::systemUTC);
    }
}
