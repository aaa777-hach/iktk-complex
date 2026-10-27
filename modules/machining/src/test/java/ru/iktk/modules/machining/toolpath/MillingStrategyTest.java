package ru.iktk.modules.machining.toolpath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.iktk.modules.machining.config.MachiningProperties;
import ru.iktk.modules.machining.dto.EquipmentInfo;
import ru.iktk.modules.machining.model.CuttingMode;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.model.MachiningMethod;
import ru.iktk.modules.machining.model.MachiningOperation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MillingStrategyTest {

    private MillingStrategy strategy;
    private EquipmentInfo equipment;
    private MachiningProperties props;

    @BeforeEach
    void setUp() {
        strategy = new MillingStrategy();
        equipment = new EquipmentInfo("EQ-1", "CNC-1", "ISO6983", 3, Map.of());
        props = new MachiningProperties(); // L=W=100, H=20
    }

    private MachiningOperation op(BigDecimal toolDiameter, BigDecimal depth, BigDecimal feed) {
        CuttingTool tool = toolDiameter == null ? null
                : CuttingTool.builder().type("END_MILL").diameter(toolDiameter).build();
        CuttingMode mode = CuttingMode.builder()
                .method(MachiningMethod.MILLING)
                .depthOfCutMm(depth)
                .feedMmPerRev(feed)
                .build();
        return MachiningOperation.builder()
                .order(1).name("Test MILLING").method(MachiningMethod.MILLING)
                .cnc(true).tool(tool).cuttingMode(mode)
                .build();
    }

    @Test
    void defaultBlank_zigzag_32points() {
        // L=W=100, tool=10 -> step=7, y=0,7,...,98 (15 ит.), 1 RAPID + 30 FEED + 1 RAPID
        Toolpath path = strategy.generate(op(BigDecimal.TEN, BigDecimal.valueOf(2), BigDecimal.valueOf(0.2)), equipment, props);
        List<ToolpathPoint> points = path.getPoints();

        assertThat(points).hasSize(32);

        ToolpathPoint first = points.get(0);
        assertThat(first.getType()).isEqualTo("RAPID");
        assertThat(first.getX()).isEqualByComparingTo("0");
        assertThat(first.getY()).isEqualByComparingTo("0");
        assertThat(first.getZ()).isEqualByComparingTo("0");

        ToolpathPoint last = points.get(points.size() - 1);
        assertThat(last.getType()).isEqualTo("RAPID");
        assertThat(last.getX()).isEqualByComparingTo("100");
        assertThat(last.getY()).isEqualByComparingTo("98");
        assertThat(last.getZ()).isEqualByComparingTo("0");

        // X в {0, 100}
        Set<BigDecimal> xs = points.stream().map(ToolpathPoint::getX).collect(Collectors.toSet());
        assertThat(xs.stream().map(BigDecimal::stripTrailingZeros))
                .containsExactlyInAnyOrder(
                        BigDecimal.ZERO.stripTrailingZeros(),
                        BigDecimal.valueOf(100).stripTrailingZeros());

        // Все FEED на Z=-2
        assertThat(points.stream().filter(p -> "FEED".equals(p.getType())))
                .allMatch(p -> p.getZ().compareTo(BigDecimal.valueOf(-2)) == 0);
    }

    @Test
    void customBlank_50x50_18points() {
        props.setBlankLength(BigDecimal.valueOf(50));
        props.setBlankWidth(BigDecimal.valueOf(50));

        Toolpath path = strategy.generate(op(BigDecimal.TEN, BigDecimal.valueOf(2), BigDecimal.valueOf(0.2)), equipment, props);

        assertThat(path.getPoints()).hasSize(18);
        Set<BigDecimal> xs = path.getPoints().stream().map(ToolpathPoint::getX).collect(Collectors.toSet());
        assertThat(xs).hasSize(2);
        // X в {0, 50}
        assertThat(xs.stream().map(BigDecimal::stripTrailingZeros))
                .containsExactlyInAnyOrder(
                        BigDecimal.ZERO.stripTrailingZeros(),
                        BigDecimal.valueOf(50).stripTrailingZeros());
        // Y max ≤ 50
        assertThat(path.getPoints().stream().map(ToolpathPoint::getY).max(BigDecimal::compareTo))
                .get().matches(y -> ((BigDecimal) y).compareTo(BigDecimal.valueOf(50)) <= 0);
    }

    @Test
    void nullTool_usesDefaultDiameter10_step7() {
        // tool=null -> resolveStep -> 10 * 0.7 = 7
        Toolpath path = strategy.generate(op(null, BigDecimal.valueOf(2), BigDecimal.valueOf(0.2)), equipment, props);
        assertThat(path.getPoints()).hasSize(32);
    }

    @Test
    void depthFromCuttingMode() {
        Toolpath path = strategy.generate(op(BigDecimal.TEN, BigDecimal.valueOf(2.5), BigDecimal.valueOf(0.2)), equipment, props);
        assertThat(path.getPoints().stream().filter(p -> "FEED".equals(p.getType())))
                .allMatch(p -> p.getZ().compareTo(BigDecimal.valueOf(-2.5)) == 0);
    }

    @Test
    void noDuplicateFeedAtOrigin_afterC310() {
        // До C.3.10 был дубль FEED(0,0,-2) — подвод + первая точка зигзага
        Toolpath path = strategy.generate(op(BigDecimal.TEN, BigDecimal.valueOf(2), BigDecimal.valueOf(0.2)), equipment, props);
        long feedAtOrigin = path.getPoints().stream()
                .filter(p -> "FEED".equals(p.getType()))
                .filter(p -> p.getX().signum() == 0 && p.getY().signum() == 0)
                .count();
        assertThat(feedAtOrigin).isEqualTo(1L);
    }
}