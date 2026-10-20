package ru.iktk.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TechnologyProcess — технологический процесс")
class TechnologyProcessTest {

    private Project sampleProject() {
        return new Project("proj1", "КР-001", "Корпус", Project.ObjectType.ASSEMBLY);
    }

    @Test
    @DisplayName("Конструктор ставит DRAFT и пустой список stages")
    void constructor_setsDefaults() {
        Project project = sampleProject();
        TechnologyProcess tp = new TechnologyProcess("tp1", "ТП-001",
                project, "obj-1", Project.ObjectType.ASSEMBLY,
                TechnologyProcess.ProcessType.ASSEMBLY);

        assertThat(tp.getId()).isEqualTo("tp1");
        assertThat(tp.getDesignation()).isEqualTo("ТП-001");
        assertThat(tp.getProject()).isSameAs(project);
        assertThat(tp.getObjectRef()).isEqualTo("obj-1");
        assertThat(tp.getObjectType()).isEqualTo(Project.ObjectType.ASSEMBLY);
        assertThat(tp.getProcessType()).isEqualTo(TechnologyProcess.ProcessType.ASSEMBLY);
        assertThat(tp.getStatus()).isEqualTo(TechnologyProcess.ProcessStatus.DRAFT);
        assertThat(tp.getStages()).isEmpty();
        assertThat(tp.stageCount()).isZero();
        assertThat(tp.getCreatedAt()).isNotNull();
        assertThat(tp.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("addStage добавляет и связывает stage ↔ tp")
    void addStage() {
        TechnologyProcess tp = new TechnologyProcess("tp1", "ТП", sampleProject(),
                "obj", Project.ObjectType.PART, TechnologyProcess.ProcessType.MACHINING);
        Stage s = new Stage("s1", "010", "T", Stage.OperationType.MACHINING, 1);

        tp.addStage(s);
        assertThat(tp.stageCount()).isEqualTo(1);
        assertThat(tp.getStages()).contains(s);
        assertThat(s.getTechnologyProcess()).isSameAs(tp);
    }

    @Test
    @DisplayName("removeStage удаляет и обнуляет связь")
    void removeStage() {
        TechnologyProcess tp = new TechnologyProcess("tp1", "ТП", sampleProject(),
                "obj", Project.ObjectType.PART, TechnologyProcess.ProcessType.MACHINING);
        Stage s = new Stage("s1", "010", "T", Stage.OperationType.MACHINING, 1);
        tp.addStage(s);

        tp.removeStage(s);
        assertThat(tp.stageCount()).isZero();
        assertThat(s.getTechnologyProcess()).isNull();
    }

    @Test
    @DisplayName("insertStage вставляет по индексу")
    void insertStage() {
        TechnologyProcess tp = new TechnologyProcess("tp1", "ТП", sampleProject(),
                "obj", Project.ObjectType.PART, TechnologyProcess.ProcessType.MACHINING);
        Stage s1 = new Stage("s1", "010", "A", Stage.OperationType.MACHINING, 1);
        Stage s2 = new Stage("s2", "020", "B", Stage.OperationType.MACHINING, 2);
        Stage sMid = new Stage("s3", "015", "M", Stage.OperationType.MACHINING, 1);

        tp.addStage(s1);
        tp.addStage(s2);
        tp.insertStage(1, sMid);

        assertThat(tp.stageCount()).isEqualTo(3);
        assertThat(tp.getStages().get(1)).isSameAs(sMid);
        assertThat(sMid.getTechnologyProcess()).isSameAs(tp);
    }

    @Test
    @DisplayName("approve/reject/archive меняют статус")
    void statusTransitions() {
        TechnologyProcess tp = new TechnologyProcess("tp1", "ТП", sampleProject(),
                "obj", Project.ObjectType.PART, TechnologyProcess.ProcessType.MACHINING);

        tp.approve();
        assertThat(tp.getStatus()).isEqualTo(TechnologyProcess.ProcessStatus.APPROVED);

        tp.reject();
        assertThat(tp.getStatus()).isEqualTo(TechnologyProcess.ProcessStatus.REJECTED);

        tp.archive();
        assertThat(tp.getStatus()).isEqualTo(TechnologyProcess.ProcessStatus.ARCHIVED);
    }

    @Test
    @DisplayName("isAssemblyProcess/isMachiningProcess")
    void processTypeHelpers() {
        TechnologyProcess asm = new TechnologyProcess("tp1", "ТП", sampleProject(),
                "obj", Project.ObjectType.ASSEMBLY, TechnologyProcess.ProcessType.ASSEMBLY);
        assertThat(asm.isAssemblyProcess()).isTrue();
        assertThat(asm.isMachiningProcess()).isFalse();

        TechnologyProcess mach = new TechnologyProcess("tp2", "ТП", sampleProject(),
                "obj", Project.ObjectType.PART, TechnologyProcess.ProcessType.MACHINING);
        assertThat(mach.isMachiningProcess()).isTrue();
        assertThat(mach.isAssemblyProcess()).isFalse();
    }

    @Test
    @DisplayName("touch при сеттерах обновляет updatedAt")
    void settersTouchUpdatedAt() throws InterruptedException {
        TechnologyProcess tp = new TechnologyProcess("tp1", "ТП", sampleProject(),
                "obj", Project.ObjectType.PART, TechnologyProcess.ProcessType.MACHINING);
        Instant before = tp.getUpdatedAt();
        Thread.sleep(5);
        tp.setName("Новое имя");
        assertThat(tp.getUpdatedAt()).isAfter(before);
    }

    @Test
    @DisplayName("equals/hashCode по id")
    void equalityById() {
        TechnologyProcess a = new TechnologyProcess("tp1", "ТП1", sampleProject(),
                "obj1", Project.ObjectType.PART, TechnologyProcess.ProcessType.MACHINING);
        TechnologyProcess b = new TechnologyProcess("tp1", "ТП2", sampleProject(),
                "obj2", Project.ObjectType.ASSEMBLY, TechnologyProcess.ProcessType.ASSEMBLY);
        TechnologyProcess c = new TechnologyProcess("tp2", "ТП1", sampleProject(),
                "obj1", Project.ObjectType.PART, TechnologyProcess.ProcessType.MACHINING);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("toString содержит id, designation, processType, objectType, status, stages.size")
    void toString_containsFields() {
        TechnologyProcess tp = new TechnologyProcess("tp1", "ТП-001", sampleProject(),
                "obj", Project.ObjectType.PART, TechnologyProcess.ProcessType.MACHINING);
        assertThat(tp.toString())
                .contains("tp1", "ТП-001", "MACHINING", "PART", "DRAFT", "stages=0");
    }

    @Test
    @DisplayName("ProcessType: 9 значений; ProcessStatus: 4; ProductionType: 3")
    void enumValues() {
        assertThat(TechnologyProcess.ProcessType.values()).hasSize(9);
        assertThat(TechnologyProcess.ProcessStatus.values()).containsExactly(
                TechnologyProcess.ProcessStatus.DRAFT,
                TechnologyProcess.ProcessStatus.APPROVED,
                TechnologyProcess.ProcessStatus.REJECTED,
                TechnologyProcess.ProcessStatus.ARCHIVED);
        assertThat(TechnologyProcess.ProductionType.values()).containsExactly(
                TechnologyProcess.ProductionType.SINGLE,
                TechnologyProcess.ProductionType.SERIAL,
                TechnologyProcess.ProductionType.MASS);
    }

    @Test
    @DisplayName("setStages перезаписывает список")
    void setStages() {
        TechnologyProcess tp = new TechnologyProcess("tp1", "ТП", sampleProject(),
                "obj", Project.ObjectType.PART, TechnologyProcess.ProcessType.MACHINING);
        List<Stage> stages = new ArrayList<>();
        stages.add(new Stage("s1", "010", "A", Stage.OperationType.MACHINING, 1));

        tp.setStages(stages);
        assertThat(tp.getStages()).isEqualTo(stages);
        assertThat(tp.stageCount()).isEqualTo(1);
    }
}
