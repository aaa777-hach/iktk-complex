package ru.iktk.modules.machining.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.EquipmentInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Валидатор G-кода: оркестрирует {@link GCodeRule}, собирает
 * {@link ValidationResult}.
 *
 * <p>Два режима:
 * <ul>
 *   <li>{@link #validate(String, EquipmentInfo)} — полная УП (%, O, M30);</li>
 *   <li>{@link #validateFragment(String, EquipmentInfo)} — фрагмент операции
 *       (только G-код, без header/footer).</li>
 * </ul>
 */
@Component
public class GCodeValidator {

    private static final Logger log = LoggerFactory.getLogger(GCodeValidator.class);

    private final List<GCodeRule> rules;
    private final MachiningProperties properties;

    public GCodeValidator(List<GCodeRule> rules, MachiningProperties properties) {
        this.rules = rules;
        this.properties = properties;
        log.info("GCodeValidator: зарегистрировано {} правил: {}",
                rules.size(), rules.stream().map(GCodeRule::name).toList());
    }

    public ValidationResult validate(String program, EquipmentInfo equipment) {
        return run(program, equipment, true);
    }

    public ValidationResult validateFragment(String gcode, EquipmentInfo equipment) {
        return run(gcode, equipment, false);
    }

    private ValidationResult run(String gcode, EquipmentInfo equipment, boolean fullProgram) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (GCodeRule rule : rules) {
            try {
                rule.check(gcode, equipment, properties, fullProgram, errors, warnings);
            } catch (Exception e) {
                errors.add(String.format("[%s] Ошибка правила: %s", rule.name(), e.getMessage()));
                log.warn("GCodeValidator: правило {} упало", rule.name(), e);
            }
        }
        boolean valid = errors.isEmpty();
        int lines = gcode == null ? 0 : gcode.split("\n", -1).length;
        log.info("GCodeValidator: fullProgram={}, lines={}, valid={}, errors={}, warnings={}",
                fullProgram, lines, valid, errors.size(), warnings.size());
        if (!valid) {
            log.warn("GCodeValidator: ошибки: {}", errors);
        }
        return new ValidationResult(valid, errors, warnings);
    }
}