package ru.iktk.modules.machining.toolpath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.EquipmentInfo;
import ru.iktk.modules.machining.model.CuttingMode;
import ru.iktk.modules.machining.model.MachiningMethod;
import ru.iktk.modules.machining.model.MachiningOperation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Стратегия сверления (сетка отверстий по заготовке).
 *
 * <p>Заготовка: {@code L × W}. Сетка {@code 2 × 2} отверстия с отступом
 * {@code L/3}, {@code 2L/3} по X и {@code W/3}, {@code 2W/3} по Y.
 */
@Component
public class DrillingStrategy implements ToolpathStrategy {

    private static final Logger log = LoggerFactory.getLogger(DrillingStrategy.class);

    @Override
    public boolean supports(MachiningMethod method) {
        return method == MachiningMethod.DRILLING || method == MachiningMethod.REAMING;
    }

    @Override
    public Toolpath generate(MachiningOperation op, EquipmentInfo equipment, MachiningProperties properties) {
        BigDecimal L = nvl(properties.getBlankLength(), BigDecimal.valueOf(100));
        BigDecimal W = nvl(properties.getBlankWidth(), BigDecimal.valueOf(100));
        BigDecimal depth = resolveDepth(op);
        BigDecimal feed = resolveFeed(op);

        log.info("DrillingStrategy.generate: op={}, L={}, W={}, depth={}, feed={}",
                op.getName(), L, W, depth, feed);

        List<ToolpathPoint> points = new ArrayList<>();
        BigDecimal x1 = L.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        BigDecimal x2 = L.multiply(BigDecimal.valueOf(2)).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        BigDecimal y1 = W.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        BigDecimal y2 = W.multiply(BigDecimal.valueOf(2)).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);

        for (BigDecimal x : List.of(x1, x2)) {
            for (BigDecimal y : List.of(y1, y2)) {
                points.add(pt(x, y, BigDecimal.ZERO, "RAPID", null));
                points.add(pt(x, y, depth.negate(), "FEED", feed));
                points.add(pt(x, y, BigDecimal.ZERO, "RAPID", null));
            }
        }

        log.info("DrillingStrategy.generate: points={} (holes={})", points.size(), points.size() / 3);
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