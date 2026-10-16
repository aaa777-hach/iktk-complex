package ru.iktk.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Конфигурация маршрутов API Gateway ИКТК.
 *
 * <p>Вынесена из {@code GatewayApplication} для разделения ответственности:
 * <ul>
 *   <li>{@code GatewayApplication} — точка входа, CORS, общие настройки;</li>
 *   <li>{@code RoutesConfig} — маршруты, фильтры, rate limiting, fallback.</li>
 * </ul>
 *
 * <p><b>Важно:</b> при использовании этого класса bean {@code routes} в
 * {@code GatewayApplication} должен быть удалён во избежание дублирования
 * {@link RouteLocator}.
 *
 * <p>Маршруты:
 * <pre>
 *   /api/v1/auth/**            → auth-service
 *   /api/v1/orchestrator/**    → orchestrator
 *   /api/v1/equipment/**       → equipment-service
 *   /api/v1/bom/**             → bom-service
 *   /api/v1/assembly/**        → assembly-process-service
 *   /api/v1/recommendations/** → recommendation-service
 *   /fallback/**               → локальный обработчик fallback
 * </pre>
 *
 * <p>Фильтры на маршрутах:
 * <ul>
 *   <li>{@code StripPrefix(0)} — путь передаётся в сервис как есть;</li>
 *   <li>{@code CircuitBreaker} — защита от каскадных отказов;</li>
 *   <li>{@code Retry} — 2 повторные попытки при временных сбоях;</li>
 *   <li>{@code RequestRateLimiter} — ограничение частоты запросов (Redis);</li>
 *   <li>{@code AddResponseHeader} — трассировочные заголовки.</li>
 * </ul>
 *
 * <p><b>Зависимости:</b> для rate limiting требуется
 * {@code spring-boot-starter-data-redis-reactive} и запущенный Redis.
 */
@Configuration
public class RoutesConfig {

    private static final Logger log = LoggerFactory.getLogger(RoutesConfig.class);

    /** Имя заголовка с идентификатором пользователя. */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** Имя заголовка с идентификатором проекта. */
    public static final String HEADER_PROJECT_ID = "X-Project-Id";

    /** Имя заголовка с идентификатором запроса. */
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    /** Имя заголовка с корреляционным идентификатором. */
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    // ------------------------------------------------------------------
    // Маршруты
    // ------------------------------------------------------------------

    /**
     * Каноническая конфигурация маршрутов ИКТК.
     *
     * <p>Помечен {@link Primary}, чтобы при наличии нескольких
     * {@link RouteLocator} использовался именно этот.
     */
    @Bean
    @Primary
    public RouteLocator routes(RouteLocatorBuilder builder,
                               GatewayProperties props,
                               RedisRateLimiter redisRateLimiter) {
        log.info("RoutesConfig: конфигурация маршрутов ИКТК");

        return builder.routes()

                // ------------------------------------------------------
                // Аутентификация — без rate limiter (высокая частота)
                // ------------------------------------------------------
                .route("auth", r -> r
                        .path("/api/v1/auth/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .addResponseHeader(HEADER_REQUEST_ID, "${requestId}")
                        )
                        .uri(props.getAuthUri()))

                // ------------------------------------------------------
                // Оркестратор — основной маршрут
                // ------------------------------------------------------
                .route("orchestrator", r -> r
                        .path("/api/v1/orchestrator/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c
                                        .setName("orchestrator-cb")
                                        .setFallbackUri("forward:/fallback/orchestrator"))
                                .retry(retry -> retry
                                        .setRetries(2)
                                        .setStatuses(org.springframework.http.HttpStatus.BAD_GATEWAY,
                                                org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,
                                                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(userKeyResolver()))
                                .addResponseHeader(HEADER_REQUEST_ID, "${requestId}")
                        )
                        .uri(props.getOrchestratorUri()))

                // ------------------------------------------------------
                // Оборудование
                // ------------------------------------------------------
                .route("equipment-service", r -> r
                        .path("/api/v1/equipment/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c
                                        .setName("equipment-cb")
                                        .setFallbackUri("forward:/fallback/equipment"))
                                .retry(retry -> retry.setRetries(2))
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(userKeyResolver()))
                                .addResponseHeader(HEADER_REQUEST_ID, "${requestId}")
                        )
                        .uri(props.getEquipmentUri()))

                // ------------------------------------------------------
                // BOM
                // ------------------------------------------------------
                .route("bom-service", r -> r
                        .path("/api/v1/bom/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c
                                        .setName("bom-cb")
                                        .setFallbackUri("forward:/fallback/bom"))
                                .retry(retry -> retry.setRetries(2))
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(projectKeyResolver()))
                                .addResponseHeader(HEADER_REQUEST_ID, "${requestId}")
                        )
                        .uri(props.getBomUri()))

                // ------------------------------------------------------
                // ТП сборки
                // ------------------------------------------------------
                .route("assembly-process-service", r -> r
                        .path("/api/v1/assembly/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c
                                        .setName("assembly-cb")
                                        .setFallbackUri("forward:/fallback/assembly"))
                                .retry(retry -> retry.setRetries(2))
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(projectKeyResolver()))
                                .addResponseHeader(HEADER_REQUEST_ID, "${requestId}")
                        )
                        .uri(props.getAssemblyUri()))

                // ------------------------------------------------------
                // Рекомендации
                // ------------------------------------------------------
                .route("recommendation-service", r -> r
                        .path("/api/v1/recommendations/**")
                        .filters(f -> f
                                .stripPrefix(0)
                                .circuitBreaker(c -> c
                                        .setName("recommendation-cb")
                                        .setFallbackUri("forward:/fallback/recommendation"))
                                .retry(retry -> retry.setRetries(2))
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(projectKeyResolver()))
                                .addResponseHeader(HEADER_REQUEST_ID, "${requestId}")
                        )
                        .uri(props.getRecommendationUri()))

                // ------------------------------------------------------
                // Fallback (локальный обработчик)
                // ------------------------------------------------------
                .route("fallback", r -> r
                        .path("/fallback/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("forward:/fallback"))

                .build();
    }

    // ------------------------------------------------------------------
    // Rate limiter
    // ------------------------------------------------------------------

    /**
     * Redis-ограничитель частоты запросов.
     *
     * <p>Параметры (replenishRate, burstCapacity) можно вынести в
     * {@link GatewayProperties} для настройки без пересборки.
     */
    @Bean
    public RedisRateLimiter redisRateLimiter(GatewayProperties props) {
        int replenish = props.getRateLimitReplenish() > 0
                ? props.getRateLimitReplenish() : 100;
        int burst = props.getRateLimitBurst() > 0
                ? props.getRateLimitBurst() : 200;

        log.info("RoutesConfig: RedisRateLimiter replenishRate={}, burstCapacity={}",
                replenish, burst);

        return new RedisRateLimiter(replenish, burst, 1);
    }

    /**
     * Ключ rate limiter по пользователю.
     *
     * <p>Приоритет:
     * <ol>
     *   <li>заголовок {@code X-User-Id} (устанавливается AuthFilter);</li>
     *   <li>principal из SecurityContext;</li>
     *   <li>IP-адрес клиента (fallback).</li>
     * </ol>
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(HEADER_USER_ID);
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            return exchange.getPrincipal()
                    .map(p -> "user:" + p.getName())
                    .switchIfEmpty(Mono.just("ip:" + remoteAddress(exchange)));
        };
    }

    /**
     * Ключ rate limiter по проекту.
     *
     * <p>Используется для BOM, ТП сборки и рекомендаций — тяжёлых операций,
     * привязанных к проекту. Приоритет:
     * <ol>
     *   <li>заголовок {@code X-Project-Id};</li>
     *   <li>сегмент пути {@code /projects/{projectId}};</li>
     *   <li>IP-адрес клиента (fallback).</li>
     * </ol>
     */
    @Bean
    public KeyResolver projectKeyResolver() {
        return exchange -> {
            String projectId = exchange.getRequest().getHeaders().getFirst(HEADER_PROJECT_ID);
            if (projectId != null && !projectId.isBlank()) {
                return Mono.just("project:" + projectId);
            }

            String path = exchange.getRequest().getURI().getPath();
            String extracted = extractProjectId(path);
            if (extracted != null) {
                return Mono.just("project:" + extracted);
            }

            return Mono.just("ip:" + remoteAddress(exchange));
        };
    }

    // ------------------------------------------------------------------
    // Вспомогательные методы
    // ------------------------------------------------------------------

    /**
     * Извлечь {@code projectId} из пути вида {@code /api/v1/.../projects/{id}/...}.
     */
    private String extractProjectId(String path) {
        if (path == null) {
            return null;
        }
        String marker = "/projects/";
        int idx = path.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        int start = idx + marker.length();
        int end = path.indexOf('/', start);
        return end < 0 ? path.substring(start) : path.substring(start, end);
    }

    /**
     * IP-адрес клиента для fallback-ключа.
     */
    private String remoteAddress(org.springframework.web.server.ServerWebExchange exchange) {
        if (exchange.getRequest().getRemoteAddress() == null) {
            return "unknown";
        }
        return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }
}