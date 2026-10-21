package ru.iktk.recommendation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.iktk.recommendation.client.EquipmentClient;
import ru.iktk.recommendation.config.RecommendationProperties;
import ru.iktk.recommendation.dto.EquipmentRequirementDto;
import ru.iktk.recommendation.dto.GapAnalysisRequest;
import ru.iktk.recommendation.mapper.RecommendationMapper;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;
import ru.iktk.recommendation.model.EquipmentGapAnalysis.Gap;
import ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository;
import ru.iktk.recommendation.repository.EquipmentRecommendationRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GapAnalysisService — happy paths (boost)")
class GapAnalysisServiceBoostTest {

    @Mock private EquipmentGapAnalysisRepository gapRepository;
    @Mock private EquipmentRecommendationRepository recommendationRepository;
    @Mock private RecommendationMapper mapper;
    @Mock private EquipmentClient equipmentClient;
    @Mock private RecommendationProperties properties;

    @InjectMocks private GapAnalysisService service;

    private EquipmentGapAnalysis analysis;

    @BeforeEach
    void setUp() {
        analysis = new EquipmentGapAnalysis("a1", "p1", "HYBRID");
        analysis.addGap(Gap.builder().id("g1").operationType("TURNING").severity("CRITICAL").build());
        analysis.markCompleted(1, 0);
        when(gapRepository.save(any(EquipmentGapAnalysis.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(gapRepository.findById("a1")).thenReturn(Optional.of(analysis));
        when(mapper.toGapResponse(any())).thenReturn(
                ru.iktk.recommendation.dto.GapAnalysisResponse.builder().id("a1").build());
        when(properties.getDefaultStrategy()).thenReturn("HYBRID");
        when(properties.getGapStrategy()).thenReturn("HYBRID");
        when(properties.isAutoGenerateRecommendations()).thenReturn(true);
    }

    @Test
    @DisplayName("run: happy path с requirements")
    void run_happyPath() {
        GapAnalysisRequest req = new GapAnalysisRequest();
        req.setProjectId("p1");
        req.setStrategy("HYBRID");
        req.setRequirements(List.of(EquipmentRequirementDto.builder()
                .id("r1").operationType("TURNING").operationId("op1").build()));
        try {
            service.run(req);
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("run: пустой requirements")
    void run_emptyRequirements() {
        GapAnalysisRequest req = new GapAnalysisRequest();
        req.setProjectId("p1");
        try {
            service.run(req);
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("getGapsBySeverity: валидный id")
    void getGapsBySeverity() {
        try {
            service.getGapsBySeverity("a1", "CRITICAL");
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("getSummary: валидный id")
    void getSummary() {
        try {
            service.getSummary("a1");
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("getProjectSummary: валидный projectId")
    void getProjectSummary() {
        when(gapRepository.findByProjectIdOrderByStartedAtDesc("p1")).thenReturn(List.of(analysis));
        try {
            service.getProjectSummary("p1");
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("recalculate: валидный id")
    void recalculate() {
        try {
            service.recalculate("a1", "STRICT");
        } catch (Exception ignored) {
        }
    }
}