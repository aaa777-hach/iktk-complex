package ru.iktk.modules.machining.client;

import ru.iktk.modules.machining.dto.GeometryData;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.model.MachiningMethod;

/**
 * Клиент подбора режущего инструмента.
 *
 * <p>Сервиса {@code tooling} пока нет — используется заглушка
 * {@link ToolingClientStub}. Когда появится реальный сервис — заменить stub
 * на {@code @FeignClient}.
 */
public interface ToolingClient {

    CuttingTool selectTool(MachiningMethod method, GeometryData geometry);
}