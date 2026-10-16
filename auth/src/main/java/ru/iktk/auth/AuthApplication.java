package ru.iktk.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import ru.iktk.auth.config.AuthProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Точка входа сервиса аутентификации и авторизации ИКТК.
 *
 * <p>Сервис {@code auth} отвечает за:
 * <ul>
 *   <li>аутентификацию пользователей (логин/пароль, выпуск JWT);</li>
 *   <li>обновление и отзыв токенов (refresh, logout);</li>
 *   <li>ролевую модель доступа — RBAC ({@code RbacService});</li>
 *   <li>атрибутную модель доступа — ABAC ({@code AbacService});</li>
 *   <li>аудит действий пользователей ({@code AuditService});</li>
 *   <li>управление пользователями и ролями ({@code UserRepository}).</li>
 * </ul>
 *
 * <p>API Gateway (позиция 19, {@code AuthFilter}) проверяет JWT,
 * выпущенные этим сервисом. Формат claims согласован:
 * <pre>
 *   sub           — идентификатор пользователя (логин или UUID)
 *   roles         — список ролей (например, ["ENGINEER","ADMIN"])
 *   authorities   — альтернативное имя для roles
 *   iss           — издатель токена (iktk.security.jwt.issuer)
 *   iat / exp     — время выпуска и истечения
 * </pre>
 *
 * <p>Ключевые аннотации:
 * <ul>
 *   <li>{@link SpringBootApplication} — автоконфигурация Spring Boot;</li>
 *   <li>{@link EnableJpaRepositories} — JPA-репозитории для пользователей и ролей;</li>
 *   <li>{@link EntityScan} — сканирование сущностей;</li>
 *   <li>{@link EnableMethodSecurity} — {@code @PreAuthorize} на методах сервисов;</li>
 *   <li>{@link EnableAsync} — асинхронный аудит и уведомления;</li>
 *   <li>{@link EnableScheduling} — плановые задачи (ротация ключей, очистка сессий);</li>
 *   <li>{@link EnableFeignClients} — обращения к другим сервисам (при необходимости);</li>
 *   <li>{@link EnableConfigurationProperties} — типизированные настройки.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "ru.iktk.auth",
        "ru.iktk.core"
})
@EnableJpaRepositories(basePackages = "ru.iktk.auth.repository")
@EntityScan(basePackages = {
        "ru.iktk.auth.model",
        "ru.iktk.core.domain"
})
@EnableMethodSecurity(prePostEnabled = true)
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(AuthProperties.class)
public class AuthApplication {

    private static final Logger log = LoggerFactory.getLogger(AuthApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
                SpringApplication.run(AuthApplication.class, args);

        Environment env = ctx.getEnvironment();
        String appName = env.getProperty("spring.application.name", "auth");
        String port = env.getProperty("server.port", "8081");
        String profile = String.join(",", env.getActiveProfiles());
        String jwtIssuer = env.getProperty("iktk.security.jwt.issuer", "iktk");
        String jwtTtl = env.getProperty("iktk.security.jwt.access-ttl", "PT15M");

        log.info("""

                =========================================================
                  ИКТК: сервис аутентификации запущен
                  Приложение : {}
                  Профиль    : {}
                  Порт       : {}
                  Хост       : {}
                  JWT issuer : {}
                  Access TTL : {}
                  Внешний URL: {}
                =========================================================
                """,
                appName,
                profile.isEmpty() ? "default" : profile,
                port,
                hostname(),
                jwtIssuer,
                jwtTtl,
                localUrl(env, port));

        validateConfiguration(env);
    }

    // ------------------------------------------------------------------
    // Валидация конфигурации на старте
    // ------------------------------------------------------------------

    /**
     * Проверка критичных настроек безопасности.
     *
     * <p>Сервис не должен стартовать, если JWT-секрет не задан или слишком
     * короткий — иначе токены будут уязвимы для подбора.
     */
    private static void validateConfiguration(Environment env) {
        String secret = env.getProperty("iktk.security.jwt.secret", "");

        if (secret.isBlank()) {
            log.error("AuthApplication: не задан iktk.security.jwt.secret — аутентификация невозможна");
            throw new IllegalStateException(
                    "Не задан JWT-секрет (iktk.security.jwt.secret). " +
                    "Укажите значение в переменных окружения или в application.yml.");
        }

        if (secret.length() < 32) {
            log.error("AuthApplication: JWT-секрет короче 32 символов — небезопасно для HS256");
            throw new IllegalStateException(
                    "JWT-секрет должен быть не короче 32 байт (HS256). " +
                    "Текущая длина: " + secret.length());
        }

        log.info("AuthApplication: конфигурация безопасности проверена");
    }

    // ------------------------------------------------------------------
    // Утилиты
    // ------------------------------------------------------------------

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    private static String localUrl(Environment env, String port) {
        String scheme = env.getProperty("server.ssl.enabled", Boolean.class, false)
                ? "https" : "http";
        return scheme + "://localhost:" + port;
    }
}