package ru.iktk.recommendation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.iktk.recommendation.config.RecommendationProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Точка входа сервиса рекомендаций ИКТК.
 *
 * <p>Сервис {@code recommendation-service} отвечает за:
 * <ul>
 *   <li>gap-анализ: сопоставление требований ТП с парком
 *       оборудования и выявление дефицита;</li>
 *   <li>формирование рекомендаций при дефиците:
 *       альтернативное имеющееся оборудование, докупка, аренда,
 *       аутсорс, корректировка ТП;</li>
 *   <li>скоринг вариантов по производительности, загрузке,
 *       стоимости, срокам;</li>
 *   <li>поиск в каталоге внешнего оборудования
 *       ({@code vendor_catalog});</li>
 *   <li>формирование вариантов аутсорса
 *       ({@code outsourcing_options});</li>
 *   <li>предоставление рекомендаций для точки контроля
 *       {@code RECOMMENDATION_REVIEW} оркестратора.</li>
 * </ul>
 *
 * <p><b>Модель данных.</b> Ключевые таблицы (миграция V6):
 * <ul>
 *   <li>{@code equipment_requirements} — требования к оборудованию
 *       из ТП;</li>
 *   <li>{@code equipment_gap_analysis} — результаты gap-анализа;</li>
 *   <li>{@code equipment_recommendations} — рекомендации;</li>
 *   <li>{@code vendor_catalog} — каталог внешнего оборудования;</li>
 *   <li>{@code outsourcing_options} — варианты аутсорса.</li>
 * </ul>
 *
 * <p><b>Взаимодействие с оркестратором.</b> Сервис вызывается
 * оркестратором (позиция 4) на этапе
 * {@code EQUIPMENT_GAP_RESOLUTION}. Команда содержит
 * {@code projectId}, {@code state}, {@code payload} с требованиями
 * к оборудованию. Результат публикуется с событием
 * {@code RECOMMENDATIONS_READY}.
 *
 * <p><b>Взаимодействие с другими сервисами.</b>
 * <ul>
 *   <li>{@code orchestrator} — заказчик gap-анализа и рекомендаций;</li>
 *   <li>{@code equipment-service} (позиции 72–79) — источник данных
 *       о парке предприятия;</li>
 *   <li>{@code casting}, {@code forging}, {@code machining} — источники
 *       требований к оборудованию (через {@code stage_equipment_requirements});</li>
 *   <li>{@code postgres} — хранилище рекомендаций;</li>
 *   <li>{@code rabbitmq} — обмен командами и результатами.</li>
 * </ul>
 *
 * <p>Ключевые аннотации:
 * <ul>
 *   <li>{@link SpringBootApplication} — автоконфигурация Spring Boot;</li>
 *   <li>{@link EnableJpaRepositories} — JPA-репозитории рекомендаций;</li>
 *   <li>{@link EntityScan} — сущности рекомендаций и общие доменные модели;</li>
 *   <li>{@link EnableAsync} — асинхронный gap-анализ (может быть длительным);</li>
 *   <li>{@link EnableScheduling} — плановые задачи (обновление каталога,
 *       retry, очистка устаревших рекомендаций);</li>
 *   <li>{@link EnableFeignClients} — REST-клиенты к orchestrator,
 *       equipment-service;</li>
 *   <li>{@link EnableConfigurationProperties} — типизированные настройки.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "ru.iktk.recommendation",
        "ru.iktk.core",
        "ru.iktk.shared"
})
@EnableJpaRepositories(basePackages = "ru.iktk.recommendation.repository")
@EntityScan(basePackages = {
        "ru.iktk.recommendation.model",
        "ru.iktk.core.domain"
})
@EnableAsync
@EnableScheduling
@EnableFeignClients(basePackages = "ru.iktk.recommendation.client")
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationApplication {

    private static final Logger log = LoggerFactory.getLogger(RecommendationApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
                SpringApplication.run(RecommendationApplication.class, args);

        Environment env = ctx.getEnvironment();
        String appName = env.getProperty("spring.application.name", "recommendation-service");
        String port = env.getProperty("server.port", "8097");
        String profile = String.join(",", env.getActiveProfiles());
        String queueCmd = env.getProperty(
                "iktk.messaging.commands-queue", "iktk.recommendation.queue");
        String queueRes = env.getProperty(
                "iktk.messaging.results-queue", "iktk.results");
        String strategy = env.getProperty(
                "iktk.recommendation.strategy", "BALANCED");
        String scoring = env.getProperty(
                "iktk.recommendation.scoring.weights",
                "cost=0.4,leadTime=0.3,quality=0.2,load=0.1");
        String vendorEnabled = env.getProperty(
                "iktk.recommendation.vendor.enabled", "true");
        String outsourcingEnabled = env.getProperty(
                "iktk.recommendation.outsourcing.enabled", "true");

        log.info("""

                =========================================================
                  ИКТК: сервис рекомендаций запущен
                  Приложение     : {}
                  Профиль        : {}
                  Порт           : {}
                  Хост           : {}
                  Очередь cmd    : {}
                  Очередь res    : {}
                  Стратегия      : {}
                  Веса скоринга  : {}
                  Каталог внешн. : {}
                  Аутсорс        : {}
                  Внешний URL    : {}
                =========================================================
                """,
                appName,
                profile.isEmpty() ? "default" : profile,
                port,
                hostname(),
                queueCmd,
                queueRes,
                strategy,
                scoring,
                vendorEnabled,
                outsourcingEnabled,
                localUrl(env, port));
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