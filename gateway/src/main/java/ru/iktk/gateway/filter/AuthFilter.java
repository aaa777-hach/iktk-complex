package ru.iktk.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Глобальный фильтр аутентификации API Gateway ИКТК.
 *
 * <p>Назначение:
 * <ul>
 *   <li>проверка JWT-токена из заголовка {@code Authorization: Bearer &lt;token&gt;};</li>
 *   <li>пропуск публичных эндпоинтов (логин, health, документация);</li>
 *   <li>извлечение идентификатора пользователя и ролей из claims;</li>
 *   <li>проброс заголовков {@code X-User-Id}, {@code X-User-Roles},
 *       {@code X-Request-Id}, {@code X-Correlation-Id} в нижележащие сервисы;</li>
 *   <li>возврат 401 при отсутствии/невалидном токене.</li>
 * </ul>
 *
 * <p>Фильтр реализует {@link GlobalFilter} и {@link Ordered} — применяется ко
 * всем маршрутам до {@code RouteToRequestUrlFilter} (порядок {@code -100}).
 *
 * <p>Публичные пути (не требуют токена):
 * <pre>
 *   /api/v1/auth/login
 *   /api/v1/auth/refresh
 *   /actuator/health
 *   /actuator/info
 *   /v3/api-docs/**
 *   /swagger-ui/**
 *   /swagger-ui.html
 *   /fallback/**
 * </pre>
 */
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    /** Заголовок авторизации. */
    public static final String HEADER_AUTHORIZATION = HttpHeaders.AUTHORIZATION;

    /** Схема Bearer. */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Заголовки, пробрасываемые в сервисы. */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    /** Порядок фильтра: до маршрутизации, но после трассировки. */
    public static final int ORDER = -100;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** Публичные пути (без токена). */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/fallback/**"
    );

    private final ObjectMapper objectMapper;

    @Value("${iktk.security.jwt.secret:}")
    private String jwtSecret;

    @Value("${iktk.security.jwt.issuer:iktk}")
    private String jwtIssuer;

    public AuthFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // Основной метод фильтра
    // ------------------------------------------------------------------

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. Трассировочные заголовки (Request-Id, Correlation-Id)
        String requestId = firstNonNull(
                request.getHeaders().getFirst(HEADER_REQUEST_ID),
                UUID.randomUUID().toString());
        String correlationId = firstNonNull(
                request.getHeaders().getFirst(HEADER_CORRELATION_ID),
                requestId);

        // 2. Публичные пути — пропускаем без токена, но с трассировкой
        if (isPublic(path)) {
            log.debug("AuthFilter: публичный путь {}, пропуск проверки токена", path);
            return chain.filter(withTraceHeaders(exchange, requestId, correlationId, null, null));
        }

        // 3. Извлекаем токен
        String token = extractToken(request);
        if (token == null) {
            log.warn("AuthFilter: отсутствует токен, path={}, requestId={}", path, requestId);
            return unauthorized(exchange, "MISSING_TOKEN",
                    "Отсутствует заголовок Authorization: Bearer <token>", requestId);
        }

        // 4. Проверяем токен
        try {
            Claims claims = parseToken(token);

            String userId = claims.getSubject();
            String roles = extractRoles(claims);

            if (userId == null || userId.isBlank()) {
                log.warn("AuthFilter: токен без subject, path={}, requestId={}", path, requestId);
                return unauthorized(exchange, "INVALID_TOKEN",
                        "Токен не содержит идентификатора пользователя", requestId);
            }

            log.debug("AuthFilter: успешная аутентификация, userId={}, roles={}, path={}, requestId={}",
                    userId, roles, path, requestId);

            return chain.filter(withTraceHeaders(
                    exchange, requestId, correlationId, userId, roles));

        } catch (ExpiredJwtException e) {
            log.warn("AuthFilter: истёк токен, path={}, requestId={}", path, requestId);
            return unauthorized(exchange, "TOKEN_EXPIRED",
                    "Срок действия токена истёк", requestId);
        } catch (JwtException e) {
            log.warn("AuthFilter: невалидный токен, path={}, requestId={}: {}",
                    path, requestId, e.getMessage());
            return unauthorized(exchange, "INVALID_TOKEN",
                    "Невалидный токен", requestId);
        } catch (Exception e) {
            log.error("AuthFilter: ошибка проверки токена, path={}, requestId={}",
                    path, requestId, e);
            return unauthorized(exchange, "AUTH_ERROR",
                    "Ошибка аутентификации", requestId);
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    // ------------------------------------------------------------------
    // Проверка публичных путей
    // ------------------------------------------------------------------

    private boolean isPublic(String path) {
        if (path == null) {
            return false;
        }
        for (String pattern : PUBLIC_PATHS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Извлечение и парсинг токена
    // ------------------------------------------------------------------

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HEADER_AUTHORIZATION);
        if (header == null || header.isBlank()) {
            return null;
        }
        if (!header.startsWith(BEARER_PREFIX)) {
            log.debug("AuthFilter: заголовок Authorization не Bearer: {}",
                    header.substring(0, Math.min(header.length(), 16)) + "...");
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private Claims parseToken(String token) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new JwtException("Секрет JWT не настроен (iktk.security.jwt.secret)");
        }

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(jwtIssuer)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Извлечь роли из claims.
     * Поддерживаются: {@code roles} (List<String>) и {@code authorities} (List<String>).
     */
    @SuppressWarnings("unchecked")
    private String extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles == null) {
            roles = claims.get("authorities");
        }
        if (roles instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
        }
        if (roles instanceof String s) {
            return s;
        }
        return "";
    }

    // ------------------------------------------------------------------
    // Проброс заголовков
    // ------------------------------------------------------------------

    private ServerWebExchange withTraceHeaders(ServerWebExchange exchange,
                                               String requestId,
                                               String correlationId,
                                               String userId,
                                               String roles) {
        ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
                .header(HEADER_REQUEST_ID, requestId)
                .header(HEADER_CORRELATION_ID, correlationId);

        if (userId != null) {
            builder.header(HEADER_USER_ID, userId);
        }
        if (roles != null) {
            builder.header(HEADER_USER_ROLES, roles);
        }

        return exchange.mutate().request(builder.build()).build();
    }

    // ------------------------------------------------------------------
    // Ответ 401
    // ------------------------------------------------------------------

    private Mono<Void> unauthorized(ServerWebExchange exchange,
                                    String code,
                                    String message,
                                    String requestId) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HEADER_REQUEST_ID, requestId);

        Map<String, Object> body = Map.of(
                "error", "UNAUTHORIZED",
                "code", code,
                "message", message,
                "requestId", requestId,
                "timestamp", Instant.now().toString()
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("AuthFilter: ошибка формирования 401-ответа", e);
            return response.setComplete();
        }
    }

    // ------------------------------------------------------------------
    // Утилиты
    // ------------------------------------------------------------------

    private String firstNonNull(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}