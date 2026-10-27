package ru.iktk.modules.norming.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeNormSourceTest {
    @Test void values_5() {
        assertThat(TimeNormSource.values()).hasSize(5);
        assertThat(TimeNormSource.values()).containsExactly(
                TimeNormSource.GENERAL_MACHINE_BUILDING, TimeNormSource.INDUSTRY,
                TimeNormSource.PLANT, TimeNormSource.CALCULATED, TimeNormSource.ACTUAL);
    }
    @Test void valueOf_works() {
        for (TimeNormSource s : TimeNormSource.values())
            assertThat(TimeNormSource.valueOf(s.name())).isEqualTo(s);
    }
    @Test void valueOf_unknown_throws() {
        assertThatThrownBy(() -> TimeNormSource.valueOf("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void listOf_works() {
        assertThat(java.util.Arrays.asList(TimeNormSource.values())).contains(TimeNormSource.CALCULATED);
    }
}