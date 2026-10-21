package ru.iktk.bom.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.iktk.bom.dto.BomAnalyzeRequest;
import ru.iktk.bom.dto.BomItemDto;
import ru.iktk.bom.dto.BomResponse;
import ru.iktk.bom.dto.BomTree;
import ru.iktk.bom.dto.SpecificationDto;
import ru.iktk.bom.exception.BomException;
import ru.iktk.bom.service.BomService;
import ru.iktk.shared.storage.StorageClient;
import ru.iktk.shared.versioning.VersioningService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bomtestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("BomController — REST API BOM")
class BomControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private BomService bomService;
    @MockBean private RabbitTemplate rabbitTemplate;
    @MockBean private StorageClient storageClient;
    @MockBean private VersioningService versioningService;

    private BomResponse response(String status) {
        return BomResponse.builder()
                .projectId("proj1")
                .assemblyId("u1")
                .bomId("u1")
                .status(status)
                .positionsCount(3)
                .connectionsCount(1)
                .message("ok")
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/bom/projects/{id}/analyze -> 202 + BomResponse")
    void analyze_returns202() throws Exception {
        when(bomService.analyze(any(BomAnalyzeRequest.class))).thenReturn(response("COMPLETED"));

        mvc.perform(post("/api/v1/bom/projects/proj1/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"proj1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.assemblyId").value("u1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.positionsCount").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/bom/analysis/{id} -> 200")
    void getAnalysis_returns200() throws Exception {
        when(bomService.getAnalysis("u1")).thenReturn(response("COMPLETED"));

        mvc.perform(get("/api/v1/bom/analyses/u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assemblyId").value("u1"));
    }

    @Test
    @DisplayName("GET /api/v1/bom/projects/{id} -> 200 + BomTree")
    void getByProject_returns200() throws Exception {
        BomTree tree = BomTree.builder()
                .id("u1").projectId("proj1").designation("СБ-001").name("Корпус")
                .items(List.of()).totalPositions(0).maxDepth(0)
                .build();
        when(bomService.getBomTreeByProject("proj1")).thenReturn(tree);

        mvc.perform(get("/api/v1/bom/projects/proj1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u1"))
                .andExpect(jsonPath("$.projectId").value("proj1"));
    }

    @Test
    @DisplayName("GET /api/v1/bom/{bomId}/tree -> 200")
    void getTree_returns200() throws Exception {
        BomTree tree = BomTree.builder().id("u1").projectId("proj1").build();
        when(bomService.getBomTree("u1")).thenReturn(tree);

        mvc.perform(get("/api/v1/bom/u1/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u1"));
    }

    @Test
    @DisplayName("GET /api/v1/bom/{bomId}/items -> 200 + список")
    void getItems_returns200() throws Exception {
        when(bomService.getItems(eq("u1"), any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/bom/u1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/bom/{bomId}/connections -> 200")
    void getConnections_returns200() throws Exception {
        when(bomService.getConnections("u1")).thenReturn(List.of());

        mvc.perform(get("/api/v1/bom/u1/connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/bom/{bomId}/sequence -> 200")
    void getSequence_returns200() throws Exception {
        when(bomService.getAssemblySequence("u1")).thenReturn(List.of());

        mvc.perform(get("/api/v1/bom/u1/assembly-sequence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/bom/{bomId}/specification -> 200")
    void getSpecification_returns200() throws Exception {
        SpecificationDto spec = new SpecificationDto("СБ-001", "Корпус");
        when(bomService.getSpecification("u1")).thenReturn(spec);

        mvc.perform(get("/api/v1/bom/u1/specification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designation").value("СБ-001"));
    }

    @Test
    @DisplayName("POST /api/v1/bom/{bomId}/items -> 201")
    void addItem_returns201() throws Exception {
        BomItemDto dto = BomItemDto.builder().id("i1").designation("Д-001").build();
        when(bomService.addItem(eq("u1"), any(), any(BomItemDto.class))).thenReturn(dto);

        mvc.perform(post("/api/v1/bom/u1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designation\":\"Д-001\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("i1"));
    }

    @Test
    @DisplayName("BomException -> 4xx с кодом и сообщением")
    void bomException_handler() throws Exception {
        when(bomService.getAnalysis("missing"))
                .thenThrow(BomException.notFound("не найден"));

        mvc.perform(get("/api/v1/bom/analyses/missing"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.message").value("не найден"));
    }
}