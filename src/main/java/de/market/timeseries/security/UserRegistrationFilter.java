package de.market.timeseries.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserRegistrationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationFilter.class);

    private final PermissionService permissionService;
    private final Set<String> knownUsers = ConcurrentHashMap.newKeySet();

    public UserRegistrationFilter(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();

            // MDC fuer User-spezifisches Logging
            String username = jwt.getClaimAsString("preferred_username");
            String sessionState = jwt.getClaimAsString("session_state");
            if (username != null) {
                MDC.put("username", username);
            }
            if (sessionState != null) {
                String sessionShort = sessionState.length() > 8
                        ? sessionState.substring(0, 8) : sessionState;
                MDC.put("sessionId", sessionShort);
                // Discriminator fuer SiftingAppender: ein File pro User + Session
                MDC.put("userSession", username + "_" + sessionShort);
            }
            // Request-Kontext fuer besseres Debugging
            MDC.put("method", request.getMethod());
            MDC.put("uri", request.getRequestURI());

            if (subject != null && knownUsers.add(subject)) {
                try {
                    permissionService.registerUser(jwt);
                } catch (Exception e) {
                    knownUsers.remove(subject);
                    log.warn("User-Registrierung fehlgeschlagen: {}", e.getMessage());
                }
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("username");
            MDC.remove("sessionId");
            MDC.remove("userSession");
            MDC.remove("method");
            MDC.remove("uri");
        }
    }
}
