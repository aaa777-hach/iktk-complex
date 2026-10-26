package ru.iktk.modules.machining.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.dto.EquipmentInfo;

/**
 * Валидатор G-кода.
 *
 * <p>В текущей версии — заглушка: всегда возвращает OK.
 */
@Component
public class GCodeValidator {

    private static final Logger log = LoggerFactory.getLogger(GCodeValidator.class);

    public ValidationResult validate(String program, EquipmentInfo equipment) {
        log.info("GCodeValidator.validate (STUB): lines={}, equipment={}",
                program == null ? 0 : program.split("\n", -1).length,
                equipment == null ? "—" : equipment.getModel());
        return ValidationResult.ok();
    }

    public ValidationResult validateFragment(String gcode, EquipmentInfo equipment) {
        return ValidationResult.ok();
    }
}