package ru.iktk.modules.machining.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.dto.GeometryData;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.model.MachiningMethod;

import java.math.BigDecimal;

/**
 * Заглушка клиента подбора инструмента (сервиса tooling нет).
 *
 * <p>Возвращает фиктивный инструмент с типом по методу обработки,
 * чтобы G-код содержал корректный комментарий {@code ; tool=...}.
 */
@Component
public class ToolingClientStub implements ToolingClient {

    private static final Logger log = LoggerFactory.getLogger(ToolingClientStub.class);

    @Override
    public CuttingTool selectTool(MachiningMethod method, GeometryData geometry) {
        log.info("ToolingClientStub.selectTool (STUB): method={}", method);
        if (method == null) {
            return null;
        }
        return switch (method) {
            case MILLING, BORING -> CuttingTool.builder()
                    .type("END_MILL").diameter(BigDecimal.TEN)
                    .material("HSS").coating("TiAlN").build();
            case DRILLING, REAMING -> CuttingTool.builder()
                    .type("DRILL").diameter(BigDecimal.valueOf(5))
                    .material("HSS").coating("TiN").build();
            case TURNING -> CuttingTool.builder()
                    .type("TURNING_TOOL").diameter(BigDecimal.ZERO)
                    .material("Carbide").coating("TiAlN").build();
            case GRINDING -> CuttingTool.builder()
                    .type("GRINDING_WHEEL").diameter(BigDecimal.valueOf(200))
                    .material("Al2O3").coating("-").build();
            case EDM, LASER -> CuttingTool.builder()
                    .type("EDM_WIRE").diameter(BigDecimal.valueOf(0.3))
                    .material("-").coating("-").build();
            case CONTROL -> null;
        };
    }
}