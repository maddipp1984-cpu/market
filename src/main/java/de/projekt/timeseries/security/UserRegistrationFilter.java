package de.projekt.timeseries.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class UserRegistrationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationFilter.class);

    private final PermissionService permissionService;

    public UserRegistrationFilter(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            try {
                permissionService.registerUser(jwt);
            } catch (Exception e) {
                log.warn("User-Registrierung fehlgeschlagen: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
