package ru.iktk.auth.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Глобальный обработчик исключений auth.
 *
 * <p>{@link AuthException} содержит {@code httpStatus} (по умолчанию 401),
 * {@code code} и {@code context}. Возвращаем их клиенту.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuthException(AuthException ex) {
        log.info("AuthException: code={}, message={}, status={}",
                ex.getCode(), ex.getMessage(), ex.getHttpStatus());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("code", ex.getCode());
        body.put("message", ex.getMessage());
        body.put("context", ex.getContext());

        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }
}