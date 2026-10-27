package ru.iktk.modules.forging.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForgingToolingTest {

    @Test
    void builder_allFields() {
        ForgingTooling t = ForgingTooling.builder()
                .items(List.of("Штамп", "Плита"))
                .dieCavities(List.of("Ручей 1", "Ручей 2"))
                .material("5ХНМ")
                .estimatedCost(BigDecimal.valueOf(150000))
                .build();

        assertThat(t.getItems()).hasSize(2).contains("Штамп", "Плита");
        assertThat(t.getDieCavities()).hasSize(2);
        assertThat(t.getMaterial()).isEqualTo("5ХНМ");
        assertThat(t.getEstimatedCost()).isEqualByComparingTo("150000");
    }

    @Test
    void builderDefault_itemsAndDieCavitiesNotEmpty() {
        ForgingTooling t = ForgingTooling.builder().build();
        assertThat(t.getItems()).isNotNull().isEmpty();
        assertThat(t.getDieCavities()).isNotNull().isEmpty();
    }

    @Test
    void noArgsConstructor_initializesLists() {
        ForgingTooling t = new ForgingTooling();
        assertThat(t.getItems()).isNotNull().isInstanceOf(ArrayList.class);
        assertThat(t.getDieCavities()).isNotNull().isInstanceOf(ArrayList.class);
    }
}