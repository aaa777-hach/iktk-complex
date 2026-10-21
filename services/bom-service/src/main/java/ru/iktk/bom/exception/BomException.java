package ru.iktk.bom.exception;

/**
 * Исключение сервиса BOM.
 */
public class BomException extends RuntimeException {

    public enum Code {
        VALIDATION, NOT_FOUND, EMPTY_STRUCTURE, EXTRACTION_FAILED, INTERNAL
    }

    private final Code code;

    public BomException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public BomException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code getCode() { return code; }

    public org.springframework.http.HttpStatus getHttpStatus() {
        return switch (code) {
            case VALIDATION -> org.springframework.http.HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> org.springframework.http.HttpStatus.NOT_FOUND;
            case EMPTY_STRUCTURE, EXTRACTION_FAILED -> org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
            case INTERNAL -> org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public java.util.Map<String, Object> getContext() {
        return java.util.Map.of("code", code.name());
    }

    public static BomException validation(String message) {
        return new BomException(Code.VALIDATION, message);
    }

    public static BomException notFound(String message) {
        return new BomException(Code.NOT_FOUND, message);
    }

    public static BomException emptyStructure(String projectId) {
        return new BomException(Code.EMPTY_STRUCTURE,
                "Структура изделия не извлечена: projectId=" + projectId);
    }

    public static BomException extractionFailed(String projectId, String cadSystem, Throwable cause) {
        return new BomException(Code.EXTRACTION_FAILED,
                "Ошибка извлечения BOM: projectId=" + projectId + ", cad=" + cadSystem, cause);
    }

    public static BomException internal(String message, Throwable cause) {
        return new BomException(Code.INTERNAL, message, cause);
    }
}