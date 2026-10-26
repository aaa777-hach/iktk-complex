package ru.iktk.modules.machining.validator;

import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.EquipmentInfo;

import java.util.List;

/**
 * Структурное правило: для полной УП (fullProgram=true) проверяет
 * наличие {@code %} в начале, {@code M30} в конце.
 *
 * <p>Для фрагментов (fullProgram=false) правило ничего не делает —
 * фрагмент не содержит header/footer.
 */
@Component
public class StructureRule implements GCodeRule {

    @Override
    public String name() {
        return "STRUCTURE";
    }

    @Override
    public void check(String gcode, EquipmentInfo equipment, MachiningProperties properties,
                      boolean fullProgram, List<String> errors, List<String> warnings) {
        if (!fullProgram) return;
        if (gcode == null || gcode.isBlank()) return;

        String[] lines = gcode.split("\n", -1);
        String first = firstMeaningful(lines);
        String last = lastMeaningful(lines);

        if (first == null) {
            errors.add("[STRUCTURE] Полная УП пуста");
            return;
        }
        if (!first.startsWith("%")) {
            errors.add("[STRUCTURE] УП должна начинаться с '%', найдено: " + abbreviate(first));
        }
        if (last == null || !last.startsWith("M30")) {
            errors.add("[STRUCTURE] УП должна заканчиваться 'M30', найдено: "
                    + (last == null ? "<пусто>" : abbreviate(last)));
        }
    }

    private String firstMeaningful(String[] lines) {
        for (String l : lines) {
            String t = l.trim();
            if (!t.isEmpty()) return t;
        }
        return null;
    }

    private String lastMeaningful(String[] lines) {
        for (int i = lines.length - 1; i >= 0; i--) {
            String t = lines[i].trim();
            if (!t.isEmpty()) return t;
        }
        return null;
    }

    private String abbreviate(String s) {
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }
}