package ru.iktk.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import ru.iktk.orchestrator.messaging.ModuleCommandProducer;
import ru.iktk.orchestrator.model.ProjectRuntime;
import ru.iktk.orchestrator.repository.ProjectStateRepository;
import ru.iktk.orchestrator.service.OrchestrationService;
import ru.iktk.orchestrator.statemachine.ProjectEvent;
import ru.iktk.orchestrator.statemachine.ProjectState;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Сквозной сценарий сборки: маршрутизация и переходы statemachine")
class AssemblyCycleIntegrationTest {

    @Autowired private OrchestrationService orchestrationService;
    @Autowired private ProjectStateRepository projectStateRepository;
    @MockBean private ModuleCommandProducer moduleCommandProducer;

    private String createRuntime(ProjectState state) {
        String projectId = UUID.randomUUID().toString();
        ProjectRuntime r = ProjectRuntime.builder()
                .projectId(projectId)
                .projectName("Тестовый сборочный проект")
                .requestedBy("integration-test")
                .state(state)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        projectStateRepository.save(r);
        return projectId;
    }

    @Test
    @DisplayName("ASSEMBLY_ANALYSIS: run() маршрутизирует в bom-service")
    void assemblyAnalysis_routedToBomService() {
        String projectId = createRuntime(ProjectState.ASSEMBLY_ANALYSIS);

        orchestrationService.run(projectId);

        verify(moduleCommandProducer).send(
                eq(projectId), eq("bom-service"), eq(ProjectState.ASSEMBLY_ANALYSIS), nullable(Map.class));
    }

    @Test
    @DisplayName("ASSEMBLY_PROCESS_PLANNING: run() маршрутизирует в assembly-process-service")
    void assemblyProcessPlanning_routedToModule() {
        String projectId = createRuntime(ProjectState.ASSEMBLY_PROCESS_PLANNING);

        orchestrationService.run(projectId);

        verify(moduleCommandProducer).send(
                eq(projectId), eq("assembly-process-service"),
                eq(ProjectState.ASSEMBLY_PROCESS_PLANNING), nullable(Map.class));
    }

    @Test
    @DisplayName("ASSEMBLY_PROCESS_READY от модуля -> каскад до TECHNICAL_CONTROL")
    void assemblyProcessReady_cascadesToTechnicalControl() {
        String projectId = createRuntime(ProjectState.ASSEMBLY_PROCESS_PLANNING);

        orchestrationService.onModuleResult(
                projectId, ProjectEvent.ASSEMBLY_PROCESS_READY, Map.of(), true);

        assertThat(orchestrationService.getState(projectId))
                .isEqualTo(ProjectState.TECHNICAL_CONTROL);
    }

    @Test
    @DisplayName("ASSEMBLY_ANALYSIS + BOM_EXTRACTED -> PART_PROCESS_PLANNING")
    void assemblyAnalysis_bomExtracted_transitionsToPartProcessPlanning() {
        String projectId = createRuntime(ProjectState.ASSEMBLY_ANALYSIS);

        orchestrationService.onModuleResult(
                projectId, ProjectEvent.BOM_EXTRACTED, Map.of(), true);

        assertThat(orchestrationService.getState(projectId))
                .isEqualTo(ProjectState.PART_PROCESS_PLANNING);
    }
}
