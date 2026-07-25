package com.tasktracker.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    record ErrorResponse(int status, String error, String message, String path, OffsetDateTime timestamp) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex,
                                              HttpServletRequest req) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
            errors.put(field, err.getDefaultMessage());
        });
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "error", "Validation Failed",
                "errors", errors,
                "path", req.getRequestURI(),
                "timestamp", OffsetDateTime.now()
        ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, "Not Found", ex.getMessage(),
                        req.getRequestURI(), OffsetDateTime.now()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex,
                                                        HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, "Conflict", ex.getMessage(),
                        req.getRequestURI(), OffsetDateTime.now()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex,
                                                           HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, "Bad Request", ex.getMessage(),
                        req.getRequestURI(), OffsetDateTime.now()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex,
                                                              HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(401, "Unauthorized", "Invalid email or password",
                        req.getRequestURI(), OffsetDateTime.now()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(403, "Forbidden", "Access denied",
                        req.getRequestURI(), OffsetDateTime.now()));
    }

    /**
     * Catch-all handler — logs the full exception server-side, returns a GENERIC
     * message to the client so internal details (stack traces, DB messages, etc.)
     * are never exposed over the wire.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "Internal Server Error",
                        "An unexpected error occurred. Please try again later.",
                        req.getRequestURI(), OffsetDateTime.now()));
    }
}
