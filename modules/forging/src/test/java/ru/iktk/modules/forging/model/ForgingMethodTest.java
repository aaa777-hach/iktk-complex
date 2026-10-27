package ru.iktk.modules.forging.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForgingMethodTest {

    @Test
    void values_contains5Methods() {
        assertThat(ForgingMethod.values()).hasSize(5);
        assertThat(ForgingMethod.values()).containsExactly(
                ForgingMethod.HOT_DIE_FORGING,
                ForgingMethod.COLD_DIE_FORGING,
                ForgingMethod.UPSETTING,
                ForgingMethod.EXTRUSION,
                ForgingMethod.SHEET_STAMPING
        );
    }

    @Test
    void valueOf_worksForAll() {
        for (ForgingMethod m : ForgingMethod.values()) {
            assertThat(ForgingMethod.valueOf(m.name())).isEqualTo(m);
        }
    }

    @Test
    void valueOf_unknown_throws() {
        assertThatThrownBy(() -> ForgingMethod.valueOf("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listOf_works() {
        List<ForgingMethod> list = Arrays.asList(ForgingMethod.values());
        assertThat(list).hasSize(5).contains(ForgingMethod.HOT_DIE_FORGING);
    }
}