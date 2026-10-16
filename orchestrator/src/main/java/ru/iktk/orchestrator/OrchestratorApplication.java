package ru.iktk.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.iktk.orchestrator.config.OrchestratorProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Точка входа сервиса-оркестратора («дирижёра») ИКТК.
 *
 * <p>Оркестратор:
 * <ul>
 *   <li>анализирует состояние проекта и определяет следующий этап ТПП;</li>
 *   <li>передаёт управление профильным модулям и сервисам
 *       (casting, forging, machining, norming, bom, equipment, recommendation);</li>
 *   <li>поддерживает смешанный режим: автоматическое выполнение
 *       с точками контроля и возможностью ручного вмешательства;</li>
 *   <li>ведёт журнал решений и обеспечивает аудит;</li>
 *   <li>обрабатывает отказы внешних модулей: повтор, замена, пропуск с пометкой.</li>
 * </ul>
 *
 * <p>Ключевые аннотации:
 * <ul>
 *   <li>{@link SpringBootApplication} — автоконфигурация Spring Boot;</li>
 *   <li>{@link EnableAsync} — асинхронная обработка длительных операций;</li>
 *   <li>{@link EnableScheduling} — плановые задачи (health, ретраи, очистка);</li>
 *   <li>{@link EnableFeignClients} — декларативные REST-клиенты к модулям;</li>
 *   <li>{@link EnableConfigurationProperties} — типизированные настройки.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "ru.iktk.orchestrator",
        "ru.iktk.core"
})
@EnableAsync
@EnableScheduling
@EnableFeignClients(basePackages = "ru.iktk.orchestrator.client")
@EnableConfigurationProperties(OrchestratorProperties.class)
public class OrchestratorApplication {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
                SpringApplication.run(OrchestratorApplication.class, args);

        Environment env = ctx.getEnvironment();
        String appName = env.getProperty("spring.application.name", "orchestrator");
        String port = env.getProperty("server.port", "8080");
        String profile = String.join(",", env.getActiveProfiles());

        log.info("""

                =========================================================
                  ИКТК: сервис-оркестратор запущен
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