package ru.iktk.orchestrator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import ru.iktk.core.dto.OrchestrationRequest;
import ru.iktk.core.dto.OrchestrationResponse;
import ru.iktk.core.domain.Project;
import ru.iktk.orchestrator.messaging.ModuleCommandProducer;
import ru.iktk.orchestrator.model.ProjectRuntime;
import ru.iktk.orchestrator.repository.ProjectStateRepository;
import ru.iktk.orchestrator.service.OrchestrationService;
import ru.iktk.orchestrator.statemachine.ProjectState;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Сквозной сценарий: startProject -> run -> состояние сохраняется в H2")
class OrchestrationIntegrationTest {

    @Autowired
    private OrchestrationService orchestrationService;

    @Autowired
    private ProjectStateRepository projectStateRepository;

    @MockBean
    private ModuleCommandProducer moduleCommandProducer;

    @Test
    @DisplayName("startProject сохраняет runtime в H2, run читает его обратно")
    void startProject_thenRun() {
        OrchestrationRequest request = OrchestrationRequest.builder()
                .projectName("Интеграционный корпус")
                .designation("ИНТ-001")
                .objectType(Project.ObjectType.ASSEMBLY)
                .requestedBy("integration-test")
                .build();

        OrchestrationResponse startResponse = orchestrationService.startProject(request);

        assertThat(startResponse).isNotNull();
        assertThat(startResponse.getProjectId()).isNotBlank();
        String projectId = startResponse.getProjectId();

        ProjectRuntime saved = projectStateRepository.findByProjectId(projectId)
                .orElseThrow(() -> new AssertionError("Проект не сохранён в БД: " + projectId));
        assertThat(saved.getProjectName()).isEqualTo("Интеграционный корпус");
        assertThat(saved.getState()).isNotNull();

        OrchestrationResponse runResponse = orchestrationService.run(projectId);
        assertThat(runResponse).isNotNull();
        assertThat(runResponse.getProjectId()).isEqualTo(projectId);

        ProjectState state = orchestrationService.getState(projectId);
        assertThat(state).isNotNull();
    }
}