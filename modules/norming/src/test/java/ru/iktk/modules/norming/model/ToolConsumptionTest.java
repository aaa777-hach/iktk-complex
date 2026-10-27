package ru.iktk.modules.norming.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class ToolConsumptionTest {
    @Test void builder_allFields() {
        ToolConsumption t = ToolConsumption.builder()
                .stageId("st-1").stageName("Точение").toolType("cutter").toolMaterial("HSS")
                .machineTimeMin(BigDecimal.TEN).toolLifeMin(BigDecimal.valueOf(60))
                .regrindCount(3).toolsPerPart(BigDecimal.valueOf(0.1))
                .toolsPerBatch(BigDecimal.ONE).build();
        assertThat(t.getToolType()).isEqualTo("cutter");
        assertThat(t.getToolLifeMin()).isEqualByComparingTo("60");
        assertThat(t.getRegrindCount()).isEqualTo(3);
    }
    @Test void noArgsConstructor_nullFields() {
        ToolConsumption t = new ToolConsumption();
        assertThat(t.getStageId()).isNull();
        assertThat(t.getRegrindCount()).isNull();
    }
}