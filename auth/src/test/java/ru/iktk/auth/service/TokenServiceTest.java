package ru.iktk.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.iktk.auth.model.Role;
import ru.iktk.auth.model.RoleName;
import ru.iktk.auth.model.User;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты TokenService (JWT: HS256, jjwt).
 *
 * <p>Конструктор с {@code @Value} — в тесте передаём secret/issuer/TTL напрямую.
 * Secret ≥ 32 символа (проверка в коде).
 */
class TokenServiceTest {

    private static final String SECRET =
            "test-jwt-secret-at-least-32-characters-long-0123456789-extra";
    private static final String ISSUER = "iktk";
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    private TokenService tokenService;
    private User user;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(SECRET, ISSUER, ACCESS_TTL, REFRESH_TTL);

        user = new User();
        ReflectionTestUtils.setField(user, "id", "user-123");
        ReflectionTestUtils.setField(user, "username", "admin");
        ReflectionTestUtils.setField(user, "enabled", true);
        ReflectionTestUtils.setField(user, "locked", false);

        Role adminRole = new Role(RoleName.ADMIN);
        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);
        ReflectionTestUtils.setField(user, "roles", roles);
    }

    @Test
    void generateAccessToken_containsSubjectIssuerRolesType() {
        String token = tokenService.generateAccessToken(user);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature

        Map<String, Object> claims = tokenService.extractClaims(token);
        assertThat(claims.get("sub")).isEqualTo("admin");
        assertThat(claims.get("iss")).isEqualTo(ISSUER);
        assertThat(claims.get("uid")).isEqualTo("user-123");
        assertThat(claims.get("type")).isEqualTo("access");
        assertThat(claims.get("roles")).isEqualTo(java.util.List.of("ADMIN"));
        assertThat(claims.get("exp")).isNotNull();
    }

    @Test
    void generateRefreshToken_hasRefreshType() {
        String token = tokenService.generateRefreshToken(user);

        Map<String, Object> claims = tokenService.extractClaims(token);
        assertThat(claims.get("type")).isEqualTo("refresh");
        assertThat(claims.get("sub")).isEqualTo("admin");
    }

    @Test
    void extractUsername_returnsSubject() {
        String token = tokenService.generateAccessToken(user);
        assertThat(tokenService.extractUsername(token)).isEqualTo("admin");
    }

    @Test
    void isValid_trueForFreshToken_falseForGarbage() {
        String token = tokenService.generateAccessToken(user);
        assertThat(tokenService.isValid(token)).isTrue();
        assertThat(tokenService.isValid("garbage.token.value")).isFalse();
        assertThat(tokenService.isValid("")).isFalse();
        assertThat(tokenService.isValid(null)).isFalse();
    }

    @Test
    void getAccessTtlSeconds_returnsConfiguredTtl() {
        assertThat(tokenService.getAccessTtlSeconds()).isEqualTo(15 * 60L);
    }

    @Test
    void constructor_rejectsShortSecret() {
        assertThatThrownBy(() -> new TokenService(
                "short", ISSUER, ACCESS_TTL, REFRESH_TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT secret");
    }

    @Test
    void expiredToken_isInvalid() throws InterruptedException {
        // access-ttl = 1 секунда
        TokenService shortLived = new TokenService(SECRET, ISSUER,
                Duration.ofSeconds(1), REFRESH_TTL);
        String token = shortLived.generateAccessToken(user);
        assertThat(shortLived.isValid(token)).isTrue();

        Thread.sleep(1500);
        assertThat(shortLived.isValid(token)).isFalse();
    }
}