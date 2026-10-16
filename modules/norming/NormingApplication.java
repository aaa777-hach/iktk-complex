package ru.iktk.modules.norming;

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
import ru.iktk.modules.norming.config.NormingProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Точка входа модуля нормирования ИКТК.
 *
 * <p>Модуль {@code norming} — профильный технологический модуль комплекса,
 * отвечающий за расчёт норм времени и расхода материалов по всем видам
 * технологических процессов, поддерживаемых ИКТК:
 * <ul>
 *   <li>литьё;</li>
 *   <li>объёмная штамповка (горячая и холодная);</li>
 *   <li>механообработка (включая ЧПУ);</li>
 *   <li>сборка;</li>
 *   <li>вспомогательные и контрольные операции.</li>
 * </ul>
 *
 * <p><b>Функции модуля:</b>
 * <ul>
 *   <li>расчёт штучного времени (Тшт) по операциям и переходам;</li>
 *   <li>расчёт подготовительно-заключительного времени (Тпз);</li>
 *   <li>расчёт штучно-калькуляционного времени (Тшт-к);</li>
 *   <li>расчёт нормы расхода материала на изделие;</li>
 *   <li>расчёт коэффициента использования материала (КИМ);</li>
 *   <li>расчёт расхода инструмента и оснастки;</li>
 *   <li>формирование сводных норм по изделию и по ТП;</li>
 *   <li>экспорт норм в форматы предприятия (Excel, ГОСТ-формы).</li>
 * </ul>
 *
 * <p><b>Источники данных:</b>
 * <ul>
 *   <li>спроектированные ТП от модулей {@code casting}, {@code forging},
 *       {@code machining}, {@code assembly-process-service};</li>
 *   <li>справочники норм времени (общемашиностроительные, отраслевые,
 *       заводские) — в {@link NormingProperties};</li>
 *   <li>фактическое оборудование предприятия из
 *       {@code equipment-service} — для учёта реальных режимов;</li>
 *   <li>материалы и заготовки — из справочников и результатов
 *       проектирования ТП.</li>
 * </ul>
 *
 * <p><b>Взаимодействие с оркестратором.</b> Модуль вызывается оркестратором
 * (позиция 4, {@code OrchestrationService}) через очередь команд
 * ({@code iktk.command.norming}) на этапе {@code NORMING}. По завершении
 * публикует результат в {@code iktk.results} с routing key
 * {@code iktk.result.norming} и событием {@code NORMS_READY}.
 *
 * <p><b>Связь с другими модулями.</b>
 * <ul>
 *   <li>получает спроектированные ТП от {@code casting}, {@code forging},
 *       {@code machining}, {@code assembly-process-service};</li>
 *   <li>использует {@code equipment-service} для учёта реальных режимов
 *       работы станков;</li>
 *   <li>передаёт нормы в {@code documentation} — для формирования
 *       комплекта технологической документации.</li>
 * </ul>
 *
 * <p>Ключевые аннотации:
 * <ul>
 *   <li>{@link SpringBootApplication} — автоконфигурация Spring Boot;</li>
 *   <li>{@link EnableAsync} — асинхронный расчёт норм (объёмные ТП,
 *       сборочные единицы с большим числом деталей);</li>
 *   <li>{@link EnableScheduling} — плановые задачи (health, retry,
 *       обновление справочников);</li>
 *   <li>{@link EnableFeignClients} — REST-клиенты к другим сервисам;</li>
 *   <li>{@link EnableConfigurationProperties} — типизированные настройки.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "ru.iktk.modules.norming",
        "ru.iktk.core"
})
@EnableAsync
@EnableScheduling
@EnableFeignClients(basePackages = "ru.iktk.modules.norming.client")
@EnableConfigurationProperties(NormingProperties.class)
public class NormingApplication {

    private static final Logger log = LoggerFactory.getLogger(NormingApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
                SpringApplication.run(NormingApplication.class, args);

        Environment env = ctx.getEnvironment();
        String appName = env.getProperty("spring.application.name", "norming");
        String port = env.getProperty("server.port", "8093");
        String profile = String.join(",", env.getActiveProfiles());
        String queueCmd = env.getProperty(
                "iktk.messaging.commands-queue", "iktk.norming.queue");
        String queueRes = env.getProperty(
                "iktk.messaging.results-queue", "iktk.results");
        String normSource = env.getProperty(
                "iktk.norming.default-source", "GENERAL_MACHINE_BUILDING");

        log.info("""

                =========================================================
                  ИКТК: модуль нормирования запущен
                  Приложение  : {}
                  Профиль     : {}
                  Порт        : {}
                  Хост        : {}
                  Очередь cmd : {}
                  Очередь res : {}
                  Источник норм: {}
                  Внешний URL : {}
                =========================================================
                """,
                appName,
                profile.isEmpty() ? "default" : profile,
                port,
                hostname(),
                queueCmd,
                queueRes,
                normSource,
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