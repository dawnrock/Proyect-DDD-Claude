package com.pronoide.ddd.adapter.in;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Altas de clientes recientes, en memoria (#131, ADR 0018). Cuenta las altas de cada IP y las de todas juntas en una
 * ventana deslizante de {@link SignUpLimitProperties#window()}: una IP no puede pasar de
 * {@link SignUpLimitProperties#maxPerIp()} y entre todas no pueden pasar de {@link SignUpLimitProperties#maxTotal()}.
 * El límite global acota la memoria que ocupan las altas aunque alguien use muchas IP; mientras está lleno, nadie puede
 * darse de alta.
 *
 * <p>Cada alta se <em>reserva</em> al empezar ({@link #tryReserve}), en la misma operación atómica que mira los dos
 * límites, para que una ráfaga de peticiones simultáneas no pase de ellos. Si el alta falla, {@link #release} devuelve
 * la reserva: solo cuentan las altas que crearon un cliente. {@link #lockedFor} consulta sin reservar.
 *
 * <p>Los contadores no se comparten entre instancias y se pierden al reiniciar: basta para una sola instancia. Las
 * entradas caducadas se eliminan al consultarlas y, cada cierto número de reservas, en bloque. Lo crea
 * {@link SecuritySupport}.
 */
public class SignUpLimit {

    /** Cada cuántas reservas se purgan las entradas caducadas de todas las IP. */
    private static final int PRUNE_EVERY = 1_000;

    private final SignUpLimitProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> byIp = new HashMap<>();
    private final Deque<Instant> total = new ArrayDeque<>();
    private int reservationsSincePrune;

    public SignUpLimit(SignUpLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Tiempo que le falta a esa IP para poder darse de alta; vacío si puede. */
    public synchronized Optional<Duration> lockedFor(String ip) {
        Instant now = clock.instant();
        Deque<Instant> recent = byIp.get(ip);
        Optional<Duration> address = Optional.empty();
        if (recent != null) {
            dropExpired(recent, now);
            if (recent.isEmpty()) {
                byIp.remove(ip);
            } else {
                address = remaining(recent, properties.maxPerIp(), now);
            }
        }
        dropExpired(total, now);
        Optional<Duration> global = remaining(total, properties.maxTotal(), now);
        if (address.isPresent() && global.isPresent()) {
            return Optional.of(address.get().compareTo(global.get()) >= 0 ? address.get() : global.get());
        }
        return address.isPresent() ? address : global;
    }

    /**
     * Empieza un alta: si algún límite está lleno, devuelve lo que falta y no reserva nada; si no, la anota en la IP y
     * en el total y devuelve vacío.
     */
    public synchronized Optional<Duration> tryReserve(String ip) {
        Optional<Duration> locked = lockedFor(ip);
        if (locked.isPresent()) {
            return locked;
        }
        Instant now = clock.instant();
        byIp.computeIfAbsent(ip, ignored -> new ArrayDeque<>()).addLast(now);
        total.addLast(now);
        pruneNowAndThen(now);
        return Optional.empty();
    }

    /** El alta falló: se devuelve su reserva de la IP y del total. */
    public synchronized void release(String ip) {
        Deque<Instant> recent = byIp.get(ip);
        if (recent == null || recent.isEmpty()) {
            return;
        }
        recent.pollLast();
        if (recent.isEmpty()) {
            byIp.remove(ip);
        }
        total.pollLast();
    }

    private Optional<Duration> remaining(Deque<Instant> recent, int max, Instant now) {
        if (recent.size() < max) {
            return Optional.empty();
        }
        // Solo se reserva con sitio, así que nunca hay más de max: hay sitio cuando caduque la más antigua
        return Optional.of(Duration.between(now, recent.peekFirst().plus(properties.window())));
    }

    private void dropExpired(Deque<Instant> recent, Instant now) {
        Instant oldestThatCounts = now.minus(properties.window());
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(oldestThatCounts)) {
            recent.removeFirst();
        }
    }

    private void pruneNowAndThen(Instant now) {
        if (++reservationsSincePrune < PRUNE_EVERY) {
            return;
        }
        reservationsSincePrune = 0;
        byIp.values().removeIf(recent -> {
            dropExpired(recent, now);
            return recent.isEmpty();
        });
    }
}
