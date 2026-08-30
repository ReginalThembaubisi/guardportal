package com.propertysecurity.platform.exception;

import com.propertysecurity.platform.visitorentry.CheckInRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyAttempts(TooManyAttemptsException ex) {
        return body(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    /** Same shape as the other 400s, plus a stable `reason` a frontend can switch on instead of parsing `error`. */
    @ExceptionHandler(CheckInRejectedException.class)
    public ResponseEntity<Map<String, Object>> handleCheckInRejected(CheckInRejectedException ex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", LocalDateTime.now());
        payload.put("status", HttpStatus.BAD_REQUEST.value());
        payload.put("error", ex.getMessage());
        payload.put("reason", ex.getReason().name());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(payload);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return body(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", LocalDateTime.now());
        payload.put("status", HttpStatus.BAD_REQUEST.value());
        payload.put("error", "Validation failed");
        payload.put("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(payload);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", LocalDateTime.now());
        payload.put("status", status.value());
        payload.put("error", message);
        return ResponseEntity.status(status).body(payload);
    }
}
