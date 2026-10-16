package ru.iktk.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.iktk.auth.config.AuthProperties;
import ru.iktk.auth.dto.LoginRequest;
import ru.iktk.auth.dto.LoginResponse;
import ru.iktk.auth.dto.TokenResponse;
import ru.iktk.auth.dto.UserInfoResponse;
import ru.iktk.auth.exception.AuthException;
import ru.iktk.auth.model.User;
import ru.iktk.auth.repository.UserRepository;

import java.time.Instant;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthProperties authProperties;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.authProperties = authProperties;
    }

    // ------------------------------------------------------------------
    // login
    // ------------------------------------------------------------------

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (request == null
                || request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null) {
            throw new AuthException("INVALID_REQUEST", "Логин и пароль обязательны");
        }

        String username = request.getUsername().trim();

        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new AuthException(
                        "INVALID_CREDENTIALS", "Неверный логин или пароль"));

        if (!user.isEnabled()) {
            throw new AuthException("USER_DISABLED",
                    "Учётная запись отключена",
                    HttpStatus.FORBIDDEN, Map.of());
        }

        if (user.isLocked()) {
            throw new AuthException("USER_LOCKED",
                    "Учётная запись заблокирована",
                    HttpStatus.FORBIDDEN, Map.of());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            userRepository.incrementFailedAttempts(user.getId(), Instant.now());
            if (attempts >= authProperties.getMaxFailedAttempts()) {
                userRepository.lock(user.getId(), Instant.now());
                log.warn("login: user {} locked after {} failed attempts", username, attempts);
            }
            throw new AuthException("INVALID_CREDENTIALS", "Неверный логин или пароль");
        }

        userRepository.updateLastLogin(user.getId(), Instant.now());

        LoginResponse response = new LoginResponse();
        response.setAccessToken(tokenService.generateAccessToken(user));
        response.setRefreshToken(tokenService.generateRefreshToken(user));
        response.setExpiresIn(tokenService.getAccessTtlSeconds());
        response.setUserInfo(toUserInfo(user));
        return response;
    }

    // ------------------------------------------------------------------
    // refresh
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException("MISSING_TOKEN", "Отсутствует refresh-токен");
        }
        if (!tokenService.isValid(refreshToken)) {
            throw new AuthException("INVALID_TOKEN", "Невалидный или истёкший refresh-токен");
        }

        Map<String, Object> claims = tokenService.extractClaims(refreshToken);
        if (!"refresh".equals(claims.get("type"))) {
            throw new AuthException("INVALID_TOKEN_TYPE", "Ожидался refresh-токен");
        }

        String username = tokenService.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthException("INVALID_TOKEN", "Пользователь не найден"));

        if (!user.isEnabled() || user.isLocked()) {
            throw new AuthException("USER_DISABLED", "Учётная запись недоступна",
                    HttpStatus.FORBIDDEN, Map.of());
        }

        TokenResponse response = new TokenResponse();
        response.setAccessToken(tokenService.generateAccessToken(user));
        response.setRefreshToken(tokenService.generateRefreshToken(user));
        response.setExpiresIn(tokenService.getAccessTtlSeconds());
        response.setUsername(user.getUsername());
        return response;
    }

    // ------------------------------------------------------------------
    // logout
    // ------------------------------------------------------------------

    public void logout(String token) {
        // JWT stateless — отзыва нет. Для blacklist нужен Redis.
        log.debug("logout: token accepted, no server-side revocation (stateless JWT)");
    }

    // ------------------------------------------------------------------
    // get / change
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public UserInfoResponse getUserInfo(String userId, String roles) {
        User user = userRepository.findByUsername(userId)
                .or(() -> userRepository.findById(userId))
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND",
                        "Пользователь не найден", HttpStatus.NOT_FOUND, Map.of()));
        return toUserInfo(user);
    }

    @Transactional
    public void changePassword(String userId, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new AuthException("WEAK_PASSWORD",
                    "Пароль должен содержать не менее 6 символов");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("USER_NOT_FOUND",
                        "Пользователь не найден", HttpStatus.NOT_FOUND, Map.of()));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new AuthException("INVALID_PASSWORD", "Неверный текущий пароль");
        }

        userRepository.updatePassword(userId, passwordEncoder.encode(newPassword), Instant.now());
    }

    // ------------------------------------------------------------------

    private UserInfoResponse toUserInfo(User user) {
        UserInfoResponse info = new UserInfoResponse();
        info.setUserId(user.getId());
        info.setUsername(user.getUsername());
        info.setDisplayName(user.getDisplayName());
        info.setEmail(user.getEmail());
        info.setEnterpriseCode(user.getEnterpriseCode());
        info.setDepartmentCode(user.getDepartment());
        if (user.getRoles() != null) {
            info.setRoles(user.getRoles().stream()
                    .map(r -> r.getName().name())
                    .toList());
        }
        return info;
    }
}
