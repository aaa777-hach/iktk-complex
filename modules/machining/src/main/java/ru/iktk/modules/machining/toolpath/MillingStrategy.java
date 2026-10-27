package ru.iktk.modules.machining.toolpath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.EquipmentInfo;
import ru.iktk.modules.machining.model.CuttingMode;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.model.MachiningMethod;
import ru.iktk.modules.machining.model.MachiningOperation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Стратегия торцевого фрезерования (зигзаг по заготовке).
 *
 * <p>Заготовка: {@code L × W} (из {@link MachiningProperties#getBlankLength()}
 * и {@link MachiningProperties#getBlankWidth()}). Инструмент проходит
 * зигзагом вдоль оси X с шагом {@code 0.7 × diameter}, на глубине
 * {@code depthOfCut}.
 */
@Component
public class MillingStrategy implements ToolpathStrategy {

    private static final Logger log = LoggerFactory.getLogger(MillingStrategy.class);

    @Override
    public boolean supports(MachiningMethod method) {
        return method == MachiningMethod.MILLING || method == MachiningMethod.BORING;
    }

    @Override
    public Toolpath generate(MachiningOperation op, EquipmentInfo equipment, MachiningProperties properties) {
        BigDecimal L = nvl(properties.getBlankLength(), BigDecimal.valueOf(100));
        BigDecimal W = nvl(properties.getBlankWidth(), BigDecimal.valueOf(100));
        BigDecimal depth = resolveDepth(op);
        BigDecimal feed = resolveFeed(op);
        BigDecimal step = resolveStep(op);

        log.info("MillingStrategy.generate: op={}, L={}, W={}, depth={}, feed={}, step={}",
                op.getName(), L, W, depth, feed, step);

        List<ToolpathPoint> points = new ArrayList<>();
        // подвод (RAPID) — врезание даст первая точка зигзага при y=0
        points.add(pt(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "RAPID", null));

        boolean forward = true;
        BigDecimal y = BigDecimal.ZERO;
        while (y.compareTo(W) <= 0) {
            if (forward) {
                points.add(pt(BigDecimal.ZERO, y, depth.negate(), "FEED", feed));
                points.add(pt(L, y, depth.negate(), "FEED", feed));
            } else {
                points.add(pt(L, y, depth.negate(), "FEED", feed));
                points.add(pt(BigDecimal.ZERO, y, depth.negate(), "FEED", feed));
            }
            forward = !forward;
            y = y.add(step);
        }

        // отвод
        BigDecimal lastY = y.subtract(step).max(BigDecimal.ZERO);
        points.add(pt(forward ? BigDecimal.ZERO : L, lastY, BigDecimal.ZERO, "RAPID", null));

        log.info("MillingStrategy.generate: points={}", points.size());
        return new Toolpath(points);
    }

    private BigDecimal resolveDepth(MachiningOperation op) {
        CuttingMode mode = op.getCuttingMode();
        if (mode != null && mode.getDepthOfCutMm() != null && mode.getDepthOfCutMm().compareTo(BigDecimal.ZERO) > 0) {
            return mode.getDepthOfCutMm();
        }
        return BigDecimal.valueOf(1);
    }

    private BigDecimal resolveFeed(MachiningOperation op) {
        CuttingMode mode = op.getCuttingMode();
        if (mode != null && mode.getFeedMmPerRev() != null) {
            return mode.getFeedMmPerRev();
        }
        return BigDecimal.valueOf(0.2);
    }

    private BigDecimal resolveStep(MachiningOperation op) {
        CuttingTool tool = op.getTool();
        BigDecimal d = (tool != null && tool.getDiameter() != null)
                ? tool.getDiameter() : BigDecimal.TEN;
        return d.multiply(BigDecimal.valueOf(0.7));
    }

    private ToolpathPoint pt(BigDecimal x, BigDecimal y, BigDecimal z, String type, BigDecimal feed) {
        return new ToolpathPoint(
                scale(x), scale(y), scale(z), type, feed
        );
    }

    private BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal v, BigDecimal def) {
        return v == null ? def : v;
    }
}