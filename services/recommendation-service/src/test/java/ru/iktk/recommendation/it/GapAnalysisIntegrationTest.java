package ru.iktk.recommendation.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import ru.iktk.recommendation.client.EquipmentClient;
import ru.iktk.recommendation.dto.EquipmentRequirementDto;
import ru.iktk.recommendation.dto.GapAnalysisRequest;
import ru.iktk.recommendation.dto.GapAnalysisResponse;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;
import ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository;
import ru.iktk.recommendation.service.GapAnalysisService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("IT: GapAnalysisService на реальных PostgreSQL + Flyway + RabbitMQ")
class GapAnalysisIntegrationTest extends AbstractIntegrationTest {

    @Autowired private GapAnalysisService gapAnalysisService;
    @Autowired private EquipmentGapAnalysisRepository gapRepository;

    @MockBean private EquipmentClient equipmentClient;

    @Test
    @DisplayName("run(): Flyway применил V1, ddl-auto: validate прошёл, анализ сохранён")
    void run_flywayAndValidate_analysisSaved() {
        // Дефицит: findMatching → empty → создаётся Gap
        when(equipmentClient.findMatching(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        GapAnalysisRequest req = new GapAnalysisRequest();
        req.setProjectId("it-project-1");
        req.setStrategy("HYBRID");
        req.setRequirements(List.of(EquipmentRequirementDto.builder()
                .id("r1").operationType("TURNING").requiredAccuracy("IT7")
                .requiredCnc(true)
                .build()));

        GapAnalysisResponse response = gapAnalysisService.run(req);

        assertThat(response.getId()).isNotBlank();
        assertThat(response.getGapsCount()).isEqualTo(1);

        // Проверяем БД: строка реально записана
        Optional<EquipmentGapAnalysis> saved = gapRepository.findById(response.getId());
        assertThat(saved).isPresent();
        assertThat(saved.get().getProjectId()).isEqualTo("it-project-1");
        assertThat(saved.get().getStatus()).isEqualTo("COMPLETED");
        assertThat(saved.get().getGapsCount()).isEqualTo(1);
        assertThat(saved.get().getGaps()).hasSize(1);
    }

    @Test
    @DisplayName("run(): повторный вызов по тому же projectId+strategy → переиспользование (идемпотентность на реальной БД)")
    void run_sameProject_reusesExisting() {
        when(equipmentClient.findMatching(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        GapAnalysisRequest req = new GapAnalysisRequest();
        req.setProjectId("it-project-idem");
        req.setStrategy("HYBRID");
        req.setRequirements(List.of(EquipmentRequirementDto.builder()
                .id("r1").operationType("TURNING")
                .build()));

        GapAnalysisResponse first = gapAnalysisService.run(req);
        GapAnalysisResponse second = gapAnalysisService.run(req);

        assertThat(second.getId()).isEqualTo(first.getId());

        long count = gapRepository.findAll().stream()
                .filter(a -> "it-project-idem".equals(a.getProjectId()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("run(): equipment-service недоступен → analysis=FAILED, но строка в БД есть (REQUIRES_NEW)")
    void run_equipmentServiceDown_analysisFailedButPersisted() {
        when(equipmentClient.findMatching(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        GapAnalysisRequest req = new GapAnalysisRequest();
        req.setProjectId("it-project-fail");
        req.setStrategy("HYBRID");
        req.setRequirements(List.of(EquipmentRequirementDto.builder()
                .id("r1").operationType("TURNING")
                .build()));

        try {
            gapAnalysisService.run(req);
        } catch (Exception ignored) {
            // ожидаем RecommendationException.gapAnalysisFailed
        }

        // REQUIRES_NEW: строка сохранена со статусом FAILED, несмотря на откат основной транзакции
        List<EquipmentGapAnalysis> failed = gapRepository.findAll().stream()
                .filter(a -> "it-project-fail".equals(a.getProjectId()))
                .toList();
        assertThat(failed).hasSize(1);
        assertThat(failed.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(failed.get(0).getError()).isNotBlank();
    }
}