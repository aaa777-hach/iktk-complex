package ru.iktk.equipment.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Equipment-модели — equals/hashCode, touch(), дефолты")
class EquipmentModelTest {

    // ===== EquipmentStatus =====

    @Test
    @DisplayName("EquipmentStatus: конструктор ставит AVAILABLE/0 и createdAt/updatedAt")
    void status_constructorDefaults() {
        EquipmentStatus s = new EquipmentStatus("s1", "eq1");
        assertThat(s.getState()).isEqualTo("AVAILABLE");
        assertThat(s.getLoadPercent()).isZero();
        assertThat(s.getCreatedAt()).isNotNull();
        assertThat(s.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("EquipmentStatus: touch() обновляет updatedAt, но не createdAt")
    void status_touchUpdatesUpdatedAt() throws InterruptedException {
        EquipmentStatus s = new EquipmentStatus("s1", "eq1");
        Instant created = s.getCreatedAt();
        Instant before = s.getUpdatedAt();
        Thread.sleep(5);
        s.setState("BUSY");
        assertThat(s.getUpdatedAt()).isAfter(before);
        assertThat(s.getCreatedAt()).isEqualTo(created);
    }

    @Test
    @DisplayName("EquipmentStatus: equals/hashCode по id")
    void status_equalityById() {
        EquipmentStatus a = new EquipmentStatus("s1", "eq1");
        EquipmentStatus b = new EquipmentStatus("s1", "eq2");
        EquipmentStatus c = new EquipmentStatus("s2", "eq1");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    // ===== EquipmentToolingId (composite key) =====

    @Test
    @DisplayName("EquipmentToolingId: equals/hashCode по обеим частям")
    void toolingId_equality() {
        EquipmentToolingId a = new EquipmentToolingId("eq1", "t1");
        EquipmentToolingId b = new EquipmentToolingId("eq1", "t1");
        EquipmentToolingId c = new EquipmentToolingId("eq1", "t2");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("EquipmentToolingId: сеттеры работают")
    void toolingId_setters() {
        EquipmentToolingId id = new EquipmentToolingId();
        id.setEquipmentId("eq1");
        id.setToolingId("t1");
        assertThat(id.getEquipmentId()).isEqualTo("eq1");
        assertThat(id.getToolingId()).isEqualTo("t1");
    }

    // ===== EquipmentTooling =====

    @Test
    @DisplayName("EquipmentTooling: конструктор ставит FULL и createdAt")
    void tooling_constructorDefaults() {
        EquipmentTooling t = new EquipmentTooling("eq1", "t1");
        assertThat(t.getId().getEquipmentId()).isEqualTo("eq1");
        assertThat(t.getId().getToolingId()).isEqualTo("t1");
        assertThat(t.getCompatibility()).isEqualTo("FULL");
        assertThat(t.getCreatedAt()).isNotNull();
    }

    // ===== EquipmentRequirement =====

    @Test
    @DisplayName("EquipmentRequirement: дефолты (quantity=1, priority=0, requiredCnc=false)")
    void requirement_defaults() {
        EquipmentRequirement r = new EquipmentRequirement("r1", "p1", "TURNING");
        assertThat(r.getProjectId()).isEqualTo("p1");
        assertThat(r.getOperationType()).isEqualTo("TURNING");
        assertThat(r.getQuantity()).isEqualTo(1);
        assertThat(r.getPriority()).isZero();
        assertThat(r.getRequiredCnc()).isFalse();
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("EquipmentRequirement: touch() обновляет updatedAt при setPriority")
    void requirement_touchOnSet() throws InterruptedException {
        EquipmentRequirement r = new EquipmentRequirement("r1", "p1", "TURNING");
        Instant before = r.getUpdatedAt();
        Thread.sleep(5);
        r.setPriority(5);
        assertThat(r.getUpdatedAt()).isAfter(before);
        assertThat(r.getPriority()).isEqualTo(5);
    }

    @Test
    @DisplayName("EquipmentRequirement: equals/hashCode по id")
    void requirement_equality() {
        EquipmentRequirement a = new EquipmentRequirement("r1", "p1", "TURNING");
        EquipmentRequirement b = new EquipmentRequirement("r1", "p2", "MILLING");
        EquipmentRequirement c = new EquipmentRequirement("r2", "p1", "TURNING");
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("EquipmentRequirement: сеттеры полей ТП")
    void requirement_setters() {
        EquipmentRequirement r = new EquipmentRequirement("r1", "p1", "TURNING");
        r.setProcessId("pr1");
        r.setStageId("st1");
        r.setMaterial("Steel");
        r.setRequiredCnc(true);
        r.setRequiredAxes(5);
        assertThat(r.getProcessId()).isEqualTo("pr1");
        assertThat(r.getStageId()).isEqualTo("st1");
        assertThat(r.getMaterial()).isEqualTo("Steel");
        assertThat(r.getRequiredCnc()).isTrue();
        assertThat(r.getRequiredAxes()).isEqualTo(5);
    }

    // ===== EquipmentGapAnalysis =====

    @Test
    @DisplayName("EquipmentGapAnalysis: дефолты STRICT/RUNNING, gaps пустой список")
    void gapAnalysis_defaults() {
        EquipmentGapAnalysis ga = new EquipmentGapAnalysis("g1", "p1");
        assertThat(ga.getProjectId()).isEqualTo("p1");
        assertThat(ga.getStrategy()).isEqualTo("STRICT");
        assertThat(ga.getStatus()).isEqualTo("RUNNING");
        assertThat(ga.getGapsCount()).isZero();
        assertThat(ga.getRecommendationsCount()).isZero();
        assertThat(ga.getGaps()).isNotNull().isEmpty();
        assertThat(ga.getStartedAt()).isNotNull();
        assertThat(ga.getCompletedAt()).isNull();
        assertThat(ga.getError()).isNull();
    }

    @Test
    @DisplayName("EquipmentGapAnalysis: setCompletedAt и setError через touch()")
    void gapAnalysis_complete() throws InterruptedException {
        EquipmentGapAnalysis ga = new EquipmentGapAnalysis("g1", "p1");
        Instant before = ga.getUpdatedAt();
        Thread.sleep(5);
        ga.setStatus("FAILED");
        ga.setError("boom");
        ga.setCompletedAt(Instant.now());
        assertThat(ga.getStatus()).isEqualTo("FAILED");
        assertThat(ga.getError()).isEqualTo("boom");
        assertThat(ga.getCompletedAt()).isNotNull();
        assertThat(ga.getUpdatedAt()).isAfter(before);
    }

    @Test
    @DisplayName("EquipmentGapAnalysis: equals/hashCode по id")
    void gapAnalysis_equality() {
        EquipmentGapAnalysis a = new EquipmentGapAnalysis("g1", "p1");
        EquipmentGapAnalysis b = new EquipmentGapAnalysis("g1", "p2");
        EquipmentGapAnalysis c = new EquipmentGapAnalysis("g2", "p1");
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    // ===== toString =====

    @Test
    @DisplayName("toString содержит ключевые поля")
    void toString_containsFields() {
        EquipmentStatus s = new EquipmentStatus("s1", "eq1");
        assertThat(s.toString()).contains("s1", "eq1", "AVAILABLE");

        EquipmentRequirement r = new EquipmentRequirement("r1", "p1", "TURNING");
        assertThat(r.toString()).contains("r1", "p1", "TURNING");

        EquipmentGapAnalysis ga = new EquipmentGapAnalysis("g1", "p1");
        assertThat(ga.toString()).contains("g1", "p1", "STRICT", "RUNNING");
    }
}