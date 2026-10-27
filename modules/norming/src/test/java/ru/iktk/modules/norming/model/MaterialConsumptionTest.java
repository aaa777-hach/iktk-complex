package ru.iktk.modules.norming.model;

import org.junit.jupiter.api.Test;
import ru.iktk.core.domain.Stage;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class MaterialConsumptionTest {
    @Test void builder_allFields() {
        MaterialConsumption c = MaterialConsumption.builder()
                .stageId("st-1").stageDesignation("005").stageName("Точение")
                .operationType(Stage.OperationType.MACHINING)
                .materialCode("ST45").lossMass(BigDecimal.valueOf(0.5))
                .consumptionMass(BigDecimal.TEN).lossPercent(BigDecimal.valueOf(5)).build();
        assertThat(c.getMaterialCode()).isEqualTo("ST45");
        assertThat(c.getLossMass()).isEqualByComparingTo("0.5");
        assertThat(c.getLossPercent()).isEqualByComparingTo("5");
    }
    @Test void noArgsConstructor_nullFields() {
        MaterialConsumption c = new MaterialConsumption();
        assertThat(c.getStageId()).isNull();
    }
}