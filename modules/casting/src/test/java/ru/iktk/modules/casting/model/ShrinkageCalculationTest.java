package ru.iktk.modules.casting.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ShrinkageCalculationTest {

    @Test
    void builder_allFields() {
        ShrinkageCalculation s = ShrinkageCalculation.builder()
                .shrinkagePercent(BigDecimal.valueOf(2.5))
                .linearAllowanceMm(BigDecimal.valueOf(1.2))
                .material("Steel-45")
                .method(CastingMethod.SAND_CASTING)
                .build();

        assertThat(s.getShrinkagePercent()).isEqualByComparingTo("2.5");
        assertThat(s.getLinearAllowanceMm()).isEqualByComparingTo("1.2");
        assertThat(s.getMaterial()).isEqualTo("Steel-45");
        assertThat(s.getMethod()).isEqualTo(CastingMethod.SAND_CASTING);
    }

    @Test
    void noArgsConstructor_allNull() {
        ShrinkageCalculation s = new ShrinkageCalculation();
        assertThat(s.getShrinkagePercent()).isNull();
        assertThat(s.getMaterial()).isNull();
        assertThat(s.getMethod()).isNull();
    }

    @Test
    void setters_work() {
        ShrinkageCalculation s = new ShrinkageCalculation();
        s.setShrinkagePercent(BigDecimal.ONE);
        s.setLinearAllowanceMm(BigDecimal.TEN);
        s.setMaterial("AlSi10Mg");
        s.setMethod(CastingMethod.DIE_CASTING);

        assertThat(s.getShrinkagePercent()).isEqualByComparingTo("1");
        assertThat(s.getLinearAllowanceMm()).isEqualByComparingTo("10");
        assertThat(s.getMaterial()).isEqualTo("AlSi10Mg");
        assertThat(s.getMethod()).isEqualTo(CastingMethod.DIE_CASTING);
    }
}