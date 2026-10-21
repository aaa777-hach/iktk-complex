package ru.iktk.recommendation.service;

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
import ru.iktk.recommendation.dto.GapAnalysisRequest;
import ru.iktk.recommendation.dto.GapAnalysisResponse;
import ru.iktk.recommendation.exception.RecommendationException;
import ru.iktk.recommendation.mapper.RecommendationMapper;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;
import ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository;
import ru.iktk.recommendation.repository.EquipmentRecommendationRepository;
import ru.iktk.shared.versioning.VersioningService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GapAnalysisService — gap-анализ")
class GapAnalysisServiceTest {

    @Mock private EquipmentGapAnalysisRepository gapRepository;
    @Mock private EquipmentRecommendationRepository recommendationRepository;
    @Mock private RecommendationMapper mapper;
    @Mock private EquipmentClient equipmentClient;
    @Mock private VersioningService versioningService;
    @Mock private RecommendationProperties properties;

    @InjectMocks private GapAnalysisService service;

    @Test
    @DisplayName("run: null -> RecommendationException")
    void run_null_throws() {
        assertThatThrownBy(() -> service.run(null))
                .isInstanceOf(RecommendationException.class);
    }

    @Test
    @DisplayName("run: пустой projectId -> RecommendationException")
    void run_emptyProject_throws() {
        assertThatThrownBy(() -> service.run(new GapAnalysisRequest()))
                .isInstanceOf(RecommendationException.class);
    }

    @Test
    @DisplayName("get: null -> validation")
    void get_null_throws() {
        assertThatThrownBy(() -> service.get(null))
                .isInstanceOf(RecommendationException.class);
    }

    @Test
    @DisplayName("get: не найден -> NOT_FOUND")
    void get_notFound_throws() {
        when(gapRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("missing"))
                .isInstanceOfSatisfying(RecommendationException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("get: найден -> mapper результат")
    void get_found_returnsMapped() {
        EquipmentGapAnalysis a = new EquipmentGapAnalysis("a1", "p1", "HYBRID");
        GapAnalysisResponse resp = GapAnalysisResponse.builder().id("a1").build();
        when(gapRepository.findById("a1")).thenReturn(Optional.of(a));
        when(mapper.toGapResponse(a)).thenReturn(resp);
        assertThat(service.get("a1").getId()).isEqualTo("a1");
    }

    @Test
    @DisplayName("getLatest: null -> validation")
    void getLatest_null_throws() {
        assertThatThrownBy(() -> service.getLatest(null))
                .isInstanceOf(RecommendationException.class);
    }

    @Test
    @DisplayName("getLatest: пусто -> NOT_FOUND")
    void getLatest_empty_throws() {
        when(gapRepository.findTopByProjectIdOrderByStartedAtDesc("p1"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getLatest("p1"))
                .isInstanceOfSatisfying(RecommendationException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("getAllByProject: null -> validation")
    void getAllByProject_null_throws() {
        assertThatThrownBy(() -> service.getAllByProject(null))
                .isInstanceOf(RecommendationException.class);
    }

    @Test
    @DisplayName("getAllByProject: нет данных -> пустой список")
    void getAllByProject_empty_returnsEmpty() {
        when(gapRepository.findByProjectIdOrderByStartedAtDesc("p1")).thenReturn(List.of());
        assertThat(service.getAllByProject("p1")).isEmpty();
    }
}