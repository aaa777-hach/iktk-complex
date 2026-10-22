package ru.iktk.recommendation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.iktk.recommendation.client.EquipmentClient;
import ru.iktk.recommendation.config.RecommendationProperties;
import ru.iktk.recommendation.dto.GapAnalysisRequest;
import ru.iktk.recommendation.dto.GapAnalysisResponse;
import ru.iktk.recommendation.dto.EquipmentRequirementDto;
import ru.iktk.recommendation.exception.RecommendationException;
import ru.iktk.recommendation.mapper.RecommendationMapper;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;
import ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository;
import ru.iktk.recommendation.repository.EquipmentRecommendationRepository;
import ru.iktk.recommendation.repository.EquipmentRequirementRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GapAnalysisService — matchRequirement не маскирует недоступность equipment-service")
class GapAnalysisServiceMatchTest {

    @Mock private EquipmentGapAnalysisRepository gapRepository;
    @Mock private EquipmentRecommendationRepository recommendationRepository;
    @Mock private EquipmentRequirementRepository requirementRepository;
    @Mock private RecommendationMapper mapper;
    @Mock private EquipmentClient equipmentClient;
    @Mock private RecommendationProperties properties;

    @InjectMocks private GapAnalysisService service;

    @BeforeEach
    void setUp() {
        when(gapRepository.save(any(EquipmentGapAnalysis.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toGapResponse(any())).thenReturn(
                GapAnalysisResponse.builder().id("a1").build());
    }

    private GapAnalysisRequest request() {
        GapAnalysisRequest req = new GapAnalysisRequest();
        req.setProjectId("p1");
        req.setStrategy("HYBRID");
        req.setRequirements(List.of(EquipmentRequirementDto.builder()
                .id("r1").operationType("TURNING").requiredAccuracy("IT7")
                .build()));
        return req;
    }

    @Test
    @DisplayName("run: equipment-service бросает исключение → analysis.status=FAILED, RecommendationException")
    void run_equipmentServiceDown_marksAnalysisFailed() {
        when(equipmentClient.findMatching(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Connection refused: localhost:8094"));

        assertThatThrownBy(() -> service.run(request()))
                .isInstanceOf(RecommendationException.class);

        ArgumentCaptor<EquipmentGapAnalysis> captor =
                ArgumentCaptor.forClass(EquipmentGapAnalysis.class);
        verify(gapRepository, atLeast(1)).save(captor.capture());
        EquipmentGapAnalysis last = captor.getAllValues()
                .get(captor.getAllValues().size() - 1);
        assertThat(last.getStatus()).isEqualTo("FAILED");
        assertThat(last.getError()).isNotNull();
    }

    @Test
    @DisplayName("run: findMatching → empty → analysis с gaps=1, COMPLETED (дефицит)")
    void run_equipmentNotFound_createsGap() {
        when(equipmentClient.findMatching(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        service.run(request());

        ArgumentCaptor<EquipmentGapAnalysis> captor =
                ArgumentCaptor.forClass(EquipmentGapAnalysis.class);
        verify(gapRepository, atLeast(1)).save(captor.capture());
        EquipmentGapAnalysis last = captor.getAllValues()
                .get(captor.getAllValues().size() - 1);
        assertThat(last.getGapsCount()).isEqualTo(1);
        assertThat(last.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("run: findMatching → present → analysis с gaps=0, COMPLETED (нет дефицита)")
    void run_equipmentMatched_noGaps() {
        when(equipmentClient.findMatching(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(Map.of("equipmentId", "eq-casting-01")));

        service.run(request());

        ArgumentCaptor<EquipmentGapAnalysis> captor =
                ArgumentCaptor.forClass(EquipmentGapAnalysis.class);
        verify(gapRepository, atLeast(1)).save(captor.capture());
        EquipmentGapAnalysis last = captor.getAllValues()
                .get(captor.getAllValues().size() - 1);
        assertThat(last.getGapsCount()).isZero();
        assertThat(last.getStatus()).isEqualTo("COMPLETED");
    }
}