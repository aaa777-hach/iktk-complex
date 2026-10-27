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

class DrillingStrategyTest {

    private DrillingStrategy strategy;
    private EquipmentInfo equipment;
    private MachiningProperties props;

    @BeforeEach
    void setUp() {
        strategy = new DrillingStrategy();
        equipment = new EquipmentInfo("EQ-1", "CNC-1", "ISO6983", 3, Map.of());
        props = new MachiningProperties();
    }

    private MachiningOperation op(BigDecimal depth, BigDecimal feed) {
        CuttingTool tool = CuttingTool.builder().type("DRILL").diameter(BigDecimal.valueOf(5)).build();
        CuttingMode mode = CuttingMode.builder()
                .method(MachiningMethod.DRILLING)
                .depthOfCutMm(depth)
                .feedMmPerRev(feed)
                .build();
        return MachiningOperation.builder()
                .order(1).name("Test DRILLING").method(MachiningMethod.DRILLING)
                .cnc(true).tool(tool).cuttingMode(mode)
                .build();
    }

    @Test
    void fourHoles_12points() {
        Toolpath path = strategy.generate(op(BigDecimal.valueOf(2), BigDecimal.valueOf(0.2)), equipment, props);
        List<ToolpathPoint> points = path.getPoints();

        assertThat(points).hasSize(12);

        // X в {33.33, 66.67}, Y в {33.33, 66.67}
        Set<BigDecimal> xs = points.stream().map(ToolpathPoint::getX).collect(Collectors.toSet());
        Set<BigDecimal> ys = points.stream().map(ToolpathPoint::getY).collect(Collectors.toSet());
        assertThat(xs.stream().map(BigDecimal::stripTrailingZeros))
                .containsExactlyInAnyOrder(
                        BigDecimal.valueOf(33.33).stripTrailingZeros(),
                        BigDecimal.valueOf(66.67).stripTrailingZeros());
        assertThat(ys.stream().map(BigDecimal::stripTrailingZeros))
                .containsExactlyInAnyOrder(
                        BigDecimal.valueOf(33.33).stripTrailingZeros(),
                        BigDecimal.valueOf(66.67).stripTrailingZeros());

        // Первая точка: RAPID (33.33, 33.33, 0)
        ToolpathPoint first = points.get(0);
        assertThat(first.getType()).isEqualTo("RAPID");
        assertThat(first.getX()).isEqualByComparingTo("33.33");
        assertThat(first.getY()).isEqualByComparingTo("33.33");
        assertThat(first.getZ()).isEqualByComparingTo("0");
    }

    @Test
    void holePattern_rapidFeedRapid() {
        Toolpath path = strategy.generate(op(BigDecimal.valueOf(2), BigDecimal.valueOf(0.2)), equipment, props);
        List<ToolpathPoint> p = path.getPoints();

        for (int i = 0; i < 4; i++) {
            int base = i * 3;
            assertThat(p.get(base).getType()).isEqualTo("RAPID");
            assertThat(p.get(base).getZ()).isEqualByComparingTo("0");

            assertThat(p.get(base + 1).getType()).isEqualTo("FEED");
            assertThat(p.get(base + 1).getZ()).isEqualByComparingTo("-2");

            assertThat(p.get(base + 2).getType()).isEqualTo("RAPID");
            assertThat(p.get(base + 2).getZ()).isEqualByComparingTo("0");

            // X и Y одинаковы во всех 3 точках одного отверстия
            assertThat(p.get(base).getX()).isEqualByComparingTo(p.get(base + 1).getX());
            assertThat(p.get(base).getY()).isEqualByComparingTo(p.get(base + 1).getY());
            assertThat(p.get(base).getX()).isEqualByComparingTo(p.get(base + 2).getX());
            assertThat(p.get(base).getY()).isEqualByComparingTo(p.get(base + 2).getY());
        }
    }

    @Test
    void depthFromMode() {
        Toolpath path = strategy.generate(op(BigDecimal.valueOf(1.5), BigDecimal.valueOf(0.15)), equipment, props);
        assertThat(path.getPoints().stream().filter(p -> "FEED".equals(p.getType())))
                .allMatch(p -> p.getZ().compareTo(BigDecimal.valueOf(-1.5)) == 0);
    }
}