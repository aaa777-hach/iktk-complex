package ru.iktk.modules.machining.toolpath;

import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.EquipmentInfo;
import ru.iktk.modules.machining.model.MachiningMethod;
import ru.iktk.modules.machining.model.MachiningOperation;

/**
 * Стратегия генерации траектории инструмента.
 *
 * <p>Каждая стратегия реализует {@link #supports(MachiningMethod)} и
 * {@link #generate(MachiningOperation, EquipmentInfo, MachiningProperties)}.
 * {@link ToolpathGenerator} выбирает первую подходящую стратегию (по порядку
 * регистрации в Spring), при отсутствии — fallback.
 */
public interface ToolpathStrategy {

    /** Поддерживает ли стратегия данный метод обработки. */
    boolean supports(MachiningMethod method);

    /** Сгенерировать траекторию. */
    Toolpath generate(MachiningOperation op, EquipmentInfo equipment, MachiningProperties properties);
}