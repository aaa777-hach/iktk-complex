package ru.iktk.modules.machining.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.iktk.modules.machining.model.CuttingTool;
import ru.iktk.modules.machining.model.MachiningMethod;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MachiningPropertiesTest {

    private MachiningProperties props;

    @BeforeEach
    void setUp() {
        props = new MachiningProperties();
    }

    @Test
    void steel45_milling() {
        assertThat(props.getCuttingSpeed(MachiningMethod.MILLING, "Steel-45", null))
                .isEqualByComparingTo(BigDecimal.valueOf(120));
        assertThat(props.getFeed(MachiningMethod.MILLING, "Steel-45", null))
                .isEqualByComparingTo(BigDecimal.valueOf(0.2));
        assertThat(props.getDepthOfCut(MachiningMethod.MILLING, BigDecimal.valueOf(5)))
                .isEqualByComparingTo(BigDecimal.valueOf(2));
        assertThat(props.getToolLife(MachiningMethod.MILLING, "Steel-45", null))
                .isEqualByComparingTo(BigDecimal.valueOf(60));
    }

    @Test
    void alSi10Mg_drilling() {
        assertThat(props.getCuttingSpeed(MachiningMethod.DRILLING, "AlSi10Mg", null))
                .isEqualByComparingTo(BigDecimal.valueOf(60));
        assertThat(props.getFeed(MachiningMethod.DRILLING, "AlSi10Mg", null))
                .isEqualByComparingTo(BigDecimal.valueOf(0.1));
        assertThat(props.getToolLife(MachiningMethod.DRILLING, "AlSi10Mg", null))
                .isEqualByComparingTo(BigDecimal.valueOf(60));
    }

    @Test
    void steel20Kh_substringMatch() {
        // "Steel 20Kh (ГОСТ)" содержит "steel 20kh" после нормализации
        assertThat(props.getCuttingSpeed(MachiningMethod.MILLING, "Steel 20Kh (ГОСТ)", null))
                .isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void unknownMaterial_fallbackToDefault() {
        assertThat(props.getCuttingSpeed(MachiningMethod.MILLING, "Unknown-Alloy", null))
                .isEqualByComparingTo(BigDecimal.valueOf(120));
        assertThat(props.getCuttingSpeed(MachiningMethod.DRILLING, null, null))
                .isEqualByComparingTo(BigDecimal.valueOf(25));
        // T без material — DEFAULT по методу (DRILLING = 45)
        assertThat(props.getToolLife(MachiningMethod.DRILLING, null, null))
                .isEqualByComparingTo(BigDecimal.valueOf(45));
    }

    @Test
    void depthOfCut_limitedByAllowance() {
        // t=2, allowance=0.8 -> min(2, 0.8) = 0.8
        assertThat(props.getDepthOfCut(MachiningMethod.MILLING, BigDecimal.valueOf(0.8)))
                .isEqualByComparingTo(BigDecimal.valueOf(0.8));
        // t=2, allowance=null -> 2
        assertThat(props.getDepthOfCut(MachiningMethod.MILLING, null))
                .isEqualByComparingTo(BigDecimal.valueOf(2));
    }

    @Test
    void cuttingData_override() {
        Map<String, Map<MachiningMethod, CuttingData>> custom = new HashMap<>();
        custom.put("custom-alloy", Map.of(
                MachiningMethod.MILLING,
                new CuttingData(BigDecimal.valueOf(999), BigDecimal.valueOf(0.5),
                        BigDecimal.valueOf(4), BigDecimal.valueOf(120))));
        props.setCuttingData(custom);

        assertThat(props.getCuttingSpeed(MachiningMethod.MILLING, "Custom-Alloy", null))
                .isEqualByComparingTo(BigDecimal.valueOf(999));
        // Старый материал ушёл — fallback на DEFAULT
        assertThat(props.getCuttingSpeed(MachiningMethod.MILLING, "Steel-45", null))
                .isEqualByComparingTo(BigDecimal.valueOf(120));
    }

    @Test
    void stainless304_grinding() {
        assertThat(props.getCuttingSpeed(MachiningMethod.GRINDING, "Stainless-304", null))
                .isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(props.getDepthOfCut(MachiningMethod.GRINDING, BigDecimal.valueOf(1)))
                .isEqualByComparingTo(BigDecimal.valueOf(0.05));
    }

    @Test
    void cuttingTool_doesNotAffectLookup() {
        // V/S/t/T берутся только из (material, method) — tool игнорируется
        CuttingTool tool = CuttingTool.builder()
                .type("END_MILL").diameter(BigDecimal.TEN)
                .material("Carbide").build();
        assertThat(props.getCuttingSpeed(MachiningMethod.MILLING, "AlSi10Mg", tool))
                .isEqualByComparingTo(BigDecimal.valueOf(300));
    }
}