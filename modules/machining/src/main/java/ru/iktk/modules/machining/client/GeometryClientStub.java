package ru.iktk.modules.machining.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.dto.GeometryData;

import java.math.BigDecimal;

/**
 * Заглушка геометрии. НЕ реализует {@link GeometryClient} намеренно —
 * чтобы не было конфликта bean'ов с Feign-прокси. Используется только
 * через {@link GeometryClientRouter} как fallback.
 */
@Component
public class GeometryClientStub {

    private static final Logger log = LoggerFactory.getLogger(GeometryClientStub.class);

    public GeometryData fetchGeometry(String projectId, String objectRef) {
        log.info("GeometryClientStub.fetchGeometry (STUB): projectId={}, objectRef={}",
                projectId, objectRef);

        String ref = objectRef == null || objectRef.isBlank() ? "geo-stub" : objectRef;

        return new GeometryData(
                ref,
                "Steel-45",
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(7.85),
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(3),
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(0.05),
                BigDecimal.valueOf(2),
                10,
                3,
                false,
                true,
                false,
                false
        );
    }
}