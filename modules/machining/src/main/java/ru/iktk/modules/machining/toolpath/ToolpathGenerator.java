package ru.iktk.modules.machining.toolpath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.dto.EquipmentInfo;
import ru.iktk.modules.machining.model.MachiningOperation;

import java.util.List;

/**
 * Генератор траектории инструмента.
 *
 * <p>В текущей версии — заглушка: возвращает пустую траекторию.
 * CncProgramService корректно обрабатывает этот случай.
 */
@Component
public class ToolpathGenerator {

    private static final Logger log = LoggerFactory.getLogger(ToolpathGenerator.class);

    public Toolpath generate(MachiningOperation op, EquipmentInfo equipment) {
        log.info("ToolpathGenerator.generate (STUB): operation={}, equipment={}",
                op == null ? "—" : op.getName(),
                equipment == null ? "—" : equipment.getModel());
        return new Toolpath(List.of());
    }
}