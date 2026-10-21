package ru.iktk.modules.assembly.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Assembly-модели — конструкторы, touch(), totalTimeMin, equals/hashCode")
class AssemblyModelTest {

    // ===== AssemblyProcess =====

    @Test
    @DisplayName("AssemblyProcess: конструктор ставит DRAFT/SEQUENTIAL/0 и timestamps")
    void process_constructorDefaults() {
        AssemblyProcess p = new AssemblyProcess("p1", "proj1");
        assertThat(p.getId()).isEqualTo("p1");
        assertThat(p.getProjectId()).isEqualTo("proj1");
        assertThat(p.getStrategy()).isEqualTo("SEQUENTIAL");
        assertThat(p.getStatus()).isEqualTo("DRAFT");
        assertThat(p.getStageCount()).isZero();
        assertThat(p.getComponentCount()).isZero();
        assertThat(p.getCreatedAt()).isNotNull();
        assertThat(p.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("AssemblyProcess: touch() через сеттер обновляет updatedAt")
    void process_touchOnSetter() throws InterruptedException {
        AssemblyProcess p = new AssemblyProcess("p1", "proj1");
        Instant before = p.getUpdatedAt();
        Thread.sleep(5);
        p.setNotes("Заметка");
        assertThat(p.getUpdatedAt()).isAfter(before);
        assertThat(p.getNotes()).isEqualTo("Заметка");
    }

    @Test
    @DisplayName("AssemblyProcess: approve/reject/archive меняют статус")
    void process_statusTransitions() {
        AssemblyProcess p = new AssemblyProcess("p1", "proj1");
        p.approve();
        assertThat(p.getStatus()).isEqualTo("APPROVED");
        p.reject();
        assertThat(p.getStatus()).isEqualTo("REJECTED");
        p.archive();
        assertThat(p.getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("AssemblyProcess: equals/hashCode по id, toString содержит поля")
    void process_equalityAndToString() {
        AssemblyProcess a = new AssemblyProcess("p1", "proj1");
        AssemblyProcess b = new AssemblyProcess("p1", "proj2");
        AssemblyProcess c = new AssemblyProcess("p2", "proj1");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.toString()).contains("p1", "proj1", "SEQUENTIAL", "DRAFT");
    }

    // ===== AssemblyStage =====

    @Test
    @DisplayName("AssemblyStage: конструктор ставит stageOrder и createdAt")
    void stage_constructorDefaults() {
        AssemblyStage s = new AssemblyStage("s1", "p1", 3);
        assertThat(s.getId()).isEqualTo("s1");
        assertThat(s.getAssemblyProcessId()).isEqualTo("p1");
        assertThat(s.getStageOrder()).isEqualTo(3);
        assertThat(s.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("AssemblyStage: totalTimeMin при null полях -> 0")
    void stage_totalTimeMin_nulls() {
        AssemblyStage s = new AssemblyStage("s1", "p1", 1);
        assertThat(s.totalTimeMin()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("AssemblyStage: totalTimeMin складывает timeNormMin + setupTimeMin")
    void stage_totalTimeMin_sum() {
        AssemblyStage s = new AssemblyStage("s1", "p1", 1);
        s.setTimeNormMin(new BigDecimal("10.5"));
        s.setSetupTimeMin(new BigDecimal("2.5"));
        assertThat(s.totalTimeMin()).isEqualByComparingTo("13.0");
    }

    @Test
    @DisplayName("AssemblyStage: totalTimeMin только с одним полем")
    void stage_totalTimeMin_oneField() {
        AssemblyStage s = new AssemblyStage("s1", "p1", 1);
        s.setTimeNormMin(new BigDecimal("7.0"));
        assertThat(s.totalTimeMin()).isEqualByComparingTo("7.0");
        s.setTimeNormMin(null);
        s.setSetupTimeMin(new BigDecimal("3.0"));
        assertThat(s.totalTimeMin()).isEqualByComparingTo("3.0");
    }

    @Test
    @DisplayName("AssemblyStage: equals/hashCode по id, toString содержит поля")
    void stage_equalityAndToString() {
        AssemblyStage a = new AssemblyStage("s1", "p1", 1);
        a.setName("Сборка");
        a.setOperationType("ASSEMBLY");
        AssemblyStage b = new AssemblyStage("s1", "p2", 2);
        AssemblyStage c = new AssemblyStage("s2", "p1", 1);
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.toString()).contains("s1", "p1", "Сборка", "ASSEMBLY", "stageOrder=1");
    }

    // ===== AssemblyComponent =====

    @Test
    @DisplayName("AssemblyComponent: конструктор ставит quantity=1 и createdAt")
    void component_constructorDefaults() {
        AssemblyComponent c = new AssemblyComponent("c1", "p1");
        assertThat(c.getId()).isEqualTo("c1");
        assertThat(c.getAssemblyProcessId()).isEqualTo("p1");
        assertThat(c.getQuantity()).isEqualTo(1);
        assertThat(c.getCreatedAt()).isNotNull();
        assertThat(c.getParentComponentId()).isNull();
    }

    @Test
    @DisplayName("AssemblyComponent: сеттеры меняют поля")
    void component_setters() {
        AssemblyComponent c = new AssemblyComponent("c1", "p1");
        c.setDesignation("C-001");
        c.setName("Корпус");
        c.setQuantity(3);
        c.setPosition(2);
        c.setPartKind("PART");
        c.setParentComponentId("c0");
        assertThat(c.getDesignation()).isEqualTo("C-001");
        assertThat(c.getName()).isEqualTo("Корпус");
        assertThat(c.getQuantity()).isEqualTo(3);
        assertThat(c.getPosition()).isEqualTo(2);
        assertThat(c.getPartKind()).isEqualTo("PART");
        assertThat(c.getParentComponentId()).isEqualTo("c0");
    }

    @Test
    @DisplayName("AssemblyComponent: equals/hashCode по id")
    void component_equality() {
        AssemblyComponent a = new AssemblyComponent("c1", "p1");
        AssemblyComponent b = new AssemblyComponent("c1", "p2");
        AssemblyComponent c = new AssemblyComponent("c2", "p1");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.toString()).contains("c1", "p1");
    }

    // ===== AssemblyTooling =====

    @Test
    @DisplayName("AssemblyTooling: конструктор ставит compatibility=FULL и createdAt")
    void tooling_constructorDefaults() {
        AssemblyTooling t = new AssemblyTooling("t1", "p1", "TL-1");
        assertThat(t.getId()).isEqualTo("t1");
        assertThat(t.getAssemblyProcessId()).isEqualTo("p1");
        assertThat(t.getToolingId()).isEqualTo("TL-1");
        assertThat(t.getCompatibility()).isEqualTo("FULL");
        assertThat(t.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("AssemblyTooling: сеттеры меняют compatibility и notes")
    void tooling_setters() {
        AssemblyTooling t = new AssemblyTooling("t1", "p1", "TL-1");
        t.setCompatibility("PARTIAL");
        t.setNotes("заметка");
        assertThat(t.getCompatibility()).isEqualTo("PARTIAL");
        assertThat(t.getNotes()).isEqualTo("заметка");
    }

    @Test
    @DisplayName("AssemblyTooling: equals/hashCode по id, toString содержит поля")
    void tooling_equalityAndToString() {
        AssemblyTooling a = new AssemblyTooling("t1", "p1", "TL-1");
        AssemblyTooling b = new AssemblyTooling("t1", "p2", "TL-2");
        AssemblyTooling c = new AssemblyTooling("t2", "p1", "TL-1");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.toString()).contains("t1", "p1", "TL-1", "FULL");
    }
}