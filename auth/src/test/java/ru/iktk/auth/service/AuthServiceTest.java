package ru.iktk.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.iktk.auth.config.AuthProperties;
import ru.iktk.auth.dto.LoginRequest;
import ru.iktk.auth.dto.LoginResponse;
import ru.iktk.auth.dto.TokenResponse;
import ru.iktk.auth.exception.AuthException;
import ru.iktk.auth.model.Role;
import ru.iktk.auth.model.RoleName;
import ru.iktk.auth.model.User;
import ru.iktk.auth.repository.UserRepository;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты AuthService (login, refresh, changePassword).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET =
            "test-jwt-secret-at-least-32-characters-long-0123456789-extra";
    private static final String ISSUER = "iktk";

    @Mock private UserRepository userRepository;

    private PasswordEncoder passwordEncoder;
    private TokenService tokenService;
    private AuthProperties authProperties;
    private AuthService authService;

    private User user;
    private String rawPassword = "admin123";
    private String passwordHash;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4); // cost=4 — быстро для тестов
        passwordHash = passwordEncoder.encode(rawPassword);

        tokenService = new TokenService(SECRET, ISSUER,
                Duration.ofMinutes(15), Duration.ofDays(30));

        authProperties = new AuthProperties(); // maxFailedAttempts=5, lock=30

        authService = new AuthService(userRepository, passwordEncoder,
                tokenService, authProperties);

        user = new User();
        user.setId("user-123");
        user.setUsername("admin");
        user.setPasswordHash(passwordHash);
        user.setEnabled(true);
        user.setLocked(false);
        user.setFailedAttempts(0);
        Set<Role> roles = new HashSet<>();
        roles.add(new Role(RoleName.ADMIN));
        user.setRoles(roles);
    }

    private LoginRequest req(String username, String password) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }

    // ------------------------------------------------------------------
    // login
    // ------------------------------------------------------------------

    @Test
    void login_happyPath() {
        when(userRepository.findByUsernameOrEmail("admin", "admin"))
                .thenReturn(Optional.of(user));

        LoginResponse response = authService.login(req("admin", rawPassword));

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getExpiresIn()).isEqualTo(900);
        assertThat(response.getUserInfo().getUsername()).isEqualTo("admin");
        assertThat(response.getUserInfo().getRoles()).containsExactly("ADMIN");

        verify(userRepository).updateLastLogin(eq("user-123"), any());
    }

    @Test
    void login_nullRequest_throwsInvalidRequest() {
        assertThatThrownBy(() -> authService.login(null))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getCode()).isEqualTo("INVALID_REQUEST");
                    assertThat(ae.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    @Test
    void login_missingUsername_throwsInvalidRequest() {
        assertThatThrownBy(() -> authService.login(req(null, "x")))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Логин и пароль");
    }

    @Test
    void login_userNotFound_throwsInvalidCredentials() {
        when(userRepository.findByUsernameOrEmail("ghost", "ghost"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req("ghost", "any")))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void login_wrongPassword_throwsIncrementsFailedAttempts() {
        when(userRepository.findByUsernameOrEmail("admin", "admin"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(req("admin", "wrong")))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo("INVALID_CREDENTIALS"));

        verify(userRepository).incrementFailedAttempts(eq("user-123"), any());
        verify(userRepository, never()).lock(anyString(), any());
    }

    @Test
    void login_wrongPassword_locksAfterMaxAttempts() {
        user.setFailedAttempts(4); // +1 = 5 = max
        when(userRepository.findByUsernameOrEmail("admin", "admin"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(req("admin", "wrong")))
                .isInstanceOf(AuthException.class);

        verify(userRepository).lock(eq("user-123"), any());
    }

    @Test
    void login_disabledUser_throwsForbidden() {
        user.setEnabled(false);
        when(userRepository.findByUsernameOrEmail("admin", "admin"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(req("admin", rawPassword)))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getCode()).isEqualTo("USER_DISABLED");
                    assertThat(ae.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void login_lockedUser_throwsForbidden() {
        user.setLocked(true);
        when(userRepository.findByUsernameOrEmail("admin", "admin"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(req("admin", rawPassword)))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getCode()).isEqualTo("USER_LOCKED");
                    assertThat(ae.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    // ------------------------------------------------------------------
    // refresh
    // ------------------------------------------------------------------

    @Test
    void refresh_happyPath_rotatesTokens() {
        String refreshToken = tokenService.generateRefreshToken(user);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        TokenResponse response = authService.refresh(refreshToken);

        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getAccessToken()).isNotEqualTo(refreshToken);
        assertThat(response.getExpiresIn()).isEqualTo(900);
        assertThat(response.getUsername()).isEqualTo("admin");
    }

    @Test
    void refresh_nullToken_throwsMissingToken() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo("MISSING_TOKEN"));
    }

    @Test
    void refresh_accessTokenInsteadOfRefresh_throwsWrongType() {
        // access-токен вместо refresh -> AuthService.refresh падает на type!=refresh
        // ДО вызова userRepository.findByUsername — не нужно stubbing
        String accessToken = tokenService.generateAccessToken(user);

        assertThatThrownBy(() -> authService.refresh(accessToken))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo("INVALID_TOKEN_TYPE"));
    }

    // ------------------------------------------------------------------
    // changePassword
    // ------------------------------------------------------------------

    @Test
    void changePassword_weakNewPassword_throws() {
        assertThatThrownBy(() -> authService.changePassword("user-123", "old", "123"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo("WEAK_PASSWORD"));

        verify(userRepository, never()).findById(anyString());
    }

    @Test
    void changePassword_wrongOldPassword_throws() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changePassword("user-123", "wrong", "newpass123"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getCode())
                        .isEqualTo("INVALID_PASSWORD"));

        verify(userRepository, never()).updatePassword(anyString(), anyString(), any());
    }

    @Test
    void changePassword_happyPath_updatesPassword() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(user));

        authService.changePassword("user-123", rawPassword, "newpass123");

        verify(userRepository).updatePassword(eq("user-123"), anyString(), any());
    }

    @Test
    void changePassword_userNotFound_throws() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword("ghost", "old", "newpass123"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> {
                    AuthException ae = (AuthException) e;
                    assertThat(ae.getCode()).isEqualTo("USER_NOT_FOUND");
                    assertThat(ae.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }
}