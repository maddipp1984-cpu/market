package de.market.publicapi;

import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.Map;

@RestControllerAdvice(basePackages = "de.market.publicapi")
public class PublicApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PublicApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Bad Request",
                "message", ex.getMessage(),
                "status", 400
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(409).body(Map.of(
                "error", "Conflict",
                "message", ex.getMessage(),
                "status", 409
        ));
    }

    @ExceptionHandler({DataAccessException.class, SQLException.class})
    public ResponseEntity<Map<String, Object>> handleDatabaseError(Exception ex) {
        log.error("Public API Datenbankfehler", ex);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Internal Server Error",
                "message", "Interner Datenbankfehler",
                "status", 500
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Public API Fehler", ex);
        return ResponseEntity.internalServerError().body(Map.of(
                "error", "Internal Server Error",
                "message", "Ein interner Fehler ist aufgetreten",
                "status", 500
        ));
    }
}
