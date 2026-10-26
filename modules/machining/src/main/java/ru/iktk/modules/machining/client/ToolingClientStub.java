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
 * <p>Возвращает фиктивный инструмент, чтобы E2E не падал.
 */
@Component
public class ToolingClientStub implements ToolingClient {

    private static final Logger log = LoggerFactory.getLogger(ToolingClientStub.class);

    @Override
    public CuttingTool selectTool(MachiningMethod method, GeometryData geometry) {
        log.info("ToolingClientStub.selectTool (STUB): method={}", method);
        return CuttingTool.builder()
                .type("END_MILL")
                .diameter(BigDecimal.TEN)
                .material("HSS")
                .coating("TiAlN")
                .build();
    }
}