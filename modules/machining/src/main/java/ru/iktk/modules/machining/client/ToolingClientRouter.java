package ru.iktk.modules.machining.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.client.dto.ToolingDto;
import ru.iktk.modules.machining.dto.GeometryData;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.model.MachiningMethod;

import java.math.BigDecimal;

/**
 * Router выбора инструмента: Feign → tooling-service, при ошибке — fallback на встроенный справочник.
 *
 * <p>Причина: tooling-service может быть недоступен (dev/CI). Fallback повторяет
 * логику удалённого {@code ToolingClientStub}, чтобы machining не падал.
 */
@Component
public class ToolingClientRouter {

    private static final Logger log = LoggerFactory.getLogger(ToolingClientRouter.class);

    private final ToolingClient feign;

    public ToolingClientRouter(ToolingClient feign) {
        this.feign = feign;
    }

    /**
     * Подобрать инструмент: Feign → fallback.
     *
     * @param method   метод обработки
     * @param geometry геометрия (материал передаётся в tooling)
     * @return инструмент, {@code null} для CONTROL или null method
     */
    public CuttingTool selectTool(MachiningMethod method, GeometryData geometry) {
        if (method == null) {
            return null;
        }
        String material = (geometry != null) ? geometry.getMaterial() : null;
        try {
            ResponseEntity<ToolingDto> resp = feign.select(method.name(), material);
            int status = resp.getStatusCode().value();
            if (status == 204) {
                log.info("ToolingClientRouter: tool not required (method={})", method);
                return null;
            }
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                log.info("ToolingClientRouter: tooling OK (method={}, material={}, type={})",
                        method, material, resp.getBody().getType());
                return toCuttingTool(resp.getBody());
            }
            log.warn("ToolingClientRouter: unexpected status={} from tooling, fallback", status);
            return fallback(method);
        } catch (Exception e) {
            log.warn("ToolingClientRouter: tooling unavailable ({}), fallback", e.getMessage());
            return fallback(method);
        }
    }

    private CuttingTool toCuttingTool(ToolingDto d) {
        return CuttingTool.builder()
                .type(d.getType())
                .diameter(d.getDiameter())
                .material(d.getMaterial())
                .coating(d.getCoating())
                .build();
    }

    /** Fallback — копия удалённого ToolingClientStub. */
    private CuttingTool fallback(MachiningMethod method) {
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