package de.market.shared.rest;

import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void illegalArgumentShouldReturn400() {
        ResponseEntity<Map<String, String>> response =
                handler.handleBadRequest(new IllegalArgumentException("Feld X fehlt"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Feld X fehlt");
    }

    @Test
    void illegalStateShouldReturn409() {
        ResponseEntity<Map<String, String>> response =
                handler.handleConflict(new IllegalStateException("Name existiert"));

        assertThat(response.getStatusCodeValue()).isEqualTo(409);
        assertThat(response.getBody()).containsEntry("error", "Name existiert");
    }

    @Test
    void dataAccessShouldReturn500() {
        ResponseEntity<Map<String, String>> response =
                handler.handleDataAccessException(new DataAccessException("DB down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Interner Datenbankfehler");
    }

    @Test
    void sqlExceptionShouldReturn500() {
        ResponseEntity<Map<String, String>> response =
                handler.handleSqlException(new SQLException("Connection refused"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void accessDeniedShouldReturn403() {
        ResponseEntity<Map<String, String>> response =
                handler.handleAccessDenied(new AccessDeniedException("no perms"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("error", "Zugriff verweigert");
    }

    @Test
    void authenticationShouldReturn401() {
        ResponseEntity<Map<String, String>> response =
                handler.handleAuthentication(new AuthenticationException("expired") {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Nicht authentifiziert");
    }

    @Test
    void genericExceptionShouldReturn500() {
        ResponseEntity<Map<String, String>> response =
                handler.handleGeneric(new NullPointerException("oops"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Interner Server-Fehler");
    }
}
