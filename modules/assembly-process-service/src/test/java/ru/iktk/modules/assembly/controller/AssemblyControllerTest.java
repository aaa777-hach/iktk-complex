package ru.iktk.modules.assembly.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.iktk.modules.assembly.dto.AssemblyRequest;
import ru.iktk.modules.assembly.dto.AssemblyResponse;
import ru.iktk.modules.assembly.model.AssemblyProcess;
import ru.iktk.modules.assembly.service.AssemblyProcessService;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AssemblyController.class,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration")
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AssemblyController — REST API ТП сборки")
class AssemblyControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AssemblyProcessService service;

    private AssemblyResponse response() {
        return AssemblyResponse.builder()
                .processId("p1")
                .projectId("proj1")
                .objectRef("obj-1")
                .strategy("SEQUENTIAL")
                .status("DRAFT")
                .stageCount(5)
                .componentCount(3)
                .totalTimeMin(new BigDecimal("165.0"))
                .message("ТП сборки спроектирован")
                .build();
    }

    @Test
    @DisplayName("GET /health -> 200 + status=UP")
    void health_returnsOk() throws Exception {
        mvc.perform(get("/api/v1/assembly/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("assembly-process-service"));
    }

    @Test
    @DisplayName("POST /processes -> 201 + JSON с processId")
    void designProcess_created() throws Exception {
        AssemblyRequest req = new AssemblyRequest("proj1", "obj-1");
        when(service.designProcess(any(AssemblyRequest.class))).thenReturn(response());

        mvc.perform(post("/api/v1/assembly/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.processId").value("p1"))
                .andExpect(jsonPath("$.projectId").value("proj1"))
                .andExpect(jsonPath("$.stageCount").value(5))
                .andExpect(jsonPath("$.componentCount").value(3));
    }

    @Test
    @DisplayName("POST /processes: IllegalArgumentException -> 400")
    void designProcess_badRequest() throws Exception {
        when(service.designProcess(any(AssemblyRequest.class)))
                .thenThrow(new IllegalArgumentException("projectId не задан"));

        mvc.perform(post("/api/v1/assembly/processes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /processes/{id} -> 200 + JSON")
    void getProcess_found() throws Exception {
        AssemblyProcess p = new AssemblyProcess("p1", "proj1");
        p.setObjectRef("obj-1");
        when(service.getById("p1")).thenReturn(p);

        mvc.perform(get("/api/v1/assembly/processes/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("p1"))
                .andExpect(jsonPath("$.projectId").value("proj1"));
    }

    @Test
    @DisplayName("GET /processes/{id}: не найден -> 404")
    void getProcess_notFound() throws Exception {
        when(service.getById("missing")).thenThrow(new IllegalArgumentException("не найден"));

        mvc.perform(get("/api/v1/assembly/processes/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /processes?projectId= -> 200 + список")
    void listProcesses_ok() throws Exception {
        when(service.listByProject("proj1"))
                .thenReturn(List.of(new AssemblyProcess("p1", "proj1")));

        mvc.perform(get("/api/v1/assembly/processes").param("projectId", "proj1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("p1"))
                .andExpect(jsonPath("$[0].projectId").value("proj1"));
    }

    @Test
    @DisplayName("GET /processes?projectId=: пусто -> пустой массив")
    void listProcesses_empty() throws Exception {
        when(service.listByProject("proj1")).thenReturn(List.of());

        mvc.perform(get("/api/v1/assembly/processes").param("projectId", "proj1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}