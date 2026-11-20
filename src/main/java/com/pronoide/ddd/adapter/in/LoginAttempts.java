package com.pronoide.ddd.adapter.in;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Intentos recientes de inicio de sesión, en memoria (#130, ADR 0017). Bloquea la pareja usuario e IP tras
 * {@link LoginAttemptsProperties#maxPerUser()} fallos y la IP entera tras {@link LoginAttemptsProperties#maxPerIp()},
 * durante {@link LoginAttemptsProperties#lockout()} desde el último fallo. Un fallo deja de contar cuando es más antiguo
 * que esa misma ventana.
 *
 * <p>Cada intento se <em>reserva</em> como fallo al empezar ({@link #tryAttempt}), antes de comprobar la contraseña, y
 * en la misma operación atómica que mira el bloqueo: así una ráfaga de peticiones simultáneas no prueba más contraseñas
 * que el límite. Si el intento acierta, {@link #succeeded} borra los fallos de su pareja y devuelve la reserva de la IP;
 * si falla por otra causa que no sean las credenciales, {@link #released} devuelve las dos reservas.
 *
 * <p>Todo va sincronizado: un inicio de sesión ya es lento (BCrypt se calcula fuera de aquí) y la sección crítica es
 * corta. Los contadores no se comparten entre instancias y se pierden al reiniciar: basta para una sola instancia. El
 * usuario se recorta en la clave y las entradas caducadas se eliminan al consultarlas y, cada cierto número de
 * intentos, en bloque, para que la memoria no crezca sin límite con usuarios inventados. Lo crea
 * {@link SecuritySupport}.
 */
public class LoginAttempts {

    /** Cada cuántos intentos se purgan las entradas caducadas de todos los usuarios e IP. */
    private static final int PRUNE_EVERY = 1_000;

    /** Longitud máxima del usuario dentro de la clave: nadie tiene un usuario más largo (ver el patrón de usuarios). */
    static final int MAX_USERNAME_IN_KEY = 128;

    private final LoginAttemptsProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> byUserAndIp = new HashMap<>();
    private final Map<String, Deque<Instant>> byIp = new HashMap<>();
    private int attemptsSincePrune;

    public LoginAttempts(LoginAttemptsProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Tiempo que le queda al bloqueo de ese usuario desde esa IP; vacío si puede intentarlo. */
    public synchronized Optional<Duration> lockedFor(String username, String ip) {
        Instant now = clock.instant();
        Optional<Duration> pair = remaining(byUserAndIp, pairKey(username, ip), properties.maxPerUser(), now);
        Optional<Duration> address = remaining(byIp, ip, properties.maxPerIp(), now);
        if (pair.isPresent() && address.isPresent()) {
            return Optional.of(pair.get().compareTo(address.get()) >= 0 ? pair.get() : address.get());
        }
        return pair.isPresent() ? pair : address;
    }

    /**
     * Empieza un intento: si hay bloqueo, devuelve lo que le queda y no reserva nada; si no, lo reserva como fallo de la
     * pareja y de la IP y devuelve vacío.
     */
    public synchronized Optional<Duration> tryAttempt(String username, String ip) {
        Optional<Duration> locked = lockedFor(username, ip);
        if (locked.isPresent()) {
            return locked;
        }
        Instant now = clock.instant();
        record(byUserAndIp, pairKey(username, ip), properties.maxPerUser(), now);
        record(byIp, ip, properties.maxPerIp(), now);
        pruneNowAndThen(now);
        return Optional.empty();
    }

    /** El intento acertó: se borran los fallos de la pareja y se devuelve la reserva de la IP. */
    public synchronized void succeeded(String username, String ip) {
        byUserAndIp.remove(pairKey(username, ip));
        undoLast(byIp, ip);
    }

    /** El intento terminó sin que fallaran las credenciales (cuenta deshabilitada...): se devuelven sus dos reservas. */
    public synchronized void released(String username, String ip) {
        undoLast(byUserAndIp, pairKey(username, ip));
        undoLast(byIp, ip);
    }

    private Optional<Duration> remaining(Map<String, Deque<Instant>> attempts, String key, int max, Instant now) {
        Deque<Instant> recent = attempts.get(key);
        if (recent == null) {
            return Optional.empty();
        }
        dropExpired(recent, now);
        if (recent.isEmpty()) {
            attempts.remove(key);
            return Optional.empty();
        }
        if (recent.size() < max) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(now, recent.peekLast().plus(properties.lockout())));
    }

    private void record(Map<String, Deque<Instant>> attempts, String key, int max, Instant now) {
        Deque<Instant> recent = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        dropExpired(recent, now);
        recent.addLast(now);
        // Solo importan los últimos max fallos: con ellos se decide el bloqueo y su final
        while (recent.size() > max) {
            recent.removeFirst();
        }
    }

    private static void undoLast(Map<String, Deque<Instant>> attempts, String key) {
        Deque<Instant> recent = attempts.get(key);
        if (recent != null) {
            recent.pollLast();
            if (recent.isEmpty()) {
                attempts.remove(key);
            }
        }
    }

    private void dropExpired(Deque<Instant> recent, Instant now) {
        Instant oldestThatCounts = now.minus(properties.lockout());
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(oldestThatCounts)) {
            recent.removeFirst();
        }
    }

    private void pruneNowAndThen(Instant now) {
        if (++attemptsSincePrune < PRUNE_EVERY) {
            return;
        }
        attemptsSincePrune = 0;
        for (Map<String, Deque<Instant>> attempts : List.of(byUserAndIp, byIp)) {
            attempts.values().removeIf(recent -> {
                dropExpired(recent, now);
                return recent.isEmpty();
            });
        }
    }

    private static String pairKey(String username, String ip) {
        String user = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (user.length() > MAX_USERNAME_IN_KEY) {
            user = user.substring(0, MAX_USERNAME_IN_KEY);
        }
        return user + '|' + ip;
    }
}
