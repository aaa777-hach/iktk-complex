package ru.iktk.modules.machining.postprocessor;

import ru.iktk.modules.machining.dto.EquipmentInfo;
import ru.iktk.modules.machining.model.CuttingMode;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.toolpath.Toolpath;

/**
 * Постпроцессор: преобразование траектории в машинный код конкретной СЧПУ.
 */
public interface Postprocessor {

    /** Имя постпроцессора (например, ISO6983, Fanuc, Siemens). */
    String getName();

    /** Преобразовать траекторию в G-код. */
    String process(Toolpath toolpath, CuttingTool tool, CuttingMode mode);

    /** Заголовок программы. */
    String header(EquipmentInfo equipment);

    /** Конец программы. */
    String footer(EquipmentInfo equipment);
}