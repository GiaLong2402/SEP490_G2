package com.hbsisms.security;

import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Turns a valid JWT into an authenticated {@link AuthUser}. The user is reloaded on every request so
 * that locking an account, changing its role or logging out takes effect immediately.
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public JwtAuthConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUser user = userRepository.findWithRoleById(parseUserId(jwt))
                .orElseThrow(() -> new InvalidBearerTokenException("User no longer exists"));
        if (!user.isActive()) {
            throw new InvalidBearerTokenException("User is locked");
        }
        Object version = jwt.getClaim(TokenService.VERSION_CLAIM);
        if (!(version instanceof Number number) || number.intValue() != user.getTokenVersion()) {
            throw new InvalidBearerTokenException("Token has been revoked");
        }
        AuthUser principal = AuthUser.from(user);
        return new UsernamePasswordAuthenticationToken(principal, jwt, principal.authorities());
    }

    private static Long parseUserId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw new InvalidBearerTokenException("Invalid subject");
        }
    }
}
