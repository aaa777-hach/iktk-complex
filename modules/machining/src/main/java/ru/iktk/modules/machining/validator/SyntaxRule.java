package ru.iktk.modules.machining.validator;

import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.EquipmentInfo;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Синтаксическое правило: каждая строка G-кода должна соответствовать
 * одному из форматов ISO 6983.
 *
 * <p>Допустимые строки:
 * <ul>
 *   <li>{@code ; ...} — комментарий;</li>
 *   <li>{@code %} — начало/конец программы;</li>
 *   <li>{@code O<digits> (...)} — имя программы;</li>
 *   <li>{@code M<digits> ...} — M-код;</li>
 *   <li>{@code G<digits> [X.. Y.. Z.. F..]} — G-код с параметрами;</li>
 *   <li>пустая строка.</li>
 * </ul>
 */
@Component
public class SyntaxRule implements GCodeRule {

    private static final Pattern VALID_LINE = Pattern.compile(
            "^\\s*(?:;.*|%|O\\d+.*|M\\d+.*|G\\d+(?:\\s+[XYZF]-?\\d+(?:\\.\\d+)?)*)?\\s*$");

    @Override
    public String name() {
        return "SYNTAX";
    }

    @Override
    public void check(String gcode, EquipmentInfo equipment, MachiningProperties properties,
                      boolean fullProgram, List<String> errors, List<String> warnings) {
        if (gcode == null || gcode.isBlank()) {
            if (fullProgram) {
                errors.add("[SYNTAX] Программа пуста");
            }
            return;
        }
        String[] lines = gcode.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (!VALID_LINE.matcher(line).matches()) {
                errors.add(String.format("[SYNTAX] Строка %d: недопустимый формат '%s'",
                        i + 1, abbreviate(line)));
            }
        }
    }

    private String abbreviate(String s) {
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }
}