package ru.iktk.recommendation.exception;

/**
 * Базовое исключение recommendation-service.
 */
public class RecommendationException extends RuntimeException {

    private final String code;

    public RecommendationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public RecommendationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static RecommendationException notFound(String message) {
        return new RecommendationException("NOT_FOUND", message);
    }

    public static RecommendationException validation(String message) {
        return new RecommendationException("VALIDATION_ERROR", message);
    }

    public static RecommendationException gapAnalysisFailed(String projectId, Throwable cause) {
        return new RecommendationException(
                "GAP_ANALYSIS_FAILED",
                "Gap analysis failed for project " + projectId + ": " + cause.getMessage(),
                cause);
    }

    public String getCode() {
        return code;
    }
}