package ru.iktk.modules.casting.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GatingSystemTest {

    @Test
    void builder_allFields() {
        GatingSystem g = GatingSystem.builder()
                .sprueDiameterMm(BigDecimal.valueOf(20))
                .runnerAreaMm2(BigDecimal.valueOf(100))
                .gateAreaMm2(BigDecimal.valueOf(50))
                .gatesCount(2)
                .risersCount(3)
                .pouringTimeSec(BigDecimal.valueOf(10))
                .metalVolumeMm3(BigDecimal.valueOf(1500))
                .build();

        assertThat(g.getSprueDiameterMm()).isEqualByComparingTo("20");
        assertThat(g.getRunnerAreaMm2()).isEqualByComparingTo("100");
        assertThat(g.getGateAreaMm2()).isEqualByComparingTo("50");
        assertThat(g.getGatesCount()).isEqualTo(2);
        assertThat(g.getRisersCount()).isEqualTo(3);
        assertThat(g.getPouringTimeSec()).isEqualByComparingTo("10");
        assertThat(g.getMetalVolumeMm3()).isEqualByComparingTo("1500");
    }

    @Test
    void noArgsConstructor_zeroDefaults() {
        GatingSystem g = new GatingSystem();
        assertThat(g.getSprueDiameterMm()).isNull();
        assertThat(g.getGatesCount()).isZero();
        assertThat(g.getRisersCount()).isZero();
    }

    @Test
    void setters_work() {
        GatingSystem g = new GatingSystem();
        g.setSprueDiameterMm(BigDecimal.valueOf(15));
        g.setGatesCount(4);
        assertThat(g.getSprueDiameterMm()).isEqualByComparingTo("15");
        assertThat(g.getGatesCount()).isEqualTo(4);
    }
}