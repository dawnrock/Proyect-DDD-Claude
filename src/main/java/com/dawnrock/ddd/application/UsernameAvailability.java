package com.dawnrock.ddd.application;

/**
 * Si un usuario ya está tomado (R30): es de un cliente o ya es propietario de alguna cuenta. No mira los usuarios de
 * Spring Security, que la aplicación no conoce. Lo comparten el alta y las consultas (#174).
 */
final class UsernameAvailability {

    private final CustomerOwnershipRepository customerOwnership;
    private final AccountOwnershipRepository ownership;

    UsernameAvailability(CustomerOwnershipRepository customerOwnership, AccountOwnershipRepository ownership) {
        this.customerOwnership = customerOwnership;
        this.ownership = ownership;
    }

    boolean isTaken(String username) {
        return customerOwnership.customerOf(username).isPresent() || !ownership.accountsOf(username).isEmpty();
    }
}
