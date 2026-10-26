package ru.iktk.modules.machining.toolpath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.EquipmentInfo;
import ru.iktk.modules.machining.model.MachiningMethod;
import ru.iktk.modules.machining.model.MachiningOperation;

import java.util.List;

/**
 * Fallback-стратегия: пустой Toolpath.
 *
 * <p>Используется для методов, не поддержанных другими стратегиями
 * (CONTROL, GRINDING, EDM, LASER). {@code CncProgramService} корректно
 * обрабатывает пустую траекторию (ветка «траектория не сгенерирована»).
 *
 * <p><b>Не {@code @Component}</b> — инстанцируется в {@link ToolpathGenerator}
 * вручную, чтобы не конфликтовать с другими стратегиями в {@code List<>}.
 */
public class FallbackStrategy implements ToolpathStrategy {

    private static final Logger log = LoggerFactory.getLogger(FallbackStrategy.class);

    @Override
    public boolean supports(MachiningMethod method) {
        return false; // никогда не выбирается через supports
    }

    @Override
    public Toolpath generate(MachiningOperation op, EquipmentInfo equipment, MachiningProperties properties) {
        log.warn("FallbackStrategy.generate: метод {} не поддержан, пустая траектория (op={})",
                op == null ? null : op.getMethod(), op == null ? null : op.getName());
        return new Toolpath(List.of());
    }
}