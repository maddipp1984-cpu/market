package de.projekt.timeseries.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static String getCurrentUserId() {
        return getJwt().getSubject();
    }

    public static String getCurrentUsername() {
        return getJwt().getClaimAsString("preferred_username");
    }

    private static Jwt getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new IllegalStateException("Kein JWT im Security Context");
    }
}
