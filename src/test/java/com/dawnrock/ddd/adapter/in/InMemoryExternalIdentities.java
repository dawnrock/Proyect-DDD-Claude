package com.dawnrock.ddd.adapter.in;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Doble en memoria de {@link ExternalIdentities} para los tests del soporte de seguridad: cumple el mismo contrato que
 * la implementación de JDBC ({@link ExternalIdentitiesContract}), incluida la comprobación de que el usuario existe.
 */
final class InMemoryExternalIdentities implements ExternalIdentities {

    private final Predicate<String> userExists;
    private final Map<Key, String> usernameByIdentity = new HashMap<>();

    /** @param userExists si un usuario local existe (en los tests, el {@code UserDetailsManager} en memoria) */
    InMemoryExternalIdentities(Predicate<String> userExists) {
        this.userExists = userExists;
    }

    @Override
    public synchronized Optional<String> usernameOf(String provider, String subject) {
        return Optional.ofNullable(usernameByIdentity.get(new Key(provider, subject)));
    }

    @Override
    public synchronized void link(String provider, String subject, String username) {
        if (!userExists.test(username)) {
            throw new IllegalArgumentException("No existe el usuario " + username);
        }
        Optional<String> current = usernameOf(provider, subject);
        if (current.isPresent()) {
            if (current.get().equals(username)) {
                return;
            }
            throw new IdentityAlreadyLinkedException(provider, username);
        }
        boolean userHasOne = usernameByIdentity.entrySet().stream()
                .anyMatch(e -> e.getKey().provider().equals(provider) && e.getValue().equals(username));
        if (userHasOne) {
            throw new IdentityAlreadyLinkedException(provider, username);
        }
        usernameByIdentity.put(new Key(provider, subject), username);
    }

    private record Key(String provider, String subject) {
    }
}
