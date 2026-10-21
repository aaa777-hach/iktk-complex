package ru.iktk.recommendation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
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
 * РўРѕС‡РєР° РІС…РѕРґР° СЃРµСЂРІРёСЃР° СЂРµРєРѕРјРµРЅРґР°С†РёР№ РРљРўРљ.
 *
 * <p>РЎРµСЂРІРёСЃ {@code recommendation-service} РѕС‚РІРµС‡Р°РµС‚ Р·Р°:
 * <ul>
 *   <li>gap-Р°РЅР°Р»РёР·: СЃРѕРїРѕСЃС‚Р°РІР»РµРЅРёРµ С‚СЂРµР±РѕРІР°РЅРёР№ РўРџ СЃ РїР°СЂРєРѕРј
 *       РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЏ Рё РІС‹СЏРІР»РµРЅРёРµ РґРµС„РёС†РёС‚Р°;</li>
 *   <li>С„РѕСЂРјРёСЂРѕРІР°РЅРёРµ СЂРµРєРѕРјРµРЅРґР°С†РёР№ РїСЂРё РґРµС„РёС†РёС‚Рµ:
 *       Р°Р»СЊС‚РµСЂРЅР°С‚РёРІРЅРѕРµ РёРјРµСЋС‰РµРµСЃСЏ РѕР±РѕСЂСѓРґРѕРІР°РЅРёРµ, РґРѕРєСѓРїРєР°, Р°СЂРµРЅРґР°,
 *       Р°СѓС‚СЃРѕСЂСЃ, РєРѕСЂСЂРµРєС‚РёСЂРѕРІРєР° РўРџ;</li>
 *   <li>СЃРєРѕСЂРёРЅРі РІР°СЂРёР°РЅС‚РѕРІ РїРѕ РїСЂРѕРёР·РІРѕРґРёС‚РµР»СЊРЅРѕСЃС‚Рё, Р·Р°РіСЂСѓР·РєРµ,
 *       СЃС‚РѕРёРјРѕСЃС‚Рё, СЃСЂРѕРєР°Рј;</li>
 *   <li>РїРѕРёСЃРє РІ РєР°С‚Р°Р»РѕРіРµ РІРЅРµС€РЅРµРіРѕ РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЏ
 *       ({@code vendor_catalog});</li>
 *   <li>С„РѕСЂРјРёСЂРѕРІР°РЅРёРµ РІР°СЂРёР°РЅС‚РѕРІ Р°СѓС‚СЃРѕСЂСЃР°
 *       ({@code outsourcing_options});</li>
 *   <li>РїСЂРµРґРѕСЃС‚Р°РІР»РµРЅРёРµ СЂРµРєРѕРјРµРЅРґР°С†РёР№ РґР»СЏ С‚РѕС‡РєРё РєРѕРЅС‚СЂРѕР»СЏ
 *       {@code RECOMMENDATION_REVIEW} РѕСЂРєРµСЃС‚СЂР°С‚РѕСЂР°.</li>
 * </ul>
 *
 * <p><b>РњРѕРґРµР»СЊ РґР°РЅРЅС‹С….</b> РљР»СЋС‡РµРІС‹Рµ С‚Р°Р±Р»РёС†С‹ (РјРёРіСЂР°С†РёСЏ V6):
 * <ul>
 *   <li>{@code equipment_requirements} вЂ” С‚СЂРµР±РѕРІР°РЅРёСЏ Рє РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЋ
 *       РёР· РўРџ;</li>
 *   <li>{@code equipment_gap_analysis} вЂ” СЂРµР·СѓР»СЊС‚Р°С‚С‹ gap-Р°РЅР°Р»РёР·Р°;</li>
 *   <li>{@code equipment_recommendations} вЂ” СЂРµРєРѕРјРµРЅРґР°С†РёРё;</li>
 *   <li>{@code vendor_catalog} вЂ” РєР°С‚Р°Р»РѕРі РІРЅРµС€РЅРµРіРѕ РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЏ;</li>
 *   <li>{@code outsourcing_options} вЂ” РІР°СЂРёР°РЅС‚С‹ Р°СѓС‚СЃРѕСЂСЃР°.</li>
 * </ul>
 *
 * <p><b>Р’Р·Р°РёРјРѕРґРµР№СЃС‚РІРёРµ СЃ РѕСЂРєРµСЃС‚СЂР°С‚РѕСЂРѕРј.</b> РЎРµСЂРІРёСЃ РІС‹Р·С‹РІР°РµС‚СЃСЏ
 * РѕСЂРєРµСЃС‚СЂР°С‚РѕСЂРѕРј (РїРѕР·РёС†РёСЏ 4) РЅР° СЌС‚Р°РїРµ
 * {@code EQUIPMENT_GAP_RESOLUTION}. РљРѕРјР°РЅРґР° СЃРѕРґРµСЂР¶РёС‚
 * {@code projectId}, {@code state}, {@code payload} СЃ С‚СЂРµР±РѕРІР°РЅРёСЏРјРё
 * Рє РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЋ. Р РµР·СѓР»СЊС‚Р°С‚ РїСѓР±Р»РёРєСѓРµС‚СЃСЏ СЃ СЃРѕР±С‹С‚РёРµРј
 * {@code RECOMMENDATIONS_READY}.
 *
 * <p><b>Р’Р·Р°РёРјРѕРґРµР№СЃС‚РІРёРµ СЃ РґСЂСѓРіРёРјРё СЃРµСЂРІРёСЃР°РјРё.</b>
 * <ul>
 *   <li>{@code orchestrator} вЂ” Р·Р°РєР°Р·С‡РёРє gap-Р°РЅР°Р»РёР·Р° Рё СЂРµРєРѕРјРµРЅРґР°С†РёР№;</li>
 *   <li>{@code equipment-service} (РїРѕР·РёС†РёРё 72вЂ“79) вЂ” РёСЃС‚РѕС‡РЅРёРє РґР°РЅРЅС‹С…
 *       Рѕ РїР°СЂРєРµ РїСЂРµРґРїСЂРёСЏС‚РёСЏ;</li>
 *   <li>{@code casting}, {@code forging}, {@code machining} вЂ” РёСЃС‚РѕС‡РЅРёРєРё
 *       С‚СЂРµР±РѕРІР°РЅРёР№ Рє РѕР±РѕСЂСѓРґРѕРІР°РЅРёСЋ (С‡РµСЂРµР· {@code stage_equipment_requirements});</li>
 *   <li>{@code postgres} вЂ” С…СЂР°РЅРёР»РёС‰Рµ СЂРµРєРѕРјРµРЅРґР°С†РёР№;</li>
 *   <li>{@code rabbitmq} вЂ” РѕР±РјРµРЅ РєРѕРјР°РЅРґР°РјРё Рё СЂРµР·СѓР»СЊС‚Р°С‚Р°РјРё.</li>
 * </ul>
 *
 * <p>РљР»СЋС‡РµРІС‹Рµ Р°РЅРЅРѕС‚Р°С†РёРё:
 * <ul>
 *   <li>{@link SpringBootApplication} вЂ” Р°РІС‚РѕРєРѕРЅС„РёРіСѓСЂР°С†РёСЏ Spring Boot;</li>
 *   <li>{@link EnableJpaRepositories} вЂ” JPA-СЂРµРїРѕР·РёС‚РѕСЂРёРё СЂРµРєРѕРјРµРЅРґР°С†РёР№;</li>
 *   <li>{@link EntityScan} вЂ” СЃСѓС‰РЅРѕСЃС‚Рё СЂРµРєРѕРјРµРЅРґР°С†РёР№ Рё РѕР±С‰РёРµ РґРѕРјРµРЅРЅС‹Рµ РјРѕРґРµР»Рё;</li>
 *   <li>{@link EnableAsync} вЂ” Р°СЃРёРЅС…СЂРѕРЅРЅС‹Р№ gap-Р°РЅР°Р»РёР· (РјРѕР¶РµС‚ Р±С‹С‚СЊ РґР»РёС‚РµР»СЊРЅС‹Рј);</li>
 *   <li>{@link EnableScheduling} вЂ” РїР»Р°РЅРѕРІС‹Рµ Р·Р°РґР°С‡Рё (РѕР±РЅРѕРІР»РµРЅРёРµ РєР°С‚Р°Р»РѕРіР°,
 *       retry, РѕС‡РёСЃС‚РєР° СѓСЃС‚Р°СЂРµРІС€РёС… СЂРµРєРѕРјРµРЅРґР°С†РёР№);</li>
 *   <li>{@link EnableFeignClients} вЂ” REST-РєР»РёРµРЅС‚С‹ Рє orchestrator,
 *       equipment-service;</li>
 *   <li>{@link EnableConfigurationProperties} вЂ” С‚РёРїРёР·РёСЂРѕРІР°РЅРЅС‹Рµ РЅР°СЃС‚СЂРѕР№РєРё.</li>
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
@EnableRabbit
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
                  РРљРўРљ: СЃРµСЂРІРёСЃ СЂРµРєРѕРјРµРЅРґР°С†РёР№ Р·Р°РїСѓС‰РµРЅ
                  РџСЂРёР»РѕР¶РµРЅРёРµ     : {}
                  РџСЂРѕС„РёР»СЊ        : {}
                  РџРѕСЂС‚           : {}
                  РҐРѕСЃС‚           : {}
                  РћС‡РµСЂРµРґСЊ cmd    : {}
                  РћС‡РµСЂРµРґСЊ res    : {}
                  РЎС‚СЂР°С‚РµРіРёСЏ      : {}
                  Р’РµСЃР° СЃРєРѕСЂРёРЅРіР°  : {}
                  РљР°С‚Р°Р»РѕРі РІРЅРµС€РЅ. : {}
                  РђСѓС‚СЃРѕСЂСЃ        : {}
                  Р’РЅРµС€РЅРёР№ URL    : {}
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
    // РЈС‚РёР»РёС‚С‹
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