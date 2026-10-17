package ru.iktk.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import ru.iktk.gateway.config.GatewayProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Точка входа API Gateway ИКТК.
 *
 * <p>API Gateway — единая точка входа для всех клиентов (веб, Electron).
 * Отвечает за:
 * <ul>
 *   <li>маршрутизацию запросов к микросервисам комплекса
 *       (orchestrator, auth, equipment-service, bom-service,
 *       assembly-process-service, recommendation-service);</li>
 *   <li>проверку JWT-токенов (через {@code AuthFilter});</li>
 *   <li>CORS-политику для веб-клиента;</li>
 *   <li>rate limiting, retry, circuit breaker (на уровне маршрутов);</li>
 *   <li>трассировку через заголовки {@code X-Request-Id}, {@code X-Correlation-Id}.</li>
 * </ul>
 *
 * <p>Стек: Spring Cloud Gateway (реактивный, на базе WebFlux / Project Reactor).
 */
@SpringBootApplication(scanBasePackages = {
        "ru.iktk.gateway"
})
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayApplication {

    private static final Logger log = LoggerFactory.getLogger(GatewayApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
                SpringApplication.run(GatewayApplication.class, args);

        Environment env = ctx.getEnvironment();
        String appName = env.getProperty("spring.application.name", "gateway");
        String port = env.getProperty("server.port", "8080");
        String profile = String.join(",", env.getActiveProfiles());

        log.info("""

                =========================================================
                  ИКТК: API Gateway запущен
                  Приложение : {}
                  Профиль    : {}
                  Порт       : {}
                  Хост       : {}
                  Внешний URL: {}
                =========================================================
                """,
                appName,
                profile.isEmpty() ? "default" : profile,
                port,
                hostname(),
                localUrl(env, port));
    }

    // ------------------------------------------------------------------
    // Маршруты
    // ------------------------------------------------------------------

    /**
     * Программная конфигурация маршрутов к микросервисам ИКТК.
     *
     * <p>Каждый маршрут содержит:
     * <ul>
     *   <li>идентификатор;</li>
     *   <li>predicate по префиксу пути;</li>
     *   <li>фильтр {@code StripPrefix} для отсечения префикса;</li>
     *   <li>целевой URI (настраивается через {@link GatewayProperties}).</li>
     * </ul>
     *
     * <p>Маршруты:
     * <pre>
     *   /api/v1/orchestrator/** → orchestrator
     *   /api/v1/auth/**         → auth
     *   /api/v1/equipment/**    → equipment-service
     *   /api/v1/bom/**          → bom-service
     *   /api/v1/assembly/**     → assembly-process-service
     *   /api/v1/recommendations/** → recommendation-service
     * </pre>
     */
    // ------------------------------------------------------------------
    // CORS
    // ------------------------------------------------------------------

    /**
     * CORS-политика для веб-клиента и Electron-оболочки.
     *
     * <p>По умолчанию разрешены источники, перечисленные в
     * {@link GatewayProperties#getCorsAllowedOrigins()}.
     */
    @Bean
    public CorsWebFilter corsWebFilter(GatewayProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.getCorsAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(
                "X-Request-Id", "X-Correlation-Id", "X-Total-Count"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        log.info("GatewayApplication: CORS разрешён для origin={}",
                props.getCorsAllowedOrigins());

        return new CorsWebFilter(source);
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