package ru.iktk.modules.machining.validator;

import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.EquipmentInfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Правило границ: координаты X/Y/Z не должны выходить за заготовку
 * (с запасом margin).
 *
 * <p>Границы:
 * <ul>
 *   <li>X ∈ [−marginXY, blankLength + marginXY]</li>
 *   <li>Y ∈ [−marginXY, blankWidth + marginXY]</li>
 *   <li>Z ∈ [−blankHeight, marginZ]</li>
 * </ul>
 * marginXY = max(5, 5% от размера), marginZ = 5.
 */
@Component
public class BoundsRule implements GCodeRule {

    private static final Pattern COORD = Pattern.compile("([XYZ])(-?\\d+(?:\\.\\d+)?)");
    private static final BigDecimal MARGIN_XY = BigDecimal.valueOf(5);
    private static final BigDecimal MARGIN_Z = BigDecimal.valueOf(5);
    private static final BigDecimal MIN_MARGIN = BigDecimal.valueOf(5);

    @Override
    public String name() {
        return "BOUNDS";
    }

    @Override
    public void check(String gcode, EquipmentInfo equipment, MachiningProperties properties,
                      boolean fullProgram, List<String> errors, List<String> warnings) {
        if (gcode == null || gcode.isBlank()) return;

        BigDecimal L = nvl(properties.getBlankLength(), BigDecimal.valueOf(100));
        BigDecimal W = nvl(properties.getBlankWidth(), BigDecimal.valueOf(100));
        BigDecimal H = nvl(properties.getBlankHeight(), BigDecimal.valueOf(20));

        BigDecimal mx = maxPct(L, MARGIN_XY);
        BigDecimal my = maxPct(W, MARGIN_XY);

        BigDecimal xMin = mx.negate();
        BigDecimal xMax = L.add(mx);
        BigDecimal yMin = my.negate();
        BigDecimal yMax = W.add(my);
        BigDecimal zMin = H.negate();
        BigDecimal zMax = MARGIN_Z;

        String[] lines = gcode.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.startsWith("G")) continue;
            Matcher m = COORD.matcher(line);
            while (m.find()) {
                String axis = m.group(1);
                BigDecimal val;
                try {
                    val = new BigDecimal(m.group(2));
                } catch (NumberFormatException e) {
                    continue;
                }
                switch (axis) {
                    case "X" -> checkAxis("X", val, xMin, xMax, i + 1, errors);
                    case "Y" -> checkAxis("Y", val, yMin, yMax, i + 1, errors);
                    case "Z" -> checkAxis("Z", val, zMin, zMax, i + 1, errors);
                    default -> { }
                }
            }
        }
    }

    private void checkAxis(String axis, BigDecimal val, BigDecimal min, BigDecimal max,
                           int lineNo, List<String> errors) {
        if (val.compareTo(min) < 0 || val.compareTo(max) > 0) {
            errors.add(String.format("[BOUNDS] Строка %d: %s=%s вне [%s, %s]",
                    lineNo, axis, val, min, max));
        }
    }

    private BigDecimal maxPct(BigDecimal size, BigDecimal fallback) {
        BigDecimal pct = size.multiply(BigDecimal.valueOf(0.05));
        return pct.max(fallback).max(MIN_MARGIN);
    }

    private BigDecimal nvl(BigDecimal v, BigDecimal def) {
        return v == null ? def : v;
    }
}