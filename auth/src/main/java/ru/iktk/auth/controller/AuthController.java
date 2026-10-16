package ru.iktk.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.iktk.auth.audit.AuditService;
import ru.iktk.auth.dto.LoginRequest;
import ru.iktk.auth.dto.LoginResponse;
import ru.iktk.auth.dto.RefreshRequest;
import ru.iktk.auth.dto.TokenResponse;
import ru.iktk.auth.dto.UserInfoResponse;
import ru.iktk.auth.dto.ChangePasswordRequest;
import ru.iktk.auth.exception.AuthException;
import ru.iktk.auth.service.AuthService;
import ru.iktk.auth.service.TokenService;

import java.util.Map;

/**
 * REST API сервиса аутентификации и авторизации ИКТК.
 *
 * <p>Предоставляет:
 * <ul>
 *   <li>вход в систему — {@code POST /api/v1/auth/login};</li>
 *   <li>обновление access-токена — {@code POST /api/v1/auth/refresh};</li>
 *   <li>выход и отзыв токена — {@code POST /api/v1/auth/logout};</li>
 *   <li>проверку токена — {@code POST /api/v1/auth/introspect};</li>
 *   <li>информацию о текущем пользователе — {@code GET /api/v1/auth/me};</li>
 *   <li>смену пароля — {@code POST /api/v1/auth/change-password};</li>
 * </ul>
 *
 * <p>Базовый путь: {@code /api/v1/auth}.
 *
 * <p>Публичные эндпоинты (без токена): {@code login}, {@code refresh},
 * {@code logout}. Остальные требуют валидного JWT.
 *
 * <p>Все действия пользователя фиксируются в аудите через
 * {@link AuditService} — успешные и неуспешные попытки входа,
 * смены пароля, отзыва токена.
 */
@RestController
@RequestMapping(path = "/api/v1/auth",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Auth", description = "Аутентификация и авторизация ИКТК")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** Заголовок для передачи access-токена клиентом. */
    private static final String HEADER_AUTHORIZATION = "Authorization";

    /** Заголовок для трассировки. */
    private static final String HEADER_REQUEST_ID = "X-Request-Id";

    private final AuthService authService;
    private final TokenService tokenService;
    private final AuditService auditService;

    public AuthController(AuthService authService,
                          TokenService tokenService,
                          AuditService auditService) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Вход
    // ------------------------------------------------------------------

    /**
     * Вход в систему по логину/паролю.
     *
     * <p>Возвращает пару токенов: access (короткоживущий) и refresh
     * (долгоживущий), а также базовую информацию о пользователе.
     */
    @PostMapping(path = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Вход в систему",
            description = "Аутентификация по логину/паролю, выдача JWT-токенов")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String requestId = requestId(httpRequest);
        String clientIp = clientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        log.info("login: username={}, requestId={}, ip={}",
                request.getUsername(), requestId, clientIp);

        try {
            LoginResponse response = authService.login(request);

            auditService.logSuccess(
                    request.getUsername(),
                    "LOGIN",
                    Map.of(
                            "requestId", requestId,
                            "ip", clientIp,
                            "userAgent", userAgent == null ? "" : userAgent
                    ));

            log.info("login OK: username={}, requestId={}", request.getUsername(), requestId);
            return ResponseEntity.ok(response);

        } catch (AuthException e) {
            auditService.logFailure(
                    request.getUsername(),
                    "LOGIN",
                    Map.of(
                            "requestId", requestId,
                            "ip", clientIp,
                            "reason", e.getCode()
                    ));

            log.warn("login FAIL: username={}, requestId={}, reason={}",
                    request.getUsername(), requestId, e.getCode());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // Обновление токена
    // ------------------------------------------------------------------

    /**
     * Обновление access-токена по refresh-токену.
     */
    @PostMapping(path = "/refresh",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Обновить access-токен",
            description = "Принимает refresh-токен, возвращает новую пару токенов")
    public ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {

        String requestId = requestId(httpRequest);
        log.info("refresh: requestId={}", requestId);

        try {
            TokenResponse response = authService.refresh(request.getRefreshToken());

            auditService.logSuccess(
                    response.getUsername(),
                    "REFRESH_TOKEN",
                    Map.of("requestId", requestId));

            return ResponseEntity.ok(response);

        } catch (AuthException e) {
            auditService.logFailure(
                    "unknown",
                    "REFRESH_TOKEN",
                    Map.of("requestId", requestId, "reason", e.getCode()));
            log.warn("refresh FAIL: requestId={}, reason={}", requestId, e.getCode());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // Выход
    // ------------------------------------------------------------------

    /**
     * Выход из системы: отзыв текущего access-токена
     * и связанного refresh-токена.
     */
    @PostMapping(path = "/logout")
    @Operation(summary = "Выход из системы",
            description = "Отзывает текущий access-токен и связанный refresh-токен")
    public ResponseEntity<Map<String, Object>> logout(
            @RequestHeader(value = HEADER_AUTHORIZATION, required = false) String authorization,
            HttpServletRequest httpRequest) {

        String requestId = requestId(httpRequest);
        String token = extractToken(authorization);

        if (token == null) {
            log.warn("logout: отсутствует токен, requestId={}", requestId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "UNAUTHORIZED",
                            "code", "MISSING_TOKEN",
                            "message", "Отсутствует заголовок Authorization",
                            "requestId", requestId));
        }

        String username = tokenService.extractUsername(token);

        try {
            authService.logout(token);

            auditService.logSuccess(
                    username,
                    "LOGOUT",
                    Map.of("requestId", requestId));

            log.info("logout OK: username={}, requestId={}", username, requestId);

            return ResponseEntity.ok(Map.of(
                    "status", "OK",
                    "message", "Токен отозван",
                    "requestId", requestId));

        } catch (AuthException e) {
            log.warn("logout FAIL: username={}, requestId={}, reason={}",
                    username, requestId, e.getCode());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // Проверка токена (introspection)
    // ------------------------------------------------------------------

    /**
     * Проверка валидности токена.
     *
     * <p>Используется API Gateway и другими сервисами для проверки
     * access-токена без его повторного парсинга.
     */
    @PostMapping(path = "/introspect")
    @Operation(summary = "Проверить токен",
            description = "Возвращает статус токена, пользователя и его роли")
    public ResponseEntity<Map<String, Object>> introspect(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {

        String requestId = requestId(httpRequest);
        String token = body == null ? null : body.get("token");

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "active", false,
                    "error", "MISSING_TOKEN",
                    "requestId", requestId));
        }

        boolean active = tokenService.isValid(token);
        if (!active) {
            return ResponseEntity.ok(Map.of(
                    "active", false,
                    "requestId", requestId));
        }

        Map<String, Object> claims = tokenService.extractClaims(token);

        return ResponseEntity.ok(Map.of(
                "active", true,
                "username", claims.getOrDefault("sub", ""),
                "roles", claims.getOrDefault("roles", ""),
                "issuer", claims.getOrDefault("iss", ""),
                "expiresAt", claims.getOrDefault("exp", ""),
                "requestId", requestId));
    }

    // ------------------------------------------------------------------
    // Информация о текущем пользователе
    // ------------------------------------------------------------------

    /**
     * Информация о текущем аутентифицированном пользователе.
     */
    @GetMapping(path = "/me")
    @Operation(summary = "Информация о текущем пользователе")
    public ResponseEntity<UserInfoResponse> me(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            HttpServletRequest httpRequest) {

        String requestId = requestId(httpRequest);
        log.debug("me: userId={}, requestId={}", userId, requestId);

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserInfoResponse info = authService.getUserInfo(userId, roles);
        return ResponseEntity.ok(info);
    }

    // ------------------------------------------------------------------
    // Смена пароля
    // ------------------------------------------------------------------

    /**
     * Смена пароля текущим пользователем.
     */
    @PostMapping(path = "/change-password",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Сменить пароль",
            description = "Смена пароля текущего пользователя")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestHeader(value = "X-User-Id") String userId,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {

        String requestId = requestId(httpRequest);
        log.info("changePassword: userId={}, requestId={}", userId, requestId);

        try {
            authService.changePassword(userId, request.getOldPassword(), request.getNewPassword());

            auditService.logSuccess(
                    userId,
                    "CHANGE_PASSWORD",
                    Map.of("requestId", requestId));

            return ResponseEntity.ok(Map.of(
                    "status", "OK",
                    "message", "Пароль изменён",
                    "requestId", requestId));

        } catch (AuthException e) {
            auditService.logFailure(
                    userId,
                    "CHANGE_PASSWORD",
                    Map.of("requestId", requestId, "reason", e.getCode()));
            log.warn("changePassword FAIL: userId={}, reason={}", userId, e.getCode());
            throw e;
        }
    }

    // ------------------------------------------------------------------
    // Health и служебные
    // ------------------------------------------------------------------

    /**
     * Проверка доступности сервиса аутентификации.
     */
    @GetMapping(path = "/health")
    @Operation(summary = "Проверка доступности сервиса")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "auth",
                "timestamp", java.time.Instant.now().toString()));
    }

    // ------------------------------------------------------------------
    // Вспомогательные методы
    // ------------------------------------------------------------------

    private String requestId(HttpServletRequest request) {
        String id = request.getHeader(HEADER_REQUEST_ID);
        return (id == null || id.isBlank()) ? java.util.UUID.randomUUID().toString() : id;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
