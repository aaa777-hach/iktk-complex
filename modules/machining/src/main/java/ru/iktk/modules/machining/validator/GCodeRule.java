package ru.iktk.modules.machining.validator;

import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.EquipmentInfo;

import java.util.List;

/**
 * Правило валидации G-кода.
 *
 * <p>Каждое правило добавляет errors (блокируют) и warnings (не блокируют)
 * в переданные списки. {@link GCodeValidator} оркестрирует вызов всех правил
 * и собирает {@link ValidationResult}.
 */
public interface GCodeRule {

    /** Имя правила (для логов/сообщений). */
    String name();

    /**
     * Проверить G-код.
     *
     * @param gcode        исходный G-код
     * @param equipment    станок (может быть null)
     * @param properties   настройки (заготовка, границы)
     * @param fullProgram  true — полная УП (%, O, M30); false — фрагмент операции
     * @param errors       сюда добавляются блокирующие ошибки
     * @param warnings     сюда добавляются неблокирующие предупреждения
     */
    void check(String gcode, EquipmentInfo equipment, MachiningProperties properties,
               boolean fullProgram, List<String> errors, List<String> warnings);
}