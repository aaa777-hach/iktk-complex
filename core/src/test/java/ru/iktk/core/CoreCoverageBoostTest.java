package ru.iktk.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.iktk.core.domain.Project;
import ru.iktk.core.domain.Stage;
import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.core.dto.OrchestrationRequest;
import ru.iktk.core.dto.OrchestrationResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Покрытие геттеров/сеттеров доменов и DTO")
class CoreCoverageBoostTest {

    @Test
    @DisplayName("Stage: все сеттеры и геттеры")
    void stage_allSettersAndGetters() {
        Stage s = new Stage("s1", "010", "T", Stage.OperationType.CASTING, 1);
        TechnologyProcess tp = new TechnologyProcess("tp1", "ТП",
                new Project("p1", "D", "N", Project.ObjectType.PART),
                "obj", Project.ObjectType.PART, TechnologyProcess.ProcessType.MACHINING);

        s.setDescription("desc");
        s.setTechnologyProcess(tp);
        s.setOrder(5);
        s.setOperationType(Stage.OperationType.MACHINING);
        s.setStageKind(Stage.StageKind.CONTROL);
        s.setWorkGrade(4);
        s.setEquipmentId("eq1");
        s.setToolingId("t1");
        s.setCncProgramId("cnc1");
        s.setTimeNormMin(new BigDecimal("1.5"));
        s.setSetupTimeMin(new BigDecimal("2.5"));
        s.setTariffCode("T1");
        s.setRequiredAccuracy("IT7");
        s.setMaxDimensions("100x100");
        s.setRequiredPowerKw(new BigDecimal("7.5"));
        s.setMaterial("Steel");
        s.setComment("c");

        assertThat(s.getDescription()).isEqualTo("desc");
        assertThat(s.getTechnologyProcess()).isSameAs(tp);
        assertThat(s.getOrder()).isEqualTo(5);
        assertThat(s.getOperationType()).isEqualTo(Stage.OperationType.MACHINING);
        assertThat(s.getStageKind()).isEqualTo(Stage.StageKind.CONTROL);
        assertThat(s.getWorkGrade()).isEqualTo(4);
        assertThat(s.getEquipmentId()).isEqualTo("eq1");
        assertThat(s.getToolingId()).isEqualTo("t1");
        assertThat(s.getCncProgramId()).isEqualTo("cnc1");
        assertThat(s.getTimeNormMin()).isEqualByComparingTo("1.5");
        assertThat(s.getSetupTimeMin()).isEqualByComparingTo("2.5");
        assertThat(s.getTariffCode()).isEqualTo("T1");
        assertThat(s.getRequiredAccuracy()).isEqualTo("IT7");
        assertThat(s.getMaxDimensions()).isEqualTo("100x100");
        assertThat(s.getRequiredPowerKw()).isEqualByComparingTo("7.5");
        assertThat(s.getMaterial()).isEqualTo("Steel");
        assertThat(s.getComment()).isEqualTo("c");
    }

    @Test
    @DisplayName("Project: все сеттеры и геттеры")
    void project_allSettersAndGetters() {
        Project p = new Project("p1", "D", "N", Project.ObjectType.PART);
        p.setDesignation("D2");
        p.setName("N2");
        p.setDescription("desc");
        p.setObjectType(Project.ObjectType.ASSEMBLY);
        p.setStatus(Project.ProjectStatus.ACTIVE);
        p.setEnterpriseCode("E1");
        p.setRequestedBy("u1");
        p.setResponsibleEngineer("eng1");

        assertThat(p.getDesignation()).isEqualTo("D2");
        assertThat(p.getName()).isEqualTo("N2");
        assertThat(p.getDescription()).isEqualTo("desc");
        assertThat(p.getObjectType()).isEqualTo(Project.ObjectType.ASSEMBLY);
        assertThat(p.getStatus()).isEqualTo(Project.ProjectStatus.ACTIVE);
        assertThat(p.getEnterpriseCode()).isEqualTo("E1");
        assertThat(p.getRequestedBy()).isEqualTo("u1");
        assertThat(p.getResponsibleEngineer()).isEqualTo("eng1");
    }

    @Test
    @DisplayName("TechnologyProcess: все сеттеры и геттеры")
    void technologyProcess_allSettersAndGetters() {
        Project project = new Project("p1", "D", "N", Project.ObjectType.PART);
        TechnologyProcess tp = new TechnologyProcess("tp1", "ТП", project,
                "obj1", Project.ObjectType.PART, TechnologyProcess.ProcessType.MACHINING);

        Project project2 = new Project("p2", "D2", "N2", Project.ObjectType.ASSEMBLY);
        tp.setDesignation("ТП2");
        tp.setName("Новое");
        tp.setVersion("v2");
        tp.setProject(project2);
        tp.setObjectRef("obj2");
        tp.setObjectType(Project.ObjectType.ASSEMBLY);
        tp.setProcessType(TechnologyProcess.ProcessType.ASSEMBLY);
        tp.setStatus(TechnologyProcess.ProcessStatus.APPROVED);
        tp.setProductionType(TechnologyProcess.ProductionType.SERIAL);
        tp.setAuthor("author");

        assertThat(tp.getDesignation()).isEqualTo("ТП2");
        assertThat(tp.getName()).isEqualTo("Новое");
        assertThat(tp.getVersion()).isEqualTo("v2");
        assertThat(tp.getProject()).isSameAs(project2);
        assertThat(tp.getObjectRef()).isEqualTo("obj2");
        assertThat(tp.getObjectType()).isEqualTo(Project.ObjectType.ASSEMBLY);
        assertThat(tp.getProcessType()).isEqualTo(TechnologyProcess.ProcessType.ASSEMBLY);
        assertThat(tp.getStatus()).isEqualTo(TechnologyProcess.ProcessStatus.APPROVED);
        assertThat(tp.getProductionType()).isEqualTo(TechnologyProcess.ProductionType.SERIAL);
        assertThat(tp.getAuthor()).isEqualTo("author");
    }

    @Test
    @DisplayName("OrchestrationResponse: все оставшиеся сеттеры и геттеры")
    void response_allSettersAndGetters() {
        OrchestrationResponse r = new OrchestrationResponse();
        Instant now = Instant.now();
        Map<String, Object> attrs = Map.of("k", "v");

        r.setDesignation("D");
        r.setObjectType(Project.ObjectType.ASSEMBLY);
        r.setStateDisplayName("Имя");
        r.setPointOfControl(true);
        r.setCurrentStageId("st1");
        r.setCurrentStageName("Этап 1");
        r.setProgressPercent(50);
        r.setCompletedStages(3);
        r.setTotalStages(10);
        r.setTechnologyProcessId("tp1");
        r.setRecommendationId("rec1");
        r.setMessage("msg");
        r.setUpdatedAt(now);
        r.setAttributes(attrs);

        assertThat(r.getDesignation()).isEqualTo("D");
        assertThat(r.getObjectType()).isEqualTo(Project.ObjectType.ASSEMBLY);
        assertThat(r.getStateDisplayName()).isEqualTo("Имя");
        assertThat(r.getPointOfControl()).isTrue();
        assertThat(r.getCurrentStageId()).isEqualTo("st1");
        assertThat(r.getCurrentStageName()).isEqualTo("Этап 1");
        assertThat(r.getProgressPercent()).isEqualTo(50);
        assertThat(r.getCompletedStages()).isEqualTo(3);
        assertThat(r.getTotalStages()).isEqualTo(10);
        assertThat(r.getTechnologyProcessId()).isEqualTo("tp1");
        assertThat(r.getRecommendationId()).isEqualTo("rec1");
        assertThat(r.getMessage()).isEqualTo("msg");
        assertThat(r.getUpdatedAt()).isEqualTo(now);
        assertThat(r.getAttributes()).isEqualTo(attrs);
    }

    @Test
    @DisplayName("OrchestrationRequest: оставшиеся сеттеры и геттеры")
    void request_allSettersAndGetters() {
        OrchestrationRequest r = new OrchestrationRequest();
        List<String> planned = List.of("CASTING");
        Map<String, Object> payload = Map.of("k", "v");

        r.setDescription("desc");
        r.setEnterpriseCode("E1");
        r.setResponsibleEngineer("eng1");
        r.setAssemblyId("asm1");
        r.setPlannedProcessTypes(planned);
        r.setPayload(payload);

        assertThat(r.getDescription()).isEqualTo("desc");
        assertThat(r.getEnterpriseCode()).isEqualTo("E1");
        assertThat(r.getResponsibleEngineer()).isEqualTo("eng1");
        assertThat(r.getAssemblyId()).isEqualTo("asm1");
        assertThat(r.getPlannedProcessTypes()).isEqualTo(planned);
        assertThat(r.getPayload()).isEqualTo(payload);
    }
}