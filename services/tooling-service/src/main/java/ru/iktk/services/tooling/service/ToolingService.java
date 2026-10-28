package ru.iktk.services.tooling.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.iktk.services.tooling.dto.CuttingToolDto;

import java.math.BigDecimal;

/**
 * Справочник режущего инструмента (stub, MVP).
 *
 * <p>Возвращает инструмент по методу обработки. Параметр {@code material}
 * принимается, но пока не влияет на выбор — контракт готов к расширению
 * (PCD/Carbide для Ti/закалённых сталей) без breaking change.
 */
@Service
public class ToolingService {

    private static final Logger log = LoggerFactory.getLogger(ToolingService.class);

    /**
     * Подобрать инструмент.
     *
     * @param method   метод обработки
     * @param material материал заготовки (опционально, пока не влияет)
     * @return инструмент, либо {@code null} если инструмент не требуется (CONTROL)
     */
    public CuttingToolDto select(ToolingMethod method, String material) {
        log.info("ToolingService.select: method={}, material={}", method, material);
        if (method == null) {
            return null;
        }
        return switch (method) {
            case MILLING, BORING -> new CuttingToolDto("END_MILL", BigDecimal.TEN, "HSS", "TiAlN");
            case DRILLING, REAMING -> new CuttingToolDto("DRILL", BigDecimal.valueOf(5), "HSS", "TiN");
            case TURNING -> new CuttingToolDto("TURNING_TOOL", BigDecimal.ZERO, "Carbide", "TiAlN");
            case GRINDING -> new CuttingToolDto("GRINDING_WHEEL", BigDecimal.valueOf(200), "Al2O3", "-");
            case EDM, LASER -> new CuttingToolDto("EDM_WIRE", BigDecimal.valueOf(0.3), "-", "-");
            case CONTROL -> null;
        };
    }
}