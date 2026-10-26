package ru.iktk.modules.machining.postprocessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.dto.EquipmentInfo;
import ru.iktk.modules.machining.model.CuttingMode;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.toolpath.Toolpath;
import ru.iktk.modules.machining.toolpath.ToolpathPoint;

/**
 * Базовый постпроцессор ISO 6983 (G-код).
 *
 * <p>Упрощённая реализация для C.1: формирует корректный header/footer
 * и заглушки для операций. Реальная генерация — в C.3 (ToolpathGenerator).
 */
@Component
public class Iso6983Postprocessor implements Postprocessor {

    private static final Logger log = LoggerFactory.getLogger(Iso6983Postprocessor.class);

    public static final String NAME = "ISO6983";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String process(Toolpath toolpath, CuttingTool tool, CuttingMode mode) {
        StringBuilder sb = new StringBuilder();
        sb.append("; tool=").append(tool == null ? "-" : tool.getType()).append("\n");
        sb.append("; mode=").append(mode == null ? "-" : mode.getMethod()).append("\n");
        if (toolpath != null && toolpath.getPoints() != null && !toolpath.getPoints().isEmpty()) {
            for (ToolpathPoint p : toolpath.getPoints()) {
                boolean rapid = "RAPID".equalsIgnoreCase(p.getType());
                sb.append(rapid ? "G00" : "G01")
                  .append(" X").append(p.getX())
                  .append(" Y").append(p.getY())
                  .append(" Z").append(p.getZ());
                if (!rapid && p.getFeed() != null) {
                    sb.append(" F").append(p.getFeed());
                }
                sb.append("\n");
            }
        } else {
            sb.append("; toolpath empty\n");
        }
        log.debug("Iso6983Postprocessor.process: points={}",
                toolpath == null || toolpath.getPoints() == null ? 0 : toolpath.getPoints().size());
        return sb.toString();
    }

    @Override
    public String header(EquipmentInfo equipment) {
        return "%\nO0001 (" + (equipment == null ? "UNKNOWN" : equipment.getModel()) + ")\n";
    }

    @Override
    public String footer(EquipmentInfo equipment) {
        return "%\nM30\n";
    }
}