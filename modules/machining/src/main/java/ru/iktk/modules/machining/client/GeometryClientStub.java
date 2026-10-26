package ru.iktk.modules.machining.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.dto.GeometryData;

import java.math.BigDecimal;

/**
 * Заглушка клиента integration (структура GeometryData пока не зафиксирована).
 *
 * <p>Возвращает фиктивную геометрию, чтобы E2E-прогон consumer → designProcess
 * → DB не падал на Feign-клиенте. Заменить на реальный {@code @FeignClient}
 * после согласования контракта с integration (см. {@link GeometryClient}).
 */
@Component
public class GeometryClientStub implements GeometryClient {

    private static final Logger log = LoggerFactory.getLogger(GeometryClientStub.class);

    @Override
    public GeometryData fetchGeometry(String projectId, String objectRef) {
        log.info("GeometryClientStub.fetchGeometry (STUB): projectId={}, objectRef={}",
                projectId, objectRef);

        String ref = objectRef == null || objectRef.isBlank() ? "geo-stub" : objectRef;

        return new GeometryData(
                ref,                                                    // objectRef
                "Steel-45",                                             // material
                BigDecimal.valueOf(1000),                               // volume
                BigDecimal.valueOf(7.85),                               // mass
                BigDecimal.valueOf(5),                                  // minInternalRadius
                BigDecimal.valueOf(3),                                  // minWallThickness
                BigDecimal.valueOf(5),                                  // maxHoleDepthRatio
                BigDecimal.valueOf(0.05),                               // requiredAccuracy
                BigDecimal.valueOf(2),                                  // machiningAllowance
                10,                                                     // surfaceCount
                3,                                                      // complexityScore
                false,                                                  // undercuts
                true,                                                   // holes
                false,                                                  // precisionHoles
                false                                                   // thinRibs
        );
    }
}