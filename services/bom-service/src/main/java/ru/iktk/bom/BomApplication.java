package ru.iktk.bom;

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
import ru.iktk.bom.config.BomProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Точка входа сервиса BOM (Bill of Materials) ИКТК.
 *
 * <p>Сервис {@code bom-service} отвечает за:
 * <ul>
 *   <li>анализ сборочных единиц: определение, является ли объект
 *       изготовления сборкой, и извлечение структуры изделия;</li>
 *   <li>построение дерева состава изделия (BOM): сборка → подсборки
 *       → детали → крепёж → материалы;</li>
 *   <li>сохранение позиций, количеств, обозначений, материалов
 *       в БД с версионностью;</li>
 *   <li>ручную корректировку BOM (при неполных данных из CAD);</li>
 *   <li>извлечение связей и сопряжений сборки (для ТП сборки);</li>
 *   <li>предоставление BOM для {@code assembly-process-service}
 *       и {@code documentation}.</li>
 * </ul>
 *
 * <p><b>Модель данных.</b> Ключевые таблицы:
 * <ul>
 *   <li>{@code assembly_units} — сборочные единицы;</li>
 *   <li>{@code bom_items} — позиции состава изделия;</li>
 *   <li>{@code assembly_connections} — связи и сопряжения.</li>
 * </ul>
 *
 * <p><b>Взаимодействие с оркестратором.</b> Сервис вызывается
 * оркестратором (позиция 4) на этапе {@code ASSEMBLY_ANALYSIS}.
 * Команда содержит {@code projectId}, {@code state}, {@code payload}
 * с идентификатором геометрии сборки.
 *
 * <p><b>Взаимодействие с другими сервисами.</b>
 * <ul>
 *   <li>{@code integration} (Siemens, Dassault, T-FLEX, КОМПАС) —
 *       источник геометрии и структуры сборки;</li>
 *   <li>{@code orchestrator} — заказчик анализа сборки;</li>
 *   <li>{@code assembly-process-service} — потребитель BOM
 *       для проектирования ТП сборки;</li>
 *   <li>{@code documentation} — потребитель BOM для формирования
 *       спецификации;</li>
 *   <li>{@code postgres} — хранилище BOM;</li>
 *   <li>{@code minio} — тяжёлые файлы (модели, структуры);</li>
 *   <li>{@code rabbitmq} — обмен командами и результатами.</li>
 * </ul>
 *
 * <p>Ключевые аннотации:
 * <ul>
 *   <li>{@link SpringBootApplication} — автоконфигурация Spring Boot;</li>
 *   <li>{@link EnableJpaRepositories} — JPA-репозитории BOM;</li>
 *   <li>{@link EntityScan} — сущности BOM и общие доменные модели;</li>
 *   <li>{@link EnableAsync} — асинхронный анализ больших сборок
 *       (1000+ позиций);</li>
 *   <li>{@link EnableScheduling} — плановые задачи (retry, очистка);</li>
 *   <li>{@link EnableFeignClients} — REST-клиенты к integration,
 *       orchestrator, assembly-process-service;</li>
 *   <li>{@link EnableConfigurationProperties} — типизированные настройки.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "ru.iktk.bom",
        "ru.iktk.core",
        "ru.iktk.shared"
})
@EnableJpaRepositories(basePackages = "ru.iktk.bom.repository")
@EntityScan(basePackages = {
        "ru.iktk.bom.model",
        "ru.iktk.core.domain"
})
@EnableAsync
@EnableScheduling
@EnableFeignClients(basePackages = "ru.iktk.bom.client")
@EnableConfigurationProperties(BomProperties.class)
public class BomApplication {

    private static final Logger log = LoggerFactory.getLogger(BomApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
                SpringApplication.run(BomApplication.class, args);

        Environment env = ctx.getEnvironment();
        String appName = env.getProperty("spring.application.name", "bom-service");
        String port = env.getProperty("server.port", "8095");
        String profile = String.join(",", env.getActiveProfiles());
        String queueCmd = env.getProperty(
                "iktk.messaging.commands-queue", "iktk.bom.queue");
        String queueRes = env.getProperty(
                "iktk.messaging.results-queue", "iktk.results");
        String maxDepth = env.getProperty(
                "iktk.bom.max-depth", "20");
        String cads = env.getProperty(
                "iktk.bom.cad-systems", "SIEMENS,DASSAULT,T_FLEX,KOMPAS");

        log.info("""

                =========================================================
                  ИКТК: сервис BOM запущен
                  Приложение    : {}
                  Профиль       : {}
                  Порт          : {}
                  Хост          : {}
                  Очередь cmd   : {}
                  Очередь res   : {}
                  Макс. глубина : {}
                  CAD-системы   : {}
                  Внешний URL   : {}
                =========================================================
                """,
                appName,
                profile.isEmpty() ? "default" : profile,
                port,
                hostname(),
                queueCmd,
                queueRes,
                maxDepth,
                cads,
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