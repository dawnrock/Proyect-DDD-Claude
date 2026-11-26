package com.dawnrock.ddd.adapter.in.rest;

import java.util.Optional;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.dawnrock.ddd.adapter.in.ExternalIdentities;

/**
 * Los tokens de acceso que emite Auth0 para la API (#197, ADR 0024). El {@code sub} se traduce al usuario local con la
 * vinculación de la web ({@link ExternalIdentities}, #196) y los roles salen de nuestra base de datos, así que
 * {@code CurrentCaller}, los roles de las URL y la propiedad de las cuentas no cambian. Un {@code sub} sin vincular, o
 * de un usuario local que ya no existe o está deshabilitado, se rechaza igual: {@link UnlinkedAccessTokenException}, 401
 * con {@link #UNLINKED_DETAIL}.
 */
public class Auth0AccessTokens implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Lo que se le dice a quien llega con un token de Auth0 sin usuario local: cómo conseguirlo. */
    public static final String UNLINKED_DETAIL = "La identidad de Auth0 no está vinculada a ningún usuario: entra una "
            + "vez en la web con Auth0 para vincularla";

    private final ExternalIdentities identities;

    private final UserDetailsService users;

    public Auth0AccessTokens(ExternalIdentities identities, UserDetailsService users) {
        this.identities = identities;
        this.users = users;
    }

    /** Fechas del token, emisor del tenant y audiencia de la API: un token para otra API del tenant no vale aquí. */
    public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        return JwtValidators.createDefaultWithValidators(new JwtIssuerValidator(issuer),
                new JwtAudienceValidator(audience));
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UserDetails local = identities.usernameOf(ExternalIdentities.AUTH0, jwt.getSubject())
                .flatMap(this::activeUser)
                .orElseThrow(UnlinkedAccessTokenException::new);
        return new JwtAuthenticationToken(jwt, local.getAuthorities(), local.getUsername());
    }

    private Optional<UserDetails> activeUser(String username) {
        try {
            UserDetails local = users.loadUserByUsername(username);
            boolean active = local.isEnabled() && local.isAccountNonLocked() && local.isAccountNonExpired();
            return active ? Optional.of(local) : Optional.empty();
        } catch (UsernameNotFoundException e) {
            return Optional.empty();
        }
    }
}
