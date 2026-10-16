package ru.iktk.assembly;

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
import ru.iktk.assembly.config.AssemblyProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Точка входа сервиса проектирования ТП сборки ИКТК.
 *
 * <p>Сервис {@code assembly-process-service} отвечает за:
 * <ul>
 *   <li>проектирование технологического процесса сборки сборочной
 *       единицы на основе BOM и связей/сопряжений;</li>
 *   <li>определение последовательности сборочных операций
 *       (базирование, установка, крепление, контроль);</li>
 *   <li>проектирование сборочной оснастки и приспособлений;</li>
 *   <li>выбор сборочного оборудования и инструмента;</li>
 *   <li>нормирование сборочных операций;</li>
 *   <li>формирование карты сборки и документации по ТП сборки.</li>
 * </ul>
 *
 * <p><b>Модель данных.</b> Ключевые таблицы:
 * <ul>
 *   <li>{@code assembly_processes} — ТП сборки;</li>
 *   <li>{@code assembly_operations} — операции сборки;</li>
 *   <li>{@code assembly_tooling} — оснастка для сборки.</li>
 * </ul>
 *
 * <p><b>Взаимодействие с оркестратором.</b> Сервис вызывается
 * оркестратором (позиция 4) на этапе
 * {@code ASSEMBLY_PROCESS_PLANNING}. Команда содержит
 * {@code projectId}, {@code state}, {@code payload} с идентификатором
 * BOM-дерева и параметрами сборки.
 *
 * <p><b>Взаимодействие с другими сервисами.</b>
 * <ul>
 *   <li>{@code bom-service} (позиции 80–88) — источник BOM и связей;</li>
 *   <li>{@code orchestrator} — заказчик проектирования ТП сборки;</li>
 *   <li>{@code equipment-service} (позиции 72–79) — проверка
 *       доступности сборочного оборудования;</li>
 *   <li>{@code recommendation-service} (позиции 94–105) — при
 *       дефиците оборудования;</li>
 *   <li>{@code norming} (позиции 39–41) — потребитель результата
 *       для нормирования сборочных операций;</li>
 *   <li>{@code documentation} — потребитель карты сборки;</li>
 *   <li>{@code postgres} — хранилище ТП сборки;</li>
 *   <li>{@code rabbitmq} — обмен командами и результатами.</li>
 * </ul>
 *
 * <p>Ключевые аннотации:
 * <ul>
 *   <li>{@link SpringBootApplication} — автоконфигурация Spring Boot;</li>
 *   <li>{@link EnableJpaRepositories} — JPA-репозитории ТП сборки;</li>
 *   <li>{@link EntityScan} — сущности ТП сборки и общие доменные модели;</li>
 *   <li>{@link EnableAsync} — асинхронное проектирование ТП для
 *       больших сборок (1000+ позиций);</li>
 *   <li>{@link EnableScheduling} — плановые задачи (health, retry,
 *       синхронизация справочников оснастки);</li>
 *   <li>{@link EnableFeignClients} — REST-клиенты к bom-service,
 *       equipment-service, recommendation-service;</li>
 *   <li>{@link EnableConfigurationProperties} — типизированные настройки.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "ru.iktk.assembly",
        "ru.iktk.core",
        "ru.iktk.shared"
})
@EnableJpaRepositories(basePackages = "ru.iktk.assembly.repository")
@EntityScan(basePackages = {
        "ru.iktk.assembly.model",
        "ru.iktk.core.domain"
})
@EnableAsync
@EnableScheduling
@EnableFeignClients(basePackages = "ru.iktk.assembly.client")
@EnableConfigurationProperties(AssemblyProperties.class)
public class AssemblyProcessApplication {

    private static final Logger log = LoggerFactory.getLogger(AssemblyProcessApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
                SpringApplication.run(AssemblyProcessApplication.class, args);

        Environment env = ctx.getEnvironment();
        String appName = env.getProperty("spring.application.name", "assembly-process-service");
        String port = env.getProperty("server.port", "8096");
        String profile = String.join(",", env.getActiveProfiles());
        String queueCmd = env.getProperty(
                "iktk.messaging.commands-queue", "iktk.assembly.queue");
        String queueRes = env.getProperty(
                "iktk.messaging.results-queue", "iktk.results");
        String strategy = env.getProperty(
                "iktk.assembly.sequence.strategy", "TOPOLOGICAL");
        String toolingEnabled = env.getProperty(
                "iktk.assembly.tooling.enabled", "true");

        log.info("""

                =========================================================
                  ИКТК: сервис ТП сборки запущен
                  Приложение    : {}
                  Профиль       : {}
                  Порт          : {}
                  Хост          : {}
                  Очередь cmd   : {}
                  Очередь res   : {}
                  Стратегия     : {}
                  Оснастка      : {}
                  Внешний URL   : {}
                =========================================================
                """,
                appName,
                profile.isEmpty() ? "default" : profile,
                port,
                hostname(),
                queueCmd,
                queueRes,
                strategy,
                toolingEnabled,
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