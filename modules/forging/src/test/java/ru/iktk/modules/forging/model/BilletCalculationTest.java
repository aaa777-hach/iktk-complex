package ru.iktk.modules.forging.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BilletCalculationTest {

    @Test
    void builder_allFields() {
        BilletCalculation b = BilletCalculation.builder()
                .partVolumeMm3(BigDecimal.valueOf(1000))
                .billetVolumeMm3(BigDecimal.valueOf(1200))
                .billetMassKg(BigDecimal.valueOf(9.42))
                .diameterMm(BigDecimal.valueOf(50))
                .lengthMm(BigDecimal.valueOf(100))
                .materialUtilization(BigDecimal.valueOf(0.83))
                .method(ForgingMethod.HOT_DIE_FORGING)
                .build();

        assertThat(b.getPartVolumeMm3()).isEqualByComparingTo("1000");
        assertThat(b.getBilletVolumeMm3()).isEqualByComparingTo("1200");
        assertThat(b.getBilletMassKg()).isEqualByComparingTo("9.42");
        assertThat(b.getDiameterMm()).isEqualByComparingTo("50");
        assertThat(b.getLengthMm()).isEqualByComparingTo("100");
        assertThat(b.getMaterialUtilization()).isEqualByComparingTo("0.83");
        assertThat(b.getMethod()).isEqualTo(ForgingMethod.HOT_DIE_FORGING);
    }

    @Test
    void noArgsConstructor_allNull() {
        BilletCalculation b = new BilletCalculation();
        assertThat(b.getPartVolumeMm3()).isNull();
        assertThat(b.getMethod()).isNull();
    }
}