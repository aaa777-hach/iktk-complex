package ru.iktk.modules.casting.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CastingMethodTest {

    @Test
    void values_contains5Methods() {
        assertThat(CastingMethod.values()).hasSize(5);
        assertThat(CastingMethod.values()).containsExactly(
                CastingMethod.SAND_CASTING,
                CastingMethod.DIE_CASTING,
                CastingMethod.INVESTMENT_CASTING,
                CastingMethod.CENTRIFUGAL_CASTING,
                CastingMethod.GRAVITY_CASTING
        );
    }

    @Test
    void valueOf_worksForAll() {
        for (CastingMethod m : CastingMethod.values()) {
            assertThat(CastingMethod.valueOf(m.name())).isEqualTo(m);
        }
    }

    @Test
    void valueOf_unknown_throws() {
        assertThatThrownBy(() -> CastingMethod.valueOf("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listOf_works() {
        List<CastingMethod> list = Arrays.asList(CastingMethod.values());
        assertThat(list).hasSize(5).contains(CastingMethod.SAND_CASTING);
    }
}