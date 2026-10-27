package ru.iktk.modules.forging.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ForgingTransitionTest {

    @Test
    void builder_allFields() {
        ForgingTransition t = ForgingTransition.builder()
                .order(1)
                .name("Осадка")
                .type(ForgingTransition.TransitionType.UPSETTING)
                .inputVolumeMm3(BigDecimal.valueOf(1000))
                .temperatureC(BigDecimal.valueOf(1200))
                .deformationDegree(BigDecimal.valueOf(0.3))
                .build();

        assertThat(t.getOrder()).isEqualTo(1);
        assertThat(t.getName()).isEqualTo("Осадка");
        assertThat(t.getType()).isEqualTo(ForgingTransition.TransitionType.UPSETTING);
        assertThat(t.getInputVolumeMm3()).isEqualByComparingTo("1000");
        assertThat(t.getTemperatureC()).isEqualByComparingTo("1200");
        assertThat(t.getDeformationDegree()).isEqualByComparingTo("0.3");
    }

    @Test
    void noArgsConstructor_works() {
        ForgingTransition t = new ForgingTransition();
        assertThat(t.getOrder()).isZero();
        assertThat(t.getName()).isNull();
    }

    @Test
    void allArgsConstructor_works() {
        ForgingTransition t = new ForgingTransition(2, "Протяжка",
                ForgingTransition.TransitionType.DRAWING,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO);
        assertThat(t.getOrder()).isEqualTo(2);
        assertThat(t.getType()).isEqualTo(ForgingTransition.TransitionType.DRAWING);
    }

    @Test
    void transitionType_has6Values() {
        assertThat(ForgingTransition.TransitionType.values()).hasSize(6);
        assertThat(ForgingTransition.TransitionType.values()).containsExactly(
                ForgingTransition.TransitionType.UPSETTING,
                ForgingTransition.TransitionType.DRAWING,
                ForgingTransition.TransitionType.PIERCING,
                ForgingTransition.TransitionType.BLOCKING,
                ForgingTransition.TransitionType.FINISHING,
                ForgingTransition.TransitionType.TRIMMING
        );
    }
}