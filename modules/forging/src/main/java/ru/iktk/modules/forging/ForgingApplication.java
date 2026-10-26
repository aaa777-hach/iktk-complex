package ru.iktk.modules.forging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.iktk.modules.forging.config.ForgingProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Точка входа модуля объёмной штамповки ИКТК.
 *
 * <p>Модуль {@code forging} — профильный технологический модуль комплекса,
 * отвечающий за проектирование технологического процесса объёмной
 * штамповки (горячей и холодной) и оснастки:
 * <ul>
 *   <li>анализ геометрии детали на технологичность штамповки
 *       (радиусы гибки, толщины, уклоны, перемычки);</li>
 *   <li>выбор способа штамповки (горячая объёмная, холодная объёмная,
 *       листовая, высадка, выдавливание);</li>
 *   <li>определение количества переходов и их последовательности;</li>
 *   <li>расчёт размеров заготовки и промежуточных переходов;</li>
 *   <li>расчёт усилий и выбор прессового оборудования;</li>
 *   <li>проектирование штамповой оснастки (ручьи, матрицы, пуансоны);</li>
 *   <li>нормирование операций штамповки;</li>
 *   <li>формирование технологической документации по штамповочному ТП.</li>
 * </ul>
 *
 * <p><b>Поддерживаемые виды штамповки</b> (согласно {@code final_TZ.md},
 * ред. 3, п. 4.2):
 * <ul>
 *   <li>горячая объёмная штамповка;</li>
 *   <li>холодная объёмная штамповка;</li>
 *   <li>заготовительные операции (резка, нагрев, осадка);</li>
 *   <li>листовая штамповка (при расширении — Релиз 1).</li>
 * </ul>
 *
 * <p><b>Взаимодействие с оркестратором.</b> Модуль вызывается оркестратором
 * (позиция 4, {@code OrchestrationService}) через очередь команд
 * ({@code iktk.command.forging}). Команда содержит {@code projectId},
 * {@code state}, {@code payload}. По завершении модуль публикует результат
 * в очередь {@code iktk.results} с routing key {@code iktk.result.forging}
 * и событием {@code PART_PROCESS_READY}.
 *
 * <p><b>Связь с другими модулями.</b>
 * <ul>
 *   <li>получает геометрию из {@code integration} (Siemens, Dassault,
 *       T-FLEX, КОМПАС);</li>
 *   <li>использует {@code equipment-service} для проверки доступности
 *       прессового и нагревательного оборудования;</li>
 *   <li>использует {@code recommendation-service} при дефиците
 *       оборудования;</li>
 *   <li>передаёт результат проектирования в {@code norming} и
 *       {@code documentation}.</li>
 * </ul>
 *
 * <p>Ключевые аннотации:
 * <ul>
 *   <li>{@link SpringBootApplication} — автоконфигурация Spring Boot;</li>
 *   <li>{@link EnableAsync} — асинхронные длительные расчёты (усилия,
 *       переходы, температурные режимы);</li>
 *   <li>{@link EnableScheduling} — плановые задачи (health, retry);</li>
 *   <li>{@link EnableFeignClients} — REST-клиенты к другим сервисам;</li>
 *   <li>{@link EnableConfigurationProperties} — типизированные настройки.</li>
 * </ul>
 */
@EntityScan(basePackages = "ru.iktk.modules.forging.model")
@SpringBootApplication(scanBasePackages = {
        "ru.iktk.modules.forging",
        "ru.iktk.core"
})
@EnableAsync
@EnableScheduling
@EnableFeignClients(basePackages = "ru.iktk.modules.forging.client")
@EnableConfigurationProperties(ForgingProperties.class)
public class ForgingApplication {

    private static final Logger log = LoggerFactory.getLogger(ForgingApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
                SpringApplication.run(ForgingApplication.class, args);

        Environment env = ctx.getEnvironment();
        String appName = env.getProperty("spring.application.name", "forging");
        String port = env.getProperty("server.port", "8091");
        String profile = String.join(",", env.getActiveProfiles());
        String queueCmd = env.getProperty(
                "iktk.messaging.commands-queue", "iktk.forging.queue");
        String queueRes = env.getProperty(
                "iktk.messaging.results-queue", "iktk.results");

        log.info("""

                =========================================================
                  ИКТК: модуль объёмной штамповки запущен
                  Приложение : {}
                  Профиль    : {}
                  Порт       : {}
                  Хост       : {}
                  Очередь cmd: {}
                  Очередь res: {}
                  Внешний URL: {}
                =========================================================
                """,
                appName,
                profile.isEmpty() ? "default" : profile,
                port,
                hostname(),
                queueCmd,
                queueRes,
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
