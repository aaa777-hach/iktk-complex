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
import ru.iktk.recommendation.dto.GapAnalysisRequest;
import ru.iktk.recommendation.dto.GapAnalysisResponse;
import ru.iktk.recommendation.mapper.RecommendationMapper;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;
import ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository;
import ru.iktk.recommendation.repository.EquipmentRecommendationRepository;
import ru.iktk.recommendation.repository.EquipmentRequirementRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GapAnalysisService — идемпотентность run() по projectId+strategy")
class GapAnalysisServiceIdempotencyTest {

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
        when(mapper.toGapResponse(any())).thenAnswer(inv -> {
            EquipmentGapAnalysis a = inv.getArgument(0);
            return GapAnalysisResponse.builder().id(a.getId()).status(a.getStatus()).build();
        });
    }

    private GapAnalysisRequest request() {
        GapAnalysisRequest req = new GapAnalysisRequest();
        req.setProjectId("p1");
        req.setStrategy("HYBRID");
        return req;
    }

    private EquipmentGapAnalysis existing(String id, String status, Instant startedAt) {
        EquipmentGapAnalysis a = new EquipmentGapAnalysis(id, "p1", "HYBRID");
        a.setStatus(status);
        a.setStartedAt(startedAt);
        return a;
    }

    @Test
    @DisplayName("run: свежий COMPLETED по projectId+strategy → переиспользуется, новый не создаётся")
    void run_recentCompleted_reusesExisting() {
        EquipmentGapAnalysis existing = existing("a-existing", "COMPLETED",
                Instant.now().minus(30, ChronoUnit.SECONDS));
        when(gapRepository.findTopByProjectIdAndStrategyOrderByStartedAtDesc("p1", "HYBRID"))
                .thenReturn(Optional.of(existing));

        GapAnalysisResponse resp = service.run(request());

        assertThat(resp.getId()).isEqualTo("a-existing");
        verify(gapRepository, never()).save(any());
    }

    @Test
    @DisplayName("run: свежий RUNNING → переиспользуется (параллельная доставка), новый не создаётся")
    void run_recentRunning_reusesExisting() {
        EquipmentGapAnalysis existing = existing("a-running", "RUNNING",
                Instant.now().minus(10, ChronoUnit.SECONDS));
        when(gapRepository.findTopByProjectIdAndStrategyOrderByStartedAtDesc("p1", "HYBRID"))
                .thenReturn(Optional.of(existing));

        GapAnalysisResponse resp = service.run(request());

        assertThat(resp.getId()).isEqualTo("a-running");
        assertThat(resp.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("run: старый COMPLETED (за окном 5 мин) → создаётся новый")
    void run_oldCompleted_createsNew() {
        EquipmentGapAnalysis existing = existing("a-old", "COMPLETED",
                Instant.now().minus(10, ChronoUnit.MINUTES));
        when(gapRepository.findTopByProjectIdAndStrategyOrderByStartedAtDesc("p1", "HYBRID"))
                .thenReturn(Optional.of(existing));
        when(requirementRepository.findByProjectIdOrderByCreatedAt("p1"))
                .thenReturn(java.util.List.of());
        when(equipmentClient.findMatching(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        service.run(request());

        verify(gapRepository, atLeast(1)).save(any(EquipmentGapAnalysis.class));
    }

    @Test
    @DisplayName("run: свежий FAILED → НЕ переиспользуется (нужен retry), создаётся новый")
    void run_recentFailed_createsNew() {
        EquipmentGapAnalysis existing = existing("a-failed", "FAILED",
                Instant.now().minus(30, ChronoUnit.SECONDS));
        when(gapRepository.findTopByProjectIdAndStrategyOrderByStartedAtDesc("p1", "HYBRID"))
                .thenReturn(Optional.of(existing));
        when(requirementRepository.findByProjectIdOrderByCreatedAt("p1"))
                .thenReturn(java.util.List.of());

        service.run(request());

        verify(gapRepository, atLeast(1)).save(any(EquipmentGapAnalysis.class));
    }
}