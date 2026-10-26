package ru.iktk.modules.machining.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.dto.EquipmentInfo;
import ru.iktk.modules.machining.dto.GeometryData;
import ru.iktk.modules.machining.model.MachiningMethod;

import java.util.Map;

/**
 * Заглушка клиента оборудования (реальных endpoint'ов в equipment-service нет).
 *
 * <p>Возвращает фиктивные данные, чтобы E2E не падал на Feign-клиенте.
 * Заменить на реальный {@code @FeignClient}, когда появятся endpoint'ы
 * {@code GET /api/v1/equipment/{id}} и {@code GET /api/v1/equipment/find}.
 */
@Component
public class EquipmentClientStub implements EquipmentClient {

    private static final Logger log = LoggerFactory.getLogger(EquipmentClientStub.class);

    @Override
    public EquipmentInfo getEquipment(String equipmentId) {
        log.info("EquipmentClientStub.getEquipment (STUB): id={}", equipmentId);
        return new EquipmentInfo(
                equipmentId == null ? "EQ-STUB" : equipmentId,
                "CNC-DEFAULT",
                "ISO6983",
                3,
                Map.of("stub", true)
        );
    }

    @Override
    public String findAvailable(String projectId, MachiningMethod method,
                                boolean cnc, GeometryData geometry) {
        String equipmentId = "EQ-" + (method == null ? "UNKNOWN" : method.name())
                + (cnc ? "-CNC" : "");
        log.info("EquipmentClientStub.findAvailable (STUB): projectId={}, method={}, cnc={} -> {}",
                projectId, method, cnc, equipmentId);
        return equipmentId;
    }
}