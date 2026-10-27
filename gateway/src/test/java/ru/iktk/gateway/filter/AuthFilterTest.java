package ru.iktk.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthFilter — JWT-аутентификация на gateway")
class AuthFilterTest {

    private static final String SECRET =
            "iktk-dev-secret-change-in-production-at-least-32-chars-long-0123456789";
    private static final String ISSUER = "iktk";

    private AuthFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new AuthFilter(objectMapper);
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(filter, "jwtIssuer", ISSUER);
    }

    private String token(String sub, List<String> roles, Instant exp, String issuer) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(sub)
                .setIssuer(issuer)
                .setExpiration(Date.from(exp))
                .claim("roles", roles)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private String validToken(String sub, List<String> roles) {
        return token(sub, roles, Instant.now().plusSeconds(600), ISSUER);
    }

    private ServerWebExchange run(ServerWebExchange exchange) {
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        GatewayFilterChain chain = ex -> { seen.set(ex); return Mono.empty(); };
        filter.filter(exchange, chain).block();
        return seen.get();
    }

    @Test
    @DisplayName("public /api/v1/auth/login — без токена пропускается")
    void public_login() {
        var ex = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login"));
        assertThat(run(ex)).isNotNull();
    }

    @Test
    @DisplayName("public /api/v1/auth/refresh — без токена пропускается")
    void public_refresh() {
        var ex = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/refresh"));
        assertThat(run(ex)).isNotNull();
    }

    @Test
    @DisplayName("public /api/v1/auth/introspect — без токена пропускается")
    void public_introspect() {
        var ex = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/introspect"));
        assertThat(run(ex)).isNotNull();
    }

    @Test
    @DisplayName("public /actuator/health — без токена пропускается")
    void public_health() {
        var ex = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health"));
        assertThat(run(ex)).isNotNull();
    }

    @Test
    @DisplayName("нет Authorization — 401, chain не вызван")
    void missing_token() {
        var ex = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/me"));
        assertThat(run(ex)).isNull();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Authorization без Bearer — 401")
    void not_bearer() {
        var ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Token abc"));
        assertThat(run(ex)).isNull();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("мусорный токен — 401")
    void garbage_token() {
        var ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"));
        assertThat(run(ex)).isNull();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("просроченный токен — 401")
    void expired_token() {
        String expired = token("u-1", List.of("MANAGER"), Instant.now().minusSeconds(60), ISSUER);
        var ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired));
        assertThat(run(ex)).isNull();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("чужой issuer — 401")
    void wrong_issuer() {
        String t = token("u-1", List.of("MANAGER"), Instant.now().plusSeconds(600), "other");
        var ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + t));
        assertThat(run(ex)).isNull();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("валидный токен — chain вызван, X-User-Id и X-User-Roles проброшены")
    void valid_token_injects_headers() {
        String t = validToken("22222222-2222-2222-2222-222222222222", List.of("MANAGER", "ENGINEER"));
        var ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + t));

        ServerWebExchange forwarded = run(ex);

        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getRequest().getHeaders().getFirst(AuthFilter.HEADER_USER_ID))
                .isEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(forwarded.getRequest().getHeaders().getFirst(AuthFilter.HEADER_USER_ROLES))
                .isEqualTo("MANAGER,ENGINEER");
        assertThat(forwarded.getRequest().getHeaders().getFirst(AuthFilter.HEADER_REQUEST_ID)).isNotBlank();
        assertThat(forwarded.getRequest().getHeaders().getFirst(AuthFilter.HEADER_CORRELATION_ID))
                .isEqualTo(forwarded.getRequest().getHeaders().getFirst(AuthFilter.HEADER_REQUEST_ID));
    }
}