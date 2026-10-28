package ru.iktk.orchestrator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.iktk.core.dto.OrchestrationRequest;
import ru.iktk.core.dto.OrchestrationResponse;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.orchestrator.service.DecisionLogService;
import ru.iktk.orchestrator.service.OrchestrationService;
import ru.iktk.orchestrator.statemachine.ProjectEvent;
import ru.iktk.orchestrator.statemachine.ProjectState;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrchestratorController.class,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration")
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("OrchestratorController — REST API")
class OrchestratorControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private OrchestrationService orchestrationService;
    @MockBean private DecisionLogService decisionLogService;

    private OrchestrationResponse response() {
        return OrchestrationResponse.builder()
                .projectId("p1")
                .projectName("Тест")
                .state(ProjectState.PROJECT_INIT.name())
                .stateDisplayName("Init")
                .stateCategory("AUTO")
                .pointOfControl(false)
                .awaitingUserAction(false)
                .availableEvents(List.of())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("POST /projects -> 201 + JSON с projectId")
    void createProject_returns201() throws Exception {
        OrchestrationRequest req = OrchestrationRequest.builder()
                .projectName("Тест").requestedBy("u").build();
        when(orchestrationService.startProject(any())).thenReturn(response());

        mvc.perform(post("/api/v1/orchestrator/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value("p1"))
                .andExpect(jsonPath("$.projectName").value("Тест"));
    }

    @Test
    @DisplayName("POST /projects/{id}/run -> 200")
    void run_returnsResponse() throws Exception {
        when(orchestrationService.run("p1")).thenReturn(response());
        mvc.perform(post("/api/v1/orchestrator/projects/p1/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value("p1"));
    }

    @Test
    @DisplayName("GET /projects/{id}/state -> 200 + имя enum")
    void getState_returnsState() throws Exception {
        when(orchestrationService.getState("p1")).thenReturn(ProjectState.NORMING);
        mvc.perform(get("/api/v1/orchestrator/projects/p1/state"))
                .andExpect(status().isOk())
                .andExpect(content().string("\"NORMING\""));
    }

    @Test
    @DisplayName("GET /projects/{id}/summary -> 200")
    void getSummary_returnsResponse() throws Exception {
        when(orchestrationService.getSummary("p1")).thenReturn(response());
        mvc.perform(get("/api/v1/orchestrator/projects/p1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value("p1"));
    }

    @Test
    @DisplayName("GET /projects/{id}/history?limit=50 -> 200")
    void getHistory_returnsList() throws Exception {
        when(decisionLogService.getHistory(eq("p1"), eq(50)))
                .thenReturn(List.of(Map.of("action", "PROJECT_STARTED")));
        mvc.perform(get("/api/v1/orchestrator/projects/p1/history").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("PROJECT_STARTED"));
    }

    @Test
    @DisplayName("POST /projects/{id}/approve -> 200")
    void approve_returnsResponse() throws Exception {
        when(orchestrationService.approve(eq("p1"), any())).thenReturn(response());
        mvc.perform(post("/api/v1/orchestrator/projects/p1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"OK\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /projects/{id}/events?event=NORMS_READY -> 200")
    void sendEvent_returnsResponse() throws Exception {
        when(orchestrationService.sendEvent(eq("p1"), eq(ProjectEvent.NORMS_READY), any()))
                .thenReturn(response());
        mvc.perform(post("/api/v1/orchestrator/projects/p1/events")
                        .param("event", "NORMS_READY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST .../accept -> 200")
    void acceptRecommendation_returnsResponse() throws Exception {
        when(orchestrationService.acceptRecommendation(eq("p1"), eq("r1"), any()))
                .thenReturn(response());
        mvc.perform(post("/api/v1/orchestrator/projects/p1/recommendations/r1/accept")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST .../adjust -> 200")
    void adjustProcess_returnsResponse() throws Exception {
        when(orchestrationService.adjustProcess(eq("p1"), eq("r1"), any()))
                .thenReturn(response());
        mvc.perform(post("/api/v1/orchestrator/projects/p1/recommendations/r1/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEquipmentId\":\"e5\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST .../outsource -> 200")
    void selectOutsourcing_returnsResponse() throws Exception {
        when(orchestrationService.selectOutsourcing(eq("p1"), eq("r1"), any()))
                .thenReturn(response());
        mvc.perform(post("/api/v1/orchestrator/projects/p1/recommendations/r1/outsource")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /projects/{id}/rollback?version=v3 -> 200")
    void rollback_returnsResponse() throws Exception {
        when(orchestrationService.rollback("p1", "v3")).thenReturn(response());
        mvc.perform(post("/api/v1/orchestrator/projects/p1/rollback").param("version", "v3"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("OrchestrationException -> 422 + error/message")
    void orchestrationException_returns422() throws Exception {
        when(orchestrationService.getState("p1"))
                .thenThrow(new OrchestrationException("нет проекта"));
        mvc.perform(get("/api/v1/orchestrator/projects/p1/state"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("ORCHESTRATION_ERROR"))
                .andExpect(jsonPath("$.message").value("нет проекта"));
    }
    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("POST /projects -> 422 on OrchestrationException")
    void createProject_withoutTp_returns422() throws Exception {
        when(orchestrationService.startProject(any())).thenThrow(new OrchestrationException("TP required"));
        OrchestrationRequest req = OrchestrationRequest.builder().projectName("Test").build();
        mvc.perform(post("/api/v1/orchestrator/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("ORCHESTRATION_ERROR"));
    }
}