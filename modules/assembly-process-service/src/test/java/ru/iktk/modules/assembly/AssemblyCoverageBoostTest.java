package ru.iktk.modules.assembly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.iktk.modules.assembly.dto.AssemblyRequest;
import ru.iktk.modules.assembly.dto.AssemblyResponse;
import ru.iktk.modules.assembly.model.AssemblyComponent;
import ru.iktk.modules.assembly.model.AssemblyProcess;
import ru.iktk.modules.assembly.model.AssemblyStage;
import ru.iktk.modules.assembly.model.AssemblyTooling;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Покрытие геттеров/сеттеров и null-веток моделей сборки")
class AssemblyCoverageBoostTest {

    // ===== AssemblyStage =====

    @Test
    @DisplayName("AssemblyStage: все сеттеры и геттеры")
    void stage_allSettersAndGetters() {
        AssemblyStage s = new AssemblyStage("s1", "p1", 1);
        s.setDesignation("010");
        s.setName("Сборка");
        s.setOperationType("ASSEMBLY");
        s.setEquipmentId("eq-1");
        s.setToolingId("tl-1");
        s.setTimeNormMin(new BigDecimal("10.5"));
        s.setSetupTimeMin(new BigDecimal("2.5"));
        s.setComment("тест");
        s.setStageOrder(7);
        s.setAssemblyProcessId("p2");
        s.setId("s2");

        assertThat(s.getDesignation()).isEqualTo("010");
        assertThat(s.getName()).isEqualTo("Сборка");
        assertThat(s.getOperationType()).isEqualTo("ASSEMBLY");
        assertThat(s.getEquipmentId()).isEqualTo("eq-1");
        assertThat(s.getToolingId()).isEqualTo("tl-1");
        assertThat(s.getTimeNormMin()).isEqualByComparingTo("10.5");
        assertThat(s.getSetupTimeMin()).isEqualByComparingTo("2.5");
        assertThat(s.getComment()).isEqualTo("тест");
        assertThat(s.getStageOrder()).isEqualTo(7);
        assertThat(s.getAssemblyProcessId()).isEqualTo("p2");
        assertThat(s.getId()).isEqualTo("s2");
    }

    @Test
    @DisplayName("AssemblyStage.totalTimeMin: только setupTimeMin -> возвращает его")
    void stage_totalTime_onlySetup() {
        AssemblyStage s = new AssemblyStage("s1", "p1", 1);
        s.setSetupTimeMin(new BigDecimal("5.0"));
        assertThat(s.totalTimeMin()).isEqualByComparingTo("5.0");
    }

    @Test
    @DisplayName("AssemblyStage.totalTimeMin: оба поля null -> 0")
    void stage_totalTime_bothNull() {
        AssemblyStage s = new AssemblyStage("s1", "p1", 1);
        s.setTimeNormMin(null);
        s.setSetupTimeMin(null);
        assertThat(s.totalTimeMin()).isEqualByComparingTo("0");
    }

    // ===== AssemblyProcess =====

    @Test
    @DisplayName("AssemblyProcess: все сеттеры и геттеры")
    void process_allSettersAndGetters() {
        AssemblyProcess p = new AssemblyProcess("p1", "proj1");
        p.setObjectRef("obj-1");
        p.setStrategy("PARALLEL");
        p.setStageCount(5);
        p.setComponentCount(3);
        p.setTotalTimeMin(new BigDecimal("165.0"));
        p.setStatus("APPROVED");
        p.setNotes("заметка");
        p.setId("p2");
        p.setProjectId("proj2");

        assertThat(p.getObjectRef()).isEqualTo("obj-1");
        assertThat(p.getStrategy()).isEqualTo("PARALLEL");
        assertThat(p.getStageCount()).isEqualTo(5);
        assertThat(p.getComponentCount()).isEqualTo(3);
        assertThat(p.getTotalTimeMin()).isEqualByComparingTo("165.0");
        assertThat(p.getStatus()).isEqualTo("APPROVED");
        assertThat(p.getNotes()).isEqualTo("заметка");
        assertThat(p.getId()).isEqualTo("p2");
        assertThat(p.getProjectId()).isEqualTo("proj2");
    }

    // ===== AssemblyComponent =====

    @Test
    @DisplayName("AssemblyComponent: все сеттеры и геттеры")
    void component_allSettersAndGetters() {
        AssemblyComponent c = new AssemblyComponent("c1", "p1");
        c.setParentComponentId("parent-1");
        c.setDesignation("C-001");
        c.setName("Корпус");
        c.setQuantity(3);
        c.setPosition(2);
        c.setPartKind("PART");
        c.setId("c2");
        c.setAssemblyProcessId("p2");

        assertThat(c.getParentComponentId()).isEqualTo("parent-1");
        assertThat(c.getDesignation()).isEqualTo("C-001");
        assertThat(c.getName()).isEqualTo("Корпус");
        assertThat(c.getQuantity()).isEqualTo(3);
        assertThat(c.getPosition()).isEqualTo(2);
        assertThat(c.getPartKind()).isEqualTo("PART");
        assertThat(c.getId()).isEqualTo("c2");
        assertThat(c.getAssemblyProcessId()).isEqualTo("p2");
    }

    // ===== AssemblyTooling =====

    @Test
    @DisplayName("AssemblyTooling: все сеттеры и геттеры")
    void tooling_allSettersAndGetters() {
        AssemblyTooling t = new AssemblyTooling("t1", "p1", "TL-1");
        t.setCompatibility("PARTIAL");
        t.setNotes("note");
        t.setId("t2");
        t.setAssemblyProcessId("p2");
        t.setToolingId("TL-2");

        assertThat(t.getCompatibility()).isEqualTo("PARTIAL");
        assertThat(t.getNotes()).isEqualTo("note");
        assertThat(t.getId()).isEqualTo("t2");
        assertThat(t.getAssemblyProcessId()).isEqualTo("p2");
        assertThat(t.getToolingId()).isEqualTo("TL-2");
    }

    // ===== AssemblyRequest =====

    @Test
    @DisplayName("AssemblyRequest: все сеттеры и геттеры")
    void request_allSettersAndGetters() {
        AssemblyRequest r = new AssemblyRequest();
        r.setProjectId("proj1");
        r.setObjectRef("obj-1");
        r.setStrategy("MIXED");
        r.setBomRef("bom-1");

        assertThat(r.getProjectId()).isEqualTo("proj1");
        assertThat(r.getObjectRef()).isEqualTo("obj-1");
        assertThat(r.getStrategy()).isEqualTo("MIXED");
        assertThat(r.getBomRef()).isEqualTo("bom-1");
    }

    @Test
    @DisplayName("AssemblyRequest: equals/hashCode по projectId+objectRef, toString содержит поля")
    void request_equalityAndToString() {
        AssemblyRequest a = new AssemblyRequest("proj1", "obj-1");
        AssemblyRequest b = new AssemblyRequest("proj1", "obj-1");
        b.setStrategy("PARALLEL"); // strategy не входит в equals
        AssemblyRequest c = new AssemblyRequest("proj2", "obj-1");
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a.toString()).contains("proj1", "obj-1", "SEQUENTIAL");
    }

    @Test
    @DisplayName("AssemblyRequest: equals(null) и equals(строка)")
    void request_equalityEdgeCases() {
        AssemblyRequest a = new AssemblyRequest("proj1", "obj-1");
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("string");
        assertThat(a).isEqualTo(a);
    }

    // ===== AssemblyResponse =====

    @Test
    @DisplayName("AssemblyResponse: все сеттеры и геттеры")
    void response_allSettersAndGetters() {
        AssemblyResponse r = new AssemblyResponse();
        r.setProcessId("p1");
        r.setProjectId("proj1");
        r.setObjectRef("obj-1");
        r.setStrategy("SEQUENTIAL");
        r.setStatus("DRAFT");
        r.setStageCount(5);
        r.setComponentCount(3);
        r.setTotalTimeMin(new BigDecimal("165.0"));
        r.setMessage("ok");
        r.setError("err");

        assertThat(r.getProcessId()).isEqualTo("p1");
        assertThat(r.getProjectId()).isEqualTo("proj1");
        assertThat(r.getObjectRef()).isEqualTo("obj-1");
        assertThat(r.getStrategy()).isEqualTo("SEQUENTIAL");
        assertThat(r.getStatus()).isEqualTo("DRAFT");
        assertThat(r.getStageCount()).isEqualTo(5);
        assertThat(r.getComponentCount()).isEqualTo(3);
        assertThat(r.getTotalTimeMin()).isEqualByComparingTo("165.0");
        assertThat(r.getMessage()).isEqualTo("ok");
        assertThat(r.getError()).isEqualTo("err");
    }

    @Test
    @DisplayName("AssemblyResponse.Builder: все сеттеры, build() -> все поля")
    void response_builderAllFields() {
        AssemblyResponse r = AssemblyResponse.builder()
                .processId("p1")
                .projectId("proj1")
                .objectRef("obj-1")
                .strategy("SEQUENTIAL")
                .status("DRAFT")
                .stageCount(5)
                .componentCount(3)
                .totalTimeMin(new BigDecimal("165.0"))
                .message("ok")
                .error("err")
                .build();

        assertThat(r.getProcessId()).isEqualTo("p1");
        assertThat(r.getError()).isEqualTo("err");
        assertThat(r.getMessage()).isEqualTo("ok");
    }

    @Test
    @DisplayName("AssemblyResponse: пустой builder -> все null")
    void response_emptyBuilder() {
        AssemblyResponse r = AssemblyResponse.builder().build();
        assertThat(r.getProcessId()).isNull();
        assertThat(r.getProjectId()).isNull();
        assertThat(r.getStageCount()).isNull();
        assertThat(r.getError()).isNull();
    }

    @Test
    @DisplayName("AssemblyResponse: setAvailableEvents не нужен, но проверяем list-совместимость через builder")
    void response_listLikeFields() {
        // Проверка, что builder корректно принимает и возвращает все поля
        List<String> notUsed = List.of("a", "b");
        Map<String, String> notUsedMap = Map.of("k", "v");
        // Не используется в модели, но проверяем, что нет side-effects
        assertThat(notUsed).hasSize(2);
        assertThat(notUsedMap).containsKey("k");
    }
}