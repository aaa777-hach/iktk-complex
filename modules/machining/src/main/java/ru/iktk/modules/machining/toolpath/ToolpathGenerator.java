package ru.iktk.modules.machining.toolpath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.EquipmentInfo;
import ru.iktk.modules.machining.model.MachiningOperation;

import java.util.List;

/**
 * Генератор траектории инструмента.
 *
 * <p>Router: выбирает первую подходящую {@link ToolpathStrategy} по
 * {@link ToolpathStrategy#supports(MachiningMethod)}, при отсутствии —
 * {@link FallbackStrategy} (пустой Toolpath).
 */
@Component
public class ToolpathGenerator {

    private static final Logger log = LoggerFactory.getLogger(ToolpathGenerator.class);

    private final List<ToolpathStrategy> strategies;
    private final MachiningProperties properties;
    private final FallbackStrategy fallback = new FallbackStrategy();

    public ToolpathGenerator(List<ToolpathStrategy> strategies, MachiningProperties properties) {
        this.strategies = strategies;
        this.properties = properties;
        log.info("ToolpathGenerator: зарегистрировано {} стратегий", strategies.size());
    }

    public Toolpath generate(MachiningOperation op, EquipmentInfo equipment) {
        if (op == null) {
            log.warn("ToolpathGenerator.generate: op == null, пустая траектория");
            return new Toolpath(List.of());
        }
        ToolpathStrategy strategy = strategies.stream()
                .filter(s -> s.supports(op.getMethod()))
                .findFirst()
                .orElse(fallback);
        Toolpath path = strategy.generate(op, equipment, properties);
        log.info("ToolpathGenerator.generate: op={}, method={}, strategy={}, points={}",
                op.getName(), op.getMethod(), strategy.getClass().getSimpleName(),
                path.getPoints().size());
        return path;
    }
}