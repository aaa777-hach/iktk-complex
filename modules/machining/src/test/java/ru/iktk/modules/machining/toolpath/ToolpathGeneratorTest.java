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

import static org.assertj.core.api.Assertions.assertThat;

class ToolpathGeneratorTest {

    private ToolpathGenerator generator;
    private EquipmentInfo equipment;
    private MachiningProperties props;

    @BeforeEach
    void setUp() {
        props = new MachiningProperties();
        generator = new ToolpathGenerator(
                List.of(new MillingStrategy(), new DrillingStrategy()),
                props
        );
        equipment = new EquipmentInfo("EQ-1", "CNC-1", "ISO6983", 3, Map.of());
    }

    private MachiningOperation op(MachiningMethod method) {
        CuttingTool tool = CuttingTool.builder().type("X").diameter(BigDecimal.TEN).build();
        CuttingMode mode = CuttingMode.builder()
                .method(method).depthOfCutMm(BigDecimal.valueOf(2)).feedMmPerRev(BigDecimal.valueOf(0.2))
                .build();
        return MachiningOperation.builder()
                .order(1).name("Test " + method).method(method)
                .cnc(true).tool(tool).cuttingMode(mode)
                .build();
    }

    @Test
    void milling_routesToMillingStrategy() {
        Toolpath p = generator.generate(op(MachiningMethod.MILLING), equipment);
        assertThat(p.getPoints()).isNotEmpty();
        assertThat(p.getPoints().get(0).getType()).isEqualTo("RAPID");
    }

    @Test
    void drilling_routesToDrillingStrategy() {
        Toolpath p = generator.generate(op(MachiningMethod.DRILLING), equipment);
        assertThat(p.getPoints()).hasSize(12); // 4 отверстия × 3
    }

    @Test
    void grinding_fallsBack_emptyToolpath() {
        Toolpath p = generator.generate(op(MachiningMethod.GRINDING), equipment);
        assertThat(p.getPoints()).isEmpty();
    }

    @Test
    void nullOperation_emptyToolpath() {
        Toolpath p = generator.generate(null, equipment);
        assertThat(p.getPoints()).isEmpty();
    }
}