package ru.iktk.modules.machining.client;

import ru.iktk.modules.machining.dto.EquipmentInfo;
import ru.iktk.modules.machining.dto.GeometryData;
import ru.iktk.modules.machining.model.MachiningMethod;

/**
 * Клиент к сервису оборудования.
 *
 * <p>Реального endpoint'а для «получить станок целиком» и «найти доступное
 * оборудование» в equipment-service пока нет — используется заглушка
 * {@link EquipmentClientStub}. Когда появятся endpoint'ы
 * {@code GET /api/v1/equipment/{id}} и {@code GET /api/v1/equipment/find} —
 * заменить stub на {@code @FeignClient}.
 */
public interface EquipmentClient {

    EquipmentInfo getEquipment(String equipmentId);

    String findAvailable(String projectId,
                         MachiningMethod method,
                         boolean cnc,
                         GeometryData geometry);
}