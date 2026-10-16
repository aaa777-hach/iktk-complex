package ru.iktk.auth.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class AuthException extends RuntimeException {
    private final String code;
    private final HttpStatus httpStatus;
    private final Map<String, Object> context;

    public AuthException(String code, String message) {
        this(code, message, HttpStatus.UNAUTHORIZED, Map.of());
    }

    public AuthException(String code, String message, HttpStatus httpStatus, Map<String, Object> context) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.context = context == null ? Map.of() : context;
    }

    public String getCode() { return code; }
    public HttpStatus getHttpStatus() { return httpStatus; }
    public Map<String, Object> getContext() { return context; }
}