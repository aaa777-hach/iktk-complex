package ru.iktk.modules.norming.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class MaterialInfoTest {
    @Test void builder_allFields() {
        MaterialInfo m = MaterialInfo.builder()
                .code("ST45").name("Steel 45").grade("45")
                .density(BigDecimal.valueOf(7.85)).standard("GOST 1050")
                .costPerKg(BigDecimal.valueOf(80)).build();
        assertThat(m.getCode()).isEqualTo("ST45");
        assertThat(m.getName()).isEqualTo("Steel 45");
        assertThat(m.getDensity()).isEqualByComparingTo("7.85");
        assertThat(m.getCostPerKg()).isEqualByComparingTo("80");
    }
    @Test void setters_work() {
        MaterialInfo m = new MaterialInfo();
        m.setCode("X"); m.setDensity(BigDecimal.ONE);
        assertThat(m.getCode()).isEqualTo("X");
        assertThat(m.getDensity()).isEqualByComparingTo("1");
    }
}