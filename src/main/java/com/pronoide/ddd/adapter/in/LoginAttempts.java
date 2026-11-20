package com.pronoide.ddd.adapter.in;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Fallos recientes de inicio de sesión, en memoria (#130, ADR 0017). Bloquea la pareja usuario e IP tras
 * {@link LoginAttemptsProperties#maxPerUser()} fallos y la IP entera tras {@link LoginAttemptsProperties#maxPerIp()},
 * durante {@link LoginAttemptsProperties#lockout()} desde el último fallo. Un fallo deja de contar cuando es más antiguo
 * que esa misma ventana, y un acceso correcto borra los de su pareja (no los de la IP).
 *
 * <p>Los contadores no se comparten entre instancias y se pierden al reiniciar: basta para una sola instancia. Las
 * entradas caducadas se eliminan al consultarlas y, cada cierto número de fallos, en bloque, para que los mapas no
 * crezcan sin límite con usuarios inventados.
 */
@Component
public class LoginAttempts {

    /** Cada cuántos fallos registrados se purgan las entradas caducadas de todos los usuarios e IP. */
    private static final int PRUNE_EVERY = 1_000;

    private final LoginAttemptsProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> byUserAndIp = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> byIp = new ConcurrentHashMap<>();
    private int failuresSincePrune;

    public LoginAttempts(LoginAttemptsProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Tiempo que le queda al bloqueo de ese usuario desde esa IP; vacío si puede intentarlo. */
    public Optional<Duration> lockedFor(String username, String ip) {
        Instant now = clock.instant();
        Optional<Duration> pair = remaining(byUserAndIp, pairKey(username, ip), properties.maxPerUser(), now);
        Optional<Duration> address = remaining(byIp, ip, properties.maxPerIp(), now);
        if (pair.isPresent() && address.isPresent()) {
            return Optional.of(pair.get().compareTo(address.get()) >= 0 ? pair.get() : address.get());
        }
        return pair.isPresent() ? pair : address;
    }

    /** Registra un fallo; el bloqueo empieza si alcanza alguno de los dos límites. */
    public void failed(String username, String ip) {
        Instant now = clock.instant();
        record(byUserAndIp, pairKey(username, ip), properties.maxPerUser(), now);
        record(byIp, ip, properties.maxPerIp(), now);
        pruneNowAndThen(now);
    }

    /** Un acceso correcto borra los fallos de esa pareja usuario e IP. */
    public void succeeded(String username, String ip) {
        byUserAndIp.remove(pairKey(username, ip));
    }

    private Optional<Duration> remaining(Map<String, Deque<Instant>> failures, String key, int max, Instant now) {
        Deque<Instant> recent = failures.get(key);
        if (recent == null) {
            return Optional.empty();
        }
        synchronized (recent) {
            dropExpired(recent, now);
            if (recent.isEmpty()) {
                failures.remove(key, recent);
                return Optional.empty();
            }
            if (recent.size() < max) {
                return Optional.empty();
            }
            return Optional.of(Duration.between(now, recent.peekLast().plus(properties.lockout())));
        }
    }

    private void record(Map<String, Deque<Instant>> failures, String key, int max, Instant now) {
        Deque<Instant> recent = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (recent) {
            dropExpired(recent, now);
            recent.addLast(now);
            // Solo importan los últimos max fallos: con ellos se decide el bloqueo y su final
            while (recent.size() > max) {
                recent.removeFirst();
            }
        }
    }

    private void dropExpired(Deque<Instant> recent, Instant now) {
        Instant oldestThatCounts = now.minus(properties.lockout());
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(oldestThatCounts)) {
            recent.removeFirst();
        }
    }

    private synchronized void pruneNowAndThen(Instant now) {
        if (++failuresSincePrune < PRUNE_EVERY) {
            return;
        }
        failuresSincePrune = 0;
        for (Map<String, Deque<Instant>> failures : List.of(byUserAndIp, byIp)) {
            failures.forEach((key, recent) -> {
                synchronized (recent) {
                    dropExpired(recent, now);
                    if (recent.isEmpty()) {
                        failures.remove(key, recent);
                    }
                }
            });
        }
    }

    private static String pairKey(String username, String ip) {
        String user = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return user + '|' + ip;
    }
}
