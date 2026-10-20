package ru.iktk.equipment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.iktk.equipment.model.EquipmentGapAnalysis;
import ru.iktk.equipment.model.EquipmentRequirement;
import ru.iktk.equipment.model.EquipmentStatus;
import ru.iktk.equipment.model.EquipmentTooling;
import ru.iktk.equipment.service.EquipmentService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EquipmentController.class,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration")
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("EquipmentController — REST API реестра")
class EquipmentControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private EquipmentService service;

    // ===== health / isMethodAvailable =====

    @Test
    @DisplayName("GET /health -> 200 + status=UP")
    void health_returnsOk() throws Exception {
        mvc.perform(get("/api/v1/equipment/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("equipment-service"));
    }

    @Test
    @DisplayName("GET /is-method-available?projectId=&method= -> 200 + boolean")
    void isMethodAvailable_returnsBoolean() throws Exception {
        when(service.isMethodAvailable("TURNING")).thenReturn(true);
        mvc.perform(get("/api/v1/equipment/is-method-available")
                        .param("projectId", "p1").param("method", "TURNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    // ===== status =====

    @Test
    @DisplayName("GET /{id}/status: найден -> 200 + JSON")
    void getStatus_found() throws Exception {
        EquipmentStatus s = new EquipmentStatus("s1", "eq1");
        s.setState("BUSY");
        s.setLoadPercent(42);
        when(service.findStatus("eq1")).thenReturn(Optional.of(s));

        mvc.perform(get("/api/v1/equipment/eq1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentId").value("eq1"))
                .andExpect(jsonPath("$.state").value("BUSY"))
                .andExpect(jsonPath("$.loadPercent").value(42));
    }

    @Test
    @DisplayName("GET /{id}/status: нет -> 404")
    void getStatus_notFound() throws Exception {
        when(service.findStatus("eq1")).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/equipment/eq1/status"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /{id}/status: 200 + сохранённый объект")
    void updateStatus_ok() throws Exception {
        EquipmentStatus saved = new EquipmentStatus("s1", "eq1");
        saved.setState("MAINTENANCE");
        when(service.saveStatus(eq("eq1"), any(EquipmentStatus.class))).thenReturn(saved);

        mvc.perform(put("/api/v1/equipment/eq1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"MAINTENANCE\",\"loadPercent\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("MAINTENANCE"));
    }

    @Test
    @DisplayName("PUT /{id}/status: equipment не найден -> 404")
    void updateStatus_notFound() throws Exception {
        when(service.saveStatus(eq("eq1"), any(EquipmentStatus.class)))
                .thenThrow(new IllegalArgumentException("not found"));

        mvc.perform(put("/api/v1/equipment/eq1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ===== tooling =====

    @Test
    @DisplayName("GET /{id}/tooling -> 200 + список")
    void listTooling_ok() throws Exception {
        when(service.listToolingByEquipment("eq1"))
                .thenReturn(List.of(new EquipmentTooling("eq1", "t1")));
        mvc.perform(get("/api/v1/equipment/eq1/tooling"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id.equipmentId").value("eq1"))
                .andExpect(jsonPath("$[0].id.toolingId").value("t1"));
    }

    @Test
    @DisplayName("POST /{id}/tooling -> 201")
    void addTooling_created() throws Exception {
        EquipmentTooling saved = new EquipmentTooling("eq1", "t1");
        saved.setCompatibility("PARTIAL");
        when(service.addTooling("eq1", "t1", "PARTIAL", "note")).thenReturn(saved);

        mvc.perform(post("/api/v1/equipment/eq1/tooling")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toolingId\":\"t1\",\"compatibility\":\"PARTIAL\",\"notes\":\"note\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.compatibility").value("PARTIAL"));
    }

    @Test
    @DisplayName("POST /{id}/tooling: equipment нет -> 404")
    void addTooling_notFound() throws Exception {
        when(service.addTooling(anyString(), anyString(), any(), any()))
                .thenThrow(new IllegalArgumentException());
        mvc.perform(post("/api/v1/equipment/eq1/tooling")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toolingId\":\"t1\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /{id}/tooling/{tid}: true -> 204")
    void removeTooling_noContent() throws Exception {
        when(service.removeTooling("eq1", "t1")).thenReturn(true);
        mvc.perform(delete("/api/v1/equipment/eq1/tooling/t1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /{id}/tooling/{tid}: false -> 404")
    void removeTooling_notFound() throws Exception {
        when(service.removeTooling("eq1", "t1")).thenReturn(false);
        mvc.perform(delete("/api/v1/equipment/eq1/tooling/t1"))
                .andExpect(status().isNotFound());
    }

    // ===== requirements =====

    @Test
    @DisplayName("GET /requirements?projectId= -> 200")
    void listRequirements_ok() throws Exception {
        when(service.listRequirements("p1")).thenReturn(List.of());
        mvc.perform(get("/api/v1/equipment/requirements").param("projectId", "p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /requirements -> 201")
    void createRequirement_created() throws Exception {
        EquipmentRequirement saved = new EquipmentRequirement("r1", "p1", "TURNING");
        when(service.saveRequirement(any(EquipmentRequirement.class))).thenReturn(saved);

        mvc.perform(post("/api/v1/equipment/requirements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"r1\",\"projectId\":\"p1\",\"operationType\":\"TURNING\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("r1"));
    }

    @Test
    @DisplayName("DELETE /requirements/{id}: true -> 204")
    void deleteRequirement_noContent() throws Exception {
        when(service.deleteRequirement("r1")).thenReturn(true);
        mvc.perform(delete("/api/v1/equipment/requirements/r1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /requirements/{id}: false -> 404")
    void deleteRequirement_notFound() throws Exception {
        when(service.deleteRequirement("r1")).thenReturn(false);
        mvc.perform(delete("/api/v1/equipment/requirements/r1"))
                .andExpect(status().isNotFound());
    }

    // ===== gap-analysis =====

    @Test
    @DisplayName("GET /gap-analysis?projectId= -> 200")
    void listGapAnalyses_ok() throws Exception {
        when(service.listGapAnalyses("p1")).thenReturn(List.of());
        mvc.perform(get("/api/v1/equipment/gap-analysis").param("projectId", "p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /gap-analysis -> 201")
    void startGapAnalysis_created() throws Exception {
        EquipmentGapAnalysis saved = new EquipmentGapAnalysis("g1", "p1");
        when(service.startGapAnalysis("p1", "FLEXIBLE", "ivanov")).thenReturn(saved);

        mvc.perform(post("/api/v1/equipment/gap-analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"p1\",\"strategy\":\"FLEXIBLE\",\"requestedBy\":\"ivanov\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("g1"));
    }

    @Test
    @DisplayName("POST /gap-analysis/{id}/complete: найден -> 200")
    void completeGapAnalysis_ok() throws Exception {
        EquipmentGapAnalysis ga = new EquipmentGapAnalysis("g1", "p1");
        ga.setStatus("COMPLETED");
        when(service.completeGapAnalysis("g1", "COMPLETED", null)).thenReturn(Optional.of(ga));

        mvc.perform(post("/api/v1/equipment/gap-analysis/g1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /gap-analysis/{id}/complete: не найден -> 404")
    void completeGapAnalysis_notFound() throws Exception {
        when(service.completeGapAnalysis(eq("g1"), any(), any())).thenReturn(Optional.empty());
        mvc.perform(post("/api/v1/equipment/gap-analysis/g1/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /gap-analysis/{id}/complete: пустое тело -> 200")
    void completeGapAnalysis_noBody() throws Exception {
        EquipmentGapAnalysis ga = new EquipmentGapAnalysis("g1", "p1");
        ga.setStatus("COMPLETED");
        when(service.completeGapAnalysis("g1", null, null)).thenReturn(Optional.of(ga));

        mvc.perform(post("/api/v1/equipment/gap-analysis/g1/complete"))
                .andExpect(status().isOk());
    }
}