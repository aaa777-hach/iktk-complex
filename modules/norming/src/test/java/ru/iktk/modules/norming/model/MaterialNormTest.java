package ru.iktk.modules.norming.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;

class MaterialNormTest {
    @Test void builder_allFields() {
        MaterialNorm n = MaterialNorm.builder()
                .id("mn-1").projectId("p1").processId("proc-1")
                .blankMass(BigDecimal.valueOf(10)).partMass(BigDecimal.valueOf(8))
                .normPerPart(BigDecimal.valueOf(10.2)).normPerBatch(BigDecimal.valueOf(102))
                .batchSize(BigDecimal.valueOf(10))
                .materialUtilization(BigDecimal.valueOf(0.8))
                .techLossCoefficient(BigDecimal.valueOf(0.02))
                .defectCoefficient(BigDecimal.valueOf(0.01)).build();
        assertThat(n.getId()).isEqualTo("mn-1");
        assertThat(n.getBlankMass()).isEqualByComparingTo("10");
        assertThat(n.getMaterialUtilization()).isEqualByComparingTo("0.8");
    }
    @Test void noArgsConstructor_defaultLists() {
        MaterialNorm n = new MaterialNorm();
        assertThat(n.getConsumptions()).isNotNull().isEmpty();
        assertThat(n.getToolConsumptions()).isNotNull().isEmpty();
    }
    @Test void builderDefault_lists() {
        MaterialNorm n = MaterialNorm.builder().build();
        assertThat(n.getConsumptions()).isNotNull().isInstanceOf(ArrayList.class);
        assertThat(n.getToolConsumptions()).isNotNull().isInstanceOf(ArrayList.class);
    }
}