package ru.iktk.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fallback-контроллер API Gateway ИКТК.
 *
 * <p>Принимает запросы, перенаправленные {@code CircuitBreaker} gateway,
 * когда downstream-сервис недоступен (connection refused, timeout, 5xx).
 *
 * <p>Маршруты: {@code /fallback/orchestrator}, {@code /fallback/equipment},
 * {@code /fallback/bom}, {@code /fallback/assembly}, {@code /fallback/recommendation},
 * {@code /fallback/casting}.
 *
 * <p>Ответ: HTTP {@code 503 Service Unavailable} + JSON:
 * <pre>
 * {
 *   "error": "SERVICE_UNAVAILABLE",
 *   "service": "orchestrator",
 *   "message": "Сервис временно недоступен: orchestrator",
 *   "requestId": "...",
 *   "timestamp": "2026-10-28T10:00:00Z"
 * }
 * </pre>
 */
@RestController
public class FallbackController {

    private static final Logger log = LoggerFactory.getLogger(FallbackController.class);

    /** Заголовок с идентификатором запроса (пробрасывается в ответ). */
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    /** Fallback-обработчик для недоступного downstream-сервиса. */
    @GetMapping("/fallback/{service}")
    public Mono<ResponseEntity<Map<String, Object>>> fallback(
            @PathVariable("service") String service,
            ServerWebExchange exchange) {

        String requestId = exchange.getRequest().getHeaders().getFirst(HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        log.warn("FallbackController: service={} unavailable, requestId={}", service, requestId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "SERVICE_UNAVAILABLE");
        body.put("service", service);
        body.put("message", "Сервис временно недоступен: " + service);
        body.put("requestId", requestId);
        body.put("timestamp", Instant.now().toString());

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HEADER_REQUEST_ID, requestId)
                .body(body));
    }
}