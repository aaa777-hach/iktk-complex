package ru.iktk.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Stage — этап ТП")
class StageTest {

    @Test
    @DisplayName("Конструктор ставит MAIN и timestamps")
    void constructor_setsDefaults() {
        Stage s = new Stage("s1", "010", "Токарная", Stage.OperationType.CASTING, 1);
        assertThat(s.getId()).isEqualTo("s1");
        assertThat(s.getDesignation()).isEqualTo("010");
        assertThat(s.getName()).isEqualTo("Токарная");
        assertThat(s.getOperationType()).isEqualTo(Stage.OperationType.CASTING);
        assertThat(s.getStageKind()).isEqualTo(Stage.StageKind.MAIN);
        assertThat(s.getOrder()).isEqualTo(1);
        assertThat(s.getCreatedAt()).isNotNull();
        assertThat(s.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("assignEquipment/assignTooling/assignCncProgram")
    void assignMethods() {
        Stage s = new Stage("s1", "010", "T", Stage.OperationType.CASTING, 1);
        s.assignEquipment("eq1");
        assertThat(s.getEquipmentId()).isEqualTo("eq1");
        assertThat(s.hasEquipment()).isTrue();

        s.assignTooling("t1");
        assertThat(s.getToolingId()).isEqualTo("t1");
        assertThat(s.hasTooling()).isTrue();

        s.assignCncProgram("cnc1");
        assertThat(s.getCncProgramId()).isEqualTo("cnc1");
    }

    @Test
    @DisplayName("hasEquipment/hasTooling: null/blank -> false")
    void hasEquipmentAndTooling_blankAndNull() {
        Stage s = new Stage("s1", "010", "T", Stage.OperationType.CASTING, 1);
        assertThat(s.hasEquipment()).isFalse();
        assertThat(s.hasTooling()).isFalse();

        s.assignEquipment("   ");
        assertThat(s.hasEquipment()).isFalse();

        s.assignTooling("");
        assertThat(s.hasTooling()).isFalse();
    }

    @Test
    @DisplayName("requiresCncProgram: MACHINING/CNC_MACHINING -> true, иначе false")
    void requiresCncProgram() {
        Stage cnc = new Stage("s1", "010", "T", Stage.OperationType.CNC_MACHINING, 1);
        assertThat(cnc.requiresCncProgram()).isTrue();

        Stage mach = new Stage("s2", "020", "T", Stage.OperationType.MACHINING, 1);
        assertThat(mach.requiresCncProgram()).isTrue();

        Stage cast = new Stage("s3", "030", "T", Stage.OperationType.CASTING, 1);
        assertThat(cast.requiresCncProgram()).isFalse();
    }

    @Test
    @DisplayName("totalTimeNorm: null-обработка и сложение")
    void totalTimeNorm() {
        Stage s = new Stage("s1", "010", "T", Stage.OperationType.CASTING, 1);
        assertThat(s.totalTimeNorm()).isEqualByComparingTo("0");

        s.setTimeNorm(new BigDecimal("3.0"), new BigDecimal("7.0"));
        assertThat(s.totalTimeNorm()).isEqualByComparingTo("10.0");

        s.setTimeNorm(null, new BigDecimal("5.0"));
        assertThat(s.totalTimeNorm()).isEqualByComparingTo("5.0");

        s.setTimeNorm(new BigDecimal("4.0"), null);
        assertThat(s.totalTimeNorm()).isEqualByComparingTo("4.0");

        s.setTimeNorm(null, null);
        assertThat(s.totalTimeNorm()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("touch при сеттерах обновляет updatedAt")
    void settersTouchUpdatedAt() throws InterruptedException {
        Stage s = new Stage("s1", "010", "T", Stage.OperationType.CASTING, 1);
        Instant before = s.getUpdatedAt();
        Thread.sleep(5);
        s.setName("Новое");
        assertThat(s.getUpdatedAt()).isAfter(before);
    }

    @Test
    @DisplayName("setId не трогает updatedAt")
    void setIdDoesNotTouch() throws InterruptedException {
        Stage s = new Stage("s1", "010", "T", Stage.OperationType.CASTING, 1);
        Instant before = s.getUpdatedAt();
        Thread.sleep(5);
        s.setId("s2");
        assertThat(s.getUpdatedAt()).isEqualTo(before);
    }

    @Test
    @DisplayName("equals/hashCode по id")
    void equalityById() {
        Stage a = new Stage("s1", "010", "A", Stage.OperationType.CASTING, 1);
        Stage b = new Stage("s1", "020", "B", Stage.OperationType.MACHINING, 2);
        Stage c = new Stage("s2", "010", "A", Stage.OperationType.CASTING, 1);
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("OperationType: 13 значений")
    void operationTypeCount() {
        assertThat(Stage.OperationType.values()).hasSize(13);
    }

    @Test
    @DisplayName("StageKind: 4 значения")
    void stageKindValues() {
        assertThat(Stage.StageKind.values()).containsExactly(
                Stage.StageKind.MAIN,
                Stage.StageKind.AUXILIARY,
                Stage.StageKind.CONTROL,
                Stage.StageKind.TRANSPORT);
    }
}
