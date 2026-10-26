package ru.iktk.modules.machining;

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
import org.springframework.boot.autoconfigure.domain.EntityScan;
import ru.iktk.modules.machining.config.MachiningProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Точка входа модуля механообработки ИКТК.
 *
 * <p>Модуль {@code machining} — профильный технологический модуль комплекса,
 * отвечающий за проектирование технологического процесса механической
 * обработки и формирование управляющих программ для станков с ЧПУ:
 * <ul>
 *   <li>анализ геометрии детали на технологичность механообработки
 *       (доступность инструмента, установы, базирование, жёсткость);</li>
 *   <li>выбор метода обработки (точение, фрезерование, сверление,
 *       шлифование, электроэрозия и т.п.);</li>
 *   <li>определение последовательности операций и переходов;</li>
 *   <li>выбор оборудования и режущего инструмента;</li>
 *   <li>расчёт режимов резания (скорость, подача, глубина);</li>
 *   <li>формирование управляющих программ (УП) для станков с ЧПУ
 *       (ISO 6983 / G-код, а также STEP-NC при поддержке);</li>
 *   <li>нормирование операций механообработки;</li>
 *   <li>формирование технологической документации по ТП механообработки.</li>
 * </ul>
 *
 * <p><b>Поддерживаемые виды обработки</b> (согласно {@code final_TZ.md},
 * ред. 3, п. 4.2):
 * <ul>
 *   <li>токарная обработка (включая ЧПУ);</li>
 *   <li>фрезерная обработка (включая ЧПУ);</li>
 *   <li>сверлильная, расточная, зенкерование, развёртывание;</li>
 *   <li>шлифовальная (круглая, плоская, внутренняя);</li>
 *   <li>электроэрозионная, лазерная, гидроабразивная (при расширении).</li>
 * </ul>
 *
 * <p><b>Взаимодействие с оркестратором.</b> Модуль вызывается оркестратором
 * (позиция 4, {@code OrchestrationService}) через очередь команд
 * ({@code iktk.command.machining}). Команда содержит {@code projectId},
 * {@code state}, {@code payload}. По завершении модуль публикует результат
 * в очередь {@code iktk.results} с routing key {@code iktk.result.machining}
 * и событием {@code PART_PROCESS_READY} (для ТП механообработки) либо
 * {@code CNC_PROGRAM_READY} (для УП).
 *
 * <p><b>Связь с другими модулями.</b>
 * <ul>
 *   <li>получает геометрию из {@code integration} (Siemens, Dassault,
 *       T-FLEX, КОМПАС);</li>
 *   <li>использует {@code equipment-service} для проверки доступности
 *       металлообрабатывающего оборудования, включая станки с ЧПУ;</li>
 *   <li>использует {@code recommendation-service} при дефиците
 *       оборудования;</li>
 *   <li>передаёт результат проектирования в {@code norming} и
 *       {@code documentation}.</li>
 * </ul>
 *
 * <p>Ключевые аннотации:
 * <ul>
 *   <li>{@link SpringBootApplication} — автоконфигурация Spring Boot;</li>
 *   <li>{@link EnableAsync} — асинхронные длительные расчёты (режимы
 *       резания, генерация УП, симуляция);</li>
 *   <li>{@link EnableScheduling} — плановые задачи (health, retry,
 *       очистка временных УП);</li>
 *   <li>{@link EnableFeignClients} — REST-клиенты к другим сервисам;</li>
 *   <li>{@link EnableConfigurationProperties} — типизированные настройки.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "ru.iktk.modules.machining",
        "ru.iktk.core"
})
@EntityScan("ru.iktk.modules.machining.model")
@EnableAsync
@EnableScheduling
@EnableFeignClients(basePackages = "ru.iktk.modules.machining.client")
@EnableConfigurationProperties(MachiningProperties.class)
public class MachiningApplication {

    private static final Logger log = LoggerFactory.getLogger(MachiningApplication.class);

    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
                SpringApplication.run(MachiningApplication.class, args);

        Environment env = ctx.getEnvironment();
        String appName = env.getProperty("spring.application.name", "machining");
        String port = env.getProperty("server.port", "8096");
        String profile = String.join(",", env.getActiveProfiles());
        String queueCmd = env.getProperty(
                "iktk.messaging.commands-queue", "iktk.machining.queue");
        String queueRes = env.getProperty(
                "iktk.messaging.results-queue", "iktk.results");
        String cncPostprocessor = env.getProperty(
                "iktk.machining.cnc.default-postprocessor", "ISO6983");

        log.info("""

                =========================================================
                  ИКТК: модуль механообработки запущен
                  Приложение : {}
                  Профиль    : {}
                  Порт       : {}
                  Хост       : {}
                  Очередь cmd: {}
                  Очередь res: {}
                  Постпроц.  : {}
                  Внешний URL: {}
                =========================================================
                """,
                appName,
                profile.isEmpty() ? "default" : profile,
                port,
                hostname(),
                queueCmd,
                queueRes,
                cncPostprocessor,
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
        return schema(scheme) + "://localhost:" + port;
    }

    private static String schema(String scheme) {
        return scheme == null || scheme.isBlank() ? "http" : scheme;
    }
}