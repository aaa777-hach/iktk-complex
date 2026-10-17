package ru.iktk.modules.casting;


import org.springframework.amqp.rabbit.annotation.EnableRabbit;
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
import ru.iktk.modules.casting.config.CastingProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Точка входа модуля литья ИКТК.
 *
 * <p>Модуль {@code casting} — один из профильных технологических модулей
 * комплекса. Отвечает за проектирование литейного технологического
 * процесса (ТП) и оснастки:
 * <ul>
 *   <li>анализ геометрии детали на технологичность литья
 *       (уклоны, радиусы, толщины стенок, теплоузлы);</li>
 *   <li>выбор способа литья (в песчаные формы, кокиль, под давлением,
 *       точное литьё по выплавляемым моделям и т.п.);</li>
 *   <li>расчёт литниково-питающей системы (ЛПС);</li>
 *   <li>расчёт усадки и припусков;</li>
 *   <li>проектирование литейной оснастки (модели, стержни, формы);</li>
 *   <li>нормирование операций литья;</li>
 *   <li>формирование технологической документации по литейному ТП.</li>
 * </ul>
 *
 * <p><b>Взаимодействие с оркестратором.</b> Модуль вызывается оркестратором
 * (позиция 4, {@code OrchestrationService}) через очередь команд
 * ({@code iktk.command.casting}). Команда содержит {@code projectId},
 * {@code state}, {@code payload}. По завершении модуль возвращает
 * результат в очередь результатов
 * ({@code iktk.result.casting}) с событием
 * {@code PART_PROCESS_READY} (или иным, согласованным с ТЗ).
 *
 * <p><b>Связь с другими модулями.</b>
 * <ul>
 *   <li>получает геометрию из {@code integration} (Siemens, Dassault,
 *       T-FLEX, КОМПАС);</li>
 *   <li>использует {@code equipment-service} для проверки доступности
 *       литейного оборудования предприятия;</li>
 *   <li>использует {@code recommendation-service} при дефиците
 *       оборудования;</li>
 *   <li>передаёт результат проектирования в {@code norming} и
 *       {@code documentation}.</li>
 * </ul>
 *
 * <p>Ключевые аннотации:
 * <ul>
 *   <li>{@link SpringBootApplication} — автоконфигурация Spring Boot;</li>
 *   <li>{@link EnableAsync} — асинхронные длительные расчёты;</li>
 *   <li>{@link EnableScheduling} — плановые задачи (health, retry);</li>
 *   <li>{@link EnableFeignClients} — REST-клиенты к другим сервисам;</li>
 *   <li>{@link EnableConfigurationProperties} — типизированные настройки.</li>
 * </ul>
 */
@EnableRabbit
@SpringBootApplication(scanBasePackages = {
        "ru.iktk.modules.casting",
        "ru.iktk.core"
})
@EnableAsync
@EnableScheduling
@EnableFeignClients(basePackages = "ru.iktk.modules.casting.client")
@EnableConfigurationProperties(CastingProperties.class)
public class CastingApplication {

    private static final Logger log = LoggerFactory.getLogger(CastingApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
                SpringApplication.run(CastingApplication.class, args);

        Environment env = ctx.getEnvironment();
        String appName = env.getProperty("spring.application.name", "casting");
        String port = env.getProperty("server.port", "8090");
        String profile = String.join(",", env.getActiveProfiles());
        String queueCmd = env.getProperty(
                "iktk.messaging.commands-queue", "iktk.casting.queue");
        String queueRes = env.getProperty(
                "iktk.messaging.results-queue", "iktk.results");

        log.info("""

                =========================================================
                  ИКТК: модуль литья запущен
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