package ru.iktk.modules.norming.model;

import org.junit.jupiter.api.Test;
import ru.iktk.core.domain.Stage;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class StageTimeNormTest {
    @Test void builder_allFields() {
        StageTimeNorm s = StageTimeNorm.builder()
                .stageId("st-1").stageDesignation("005").stageName("Точение")
                .operationType(Stage.OperationType.MACHINING)
                .to(BigDecimal.TEN).tv(BigDecimal.ONE).top(BigDecimal.valueOf(11))
                .tsh(BigDecimal.valueOf(12)).tshK(BigDecimal.valueOf(15))
                .tpz(BigDecimal.valueOf(20)).source(TimeNormSource.CALCULATED).build();
        assertThat(s.getStageId()).isEqualTo("st-1");
        assertThat(s.getOperationType()).isEqualTo(Stage.OperationType.MACHINING);
        assertThat(s.getTsh()).isEqualByComparingTo("12");
    }
    @Test void noArgsConstructor_nullFields() {
        StageTimeNorm s = new StageTimeNorm();
        assertThat(s.getStageId()).isNull();
        assertThat(s.getSource()).isNull();
    }
}