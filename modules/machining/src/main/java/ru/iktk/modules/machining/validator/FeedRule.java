package ru.iktk.modules.machining.validator;

import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.EquipmentInfo;

import java.util.List;

/**
 * Правило подачи: предупреждает о {@code G01} без {@code F} и
 * {@code G00} с {@code F}. Оба случая — warning, не error
 * (унаследованная подача / лишний параметр допустимы).
 */
@Component
public class FeedRule implements GCodeRule {

    @Override
    public String name() {
        return "FEED";
    }

    @Override
    public void check(String gcode, EquipmentInfo equipment, MachiningProperties properties,
                      boolean fullProgram, List<String> errors, List<String> warnings) {
        if (gcode == null || gcode.isBlank()) return;

        String[] lines = gcode.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || !line.startsWith("G")) continue;
            boolean g01 = line.startsWith("G01");
            boolean g00 = line.startsWith("G00");
            boolean hasF = line.contains("F");
            if (g01 && !hasF) {
                warnings.add(String.format("[FEED] Строка %d: G01 без F (подача унаследована)", i + 1));
            }
            if (g00 && hasF) {
                warnings.add(String.format("[FEED] Строка %d: G00 с F (подача не нужна)", i + 1));
            }
        }
    }
}