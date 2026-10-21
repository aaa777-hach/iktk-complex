package ru.iktk.recommendation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.iktk.recommendation.dto.EquipmentRecommendationDto;
import ru.iktk.recommendation.dto.EquipmentRequirementDto;
import ru.iktk.recommendation.dto.GapAnalysisRequest;
import ru.iktk.recommendation.dto.GapAnalysisResponse;
import ru.iktk.recommendation.dto.OutsourcingOptionDto;
import ru.iktk.recommendation.dto.RecommendationResponse;
import ru.iktk.recommendation.dto.VendorCatalogDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Покрытие геттеров/сеттеров DTO recommendation-service")
class RecommendationCoverageBoostTest {

    @Test
    @DisplayName("EquipmentRequirementDto: builder + setters")
    void equipmentRequirementDto_allFields() {
        EquipmentRequirementDto d = EquipmentRequirementDto.builder()
                .id("r1").processId("pr1").stageId("st1").operationId("op1")
                .operationType("TURNING").requiredAccuracy("IT7")
                .maxDimensions("1000x500x400").material("ST45")
                .requiredPowerKw(new BigDecimal("15.5"))
                .requiredForceKn(new BigDecimal("100"))
                .requiredCnc(true).quantity(2).notes("note")
                .build();
        assertThat(d.getId()).isEqualTo("r1");
        assertThat(d.getStageId()).isEqualTo("st1");
        assertThat(d.getRequiredPowerKw()).isEqualByComparingTo("15.5");
        assertThat(d.getRequiredCnc()).isTrue();
        d.setNotes("updated");
        assertThat(d.getNotes()).isEqualTo("updated");
    }

    @Test
    @DisplayName("GapAnalysisRequest: builder + setters")
    void gapAnalysisRequest_builder() {
        GapAnalysisRequest r = GapAnalysisRequest.builder()
                .projectId("p1").analysisId("a1").strategy("HYBRID")
                .requestedBy("ivanov")
                .requirements(List.of())
                .build();
        assertThat(r.getProjectId()).isEqualTo("p1");
        assertThat(r.getStrategy()).isEqualTo("HYBRID");
        assertThat(r.getRequirements()).isEmpty();
        r.setStrategy("STRICT");
        assertThat(r.getStrategy()).isEqualTo("STRICT");
    }

    @Test
    @DisplayName("GapAnalysisResponse: builder + setters")
    void gapAnalysisResponse_builder() {
        GapAnalysisResponse r = GapAnalysisResponse.builder()
                .id("a1").analysisId("a1").projectId("p1")
                .strategy("HYBRID").status("COMPLETED")
                .gapsCount(3).recommendationsCount(5)
                .durationMs(1500L).summary(Map.of("bySeverity", Map.of()))
                .startedAt(Instant.now()).completedAt(Instant.now())
                .build();
        assertThat(r.getId()).isEqualTo("a1");
        assertThat(r.getGapsCount()).isEqualTo(3);
        assertThat(r.getDurationMs()).isEqualTo(1500L);
        r.setError("x");
        assertThat(r.getError()).isEqualTo("x");
    }

    @Test
    @DisplayName("EquipmentRecommendationDto: builder + setters")
    void equipmentRecommendationDto_builder() {
        EquipmentRecommendationDto d = EquipmentRecommendationDto.builder()
                .id("r1").projectId("p1").gapId("g1").analysisId("a1")
                .type("PURCHASE").status("PENDING")
                .equipmentId("eq1").vendorId("v1")
                .rationale("ok").score(new BigDecimal("0.9"))
                .estimatedCost(new BigDecimal("100000")).leadTimeDays(30)
                .build();
        assertThat(d.getId()).isEqualTo("r1");
        assertThat(d.getScore()).isEqualByComparingTo("0.9");
        d.setStatus("ACCEPTED");
        assertThat(d.getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("RecommendationResponse: builder + state")
    void recommendationResponse_builder() {
        RecommendationResponse r = RecommendationResponse.builder()
                .recommendationId("r1").projectId("p1").gapId("g1")
                .type("PURCHASE").status("ACCEPTED").state("ACCEPTED")
                .message("ok").acceptedBy("ivanov")
                .build();
        assertThat(r.getRecommendationId()).isEqualTo("r1");
        assertThat(r.getState()).isEqualTo("ACCEPTED");
        r.setMessage("updated");
        assertThat(r.getMessage()).isEqualTo("updated");
    }

    @Test
    @DisplayName("VendorCatalogDto: builder + setters")
    void vendorCatalogDto_builder() {
        VendorCatalogDto d = VendorCatalogDto.builder()
                .id("v1").vendorId("vnd-1").vendorName("A")
                .equipmentType("TURNING").equipmentModel("M1")
                .price(new BigDecimal("100")).leadTimeDays(10)
                .region("Москва").active(true)
                .specifications(Map.of()).build();
        assertThat(d.getVendorName()).isEqualTo("A");
        assertThat(d.getActive()).isTrue();
        d.setRegion("СПб");
        assertThat(d.getRegion()).isEqualTo("СПб");
    }

    @Test
    @DisplayName("OutsourcingOptionDto: builder + setters")
    void outsourcingOptionDto_builder() {
        OutsourcingOptionDto d = OutsourcingOptionDto.builder()
                .id("o1").gapId("g1").projectId("p1").type("CONTRACTOR")
                .operation("TURNING").contractor("A")
                .cost(new BigDecimal("100")).qualityRating(new BigDecimal("0.9"))
                .comment("ok").leadTimeDays(7).status("PENDING")
                .score(new BigDecimal("0.8"))
                .build();
        assertThat(d.getContractor()).isEqualTo("A");
        assertThat(d.getQualityRating()).isEqualByComparingTo("0.9");
        d.setStatus("SELECTED");
        assertThat(d.getStatus()).isEqualTo("SELECTED");
    }
}