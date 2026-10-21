package ru.iktk.recommendation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.iktk.recommendation.dto.GapAnalysisRequest;
import ru.iktk.recommendation.dto.GapAnalysisResponse;
import ru.iktk.recommendation.dto.OutsourcingOptionDto;
import ru.iktk.recommendation.dto.RecommendationResponse;
import ru.iktk.recommendation.service.EquipmentRecommendationService;
import ru.iktk.recommendation.service.GapAnalysisService;
import ru.iktk.recommendation.service.OutsourcingService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RecommendationController — REST API рекомендаций")
class RecommendationControllerTest {

    @Mock private GapAnalysisService gapAnalysisService;
    @Mock private EquipmentRecommendationService recommendationService;
    @Mock private OutsourcingService outsourcingService;

    private MockMvc mvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RecommendationController controller = new RecommendationController(
                gapAnalysisService, recommendationService, outsourcingService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /gap-analysis -> 200 + id")
    void runGapAnalysis_returnsOk() throws Exception {
        GapAnalysisResponse resp = GapAnalysisResponse.builder()
                .id("a1").projectId("p1").strategy("HYBRID").status("COMPLETED")
                .gapsCount(3).recommendationsCount(5)
                .build();
        when(gapAnalysisService.run(any(GapAnalysisRequest.class))).thenReturn(resp);

        String body = objectMapper.writeValueAsString(
                GapAnalysisRequest.builder().projectId("p1").build());

        mvc.perform(post("/api/recommendations/gap-analysis")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("a1"))
                .andExpect(jsonPath("$.projectId").value("p1"));
    }

    @Test
    @DisplayName("GET /gap-analysis/{id} -> 200 + id")
    void getGapAnalysis_returnsOk() throws Exception {
        when(gapAnalysisService.get(eq("a1"))).thenReturn(
                GapAnalysisResponse.builder().id("a1").build());

        mvc.perform(get("/api/recommendations/gap-analysis/a1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("a1"));
    }

    @Test
    @DisplayName("GET /gap-analysis/project/{id} -> 200 + список")
    void getAllGapAnalyses_returnsList() throws Exception {
        when(gapAnalysisService.getAllByProject(eq("p1")))
                .thenReturn(List.of(GapAnalysisResponse.builder().id("a1").build()));

        mvc.perform(get("/api/recommendations/gap-analysis/project/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("a1"));
    }

    @Test
    @DisplayName("GET /vendors -> 200 + список")
    void searchVendors_returnsList() throws Exception {
        when(recommendationService.searchVendorCatalog(eq("TURNING"), eq(null), eq(null), eq(null)))
                .thenReturn(List.of());

        mvc.perform(get("/api/recommendations/vendors?equipmentType=TURNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /{id}/accept -> 200")
    void accept_returnsOk() throws Exception {
        when(recommendationService.accept(eq("r1"), eq("ok")))
                .thenReturn(RecommendationResponse.builder()
                        .recommendationId("r1").status("ACCEPTED").build());

        mvc.perform(post("/api/recommendations/r1/accept?comment=ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("GET /outsourcing/{id} -> 200")
    void getOutsourcing_returnsOk() throws Exception {
        when(outsourcingService.getById(eq("o1")))
                .thenReturn(OutsourcingOptionDto.builder()
                        .id("o1").contractor("A").build());

        mvc.perform(get("/api/recommendations/outsourcing/o1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("o1"))
                .andExpect(jsonPath("$.contractor").value("A"));
    }
}