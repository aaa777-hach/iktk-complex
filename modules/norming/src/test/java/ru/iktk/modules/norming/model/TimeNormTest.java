package ru.iktk.modules.norming.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;

class TimeNormTest {
    @Test void builder_allFields() {
        TimeNorm n = TimeNorm.builder()
                .id("tn-1").projectId("p1").processId("proc-1")
                .source("CALCULATED").batchSize(BigDecimal.TEN)
                .totalTpz(BigDecimal.valueOf(20)).totalTsh(BigDecimal.valueOf(100))
                .totalTshT(BigDecimal.valueOf(50)).totalTshK(BigDecimal.valueOf(52)).build();
        assertThat(n.getId()).isEqualTo("tn-1");
        assertThat(n.getSource()).isEqualTo("CALCULATED");
        assertThat(n.getTotalTsh()).isEqualByComparingTo("100");
    }
    @Test void noArgsConstructor_defaultLists() {
        TimeNorm n = new TimeNorm();
        assertThat(n.getStageNorms()).isNotNull().isEmpty();
    }
    @Test void builderDefault_stageNorms() {
        TimeNorm n = TimeNorm.builder().build();
        assertThat(n.getStageNorms()).isNotNull().isInstanceOf(ArrayList.class);
    }
}