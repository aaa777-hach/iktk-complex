package ru.iktk.recommendation.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.iktk.recommendation.dto.EquipmentRecommendationDto;
import ru.iktk.recommendation.dto.GapAnalysisResponse;
import ru.iktk.recommendation.dto.OutsourcingOptionDto;
import ru.iktk.recommendation.dto.VendorCatalogDto;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;
import ru.iktk.recommendation.model.EquipmentRecommendation;
import ru.iktk.recommendation.model.EquipmentRecommendation.RecommendationType;
import ru.iktk.recommendation.model.OutsourcingOption;
import ru.iktk.recommendation.model.VendorCatalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecommendationMapper — entity <-> dto")
class RecommendationMapperTest {

    private RecommendationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new RecommendationMapper(new ObjectMapper());
    }

    @Test
    @DisplayName("toDto(EquipmentRecommendation): все поля + null")
    void toDto_recommendation() {
        EquipmentRecommendation r = EquipmentRecommendation.builder()
                .id("r1").projectId("p1").gapId("g1").analysisId("a1")
                .type(RecommendationType.PURCHASE).status("PENDING")
                .equipmentId("eq1").vendorId("v1").rationale("ok")
                .score(new BigDecimal("0.9")).estimatedCost(new BigDecimal("1000"))
                .leadTimeDays(30).acceptedBy("ivanov").acceptedAt(Instant.now())
                .parameters(Map.of("k", "v"))
                .build();
        EquipmentRecommendationDto dto = mapper.toDto(r);
        assertThat(dto.getId()).isEqualTo("r1");
        assertThat(dto.getScore()).isEqualByComparingTo("0.9");
        assertThat(dto.getParameters()).containsKey("k");
        assertThat(mapper.toDto((EquipmentRecommendation) null)).isNull();
    }

    @Test
    @DisplayName("toGapResponse: все поля + alias toDto + null")
    void toGapResponse() {
        EquipmentGapAnalysis a = EquipmentGapAnalysis.builder()
                .id("a1").projectId("p1").strategy("HYBRID").status("COMPLETED")
                .requestedBy("ivanov").gapsCount(3).recommendationsCount(5)
                .gaps(List.of()).summary(Map.of("x", 1))
                .startedAt(Instant.now()).completedAt(Instant.now())
                .error("err")
                .build();
        GapAnalysisResponse resp = mapper.toGapResponse(a);
        assertThat(resp.getId()).isEqualTo("a1");
        assertThat(resp.getGapsCount()).isEqualTo(3);
        assertThat(resp.getError()).isEqualTo("err");
        assertThat(mapper.toDto(a)).isNotNull();
        assertThat(mapper.toGapResponse(null)).isNull();
    }

    @Test
    @DisplayName("toVendorDto: все поля + alias toDto + null")
    void toVendorDto() {
        VendorCatalog v = VendorCatalog.builder()
                .id("v1").vendorId("vnd").vendorName("A")
                .equipmentType("TURNING").equipmentModel("M1")
                .price(new BigDecimal("100")).leadTimeDays(10)
                .contactInfo("+7").region("Msk").active(true)
                .specifications(Map.of("spec", "x"))
                .build();
        VendorCatalogDto dto = mapper.toVendorDto(v);
        assertThat(dto.getId()).isEqualTo("v1");
        assertThat(dto.getPrice()).isEqualByComparingTo("100");
        assertThat(dto.getSpecifications()).containsKey("spec");
        assertThat(mapper.toDto(v)).isNotNull();
        assertThat(mapper.toVendorDto(null)).isNull();
    }

    @Test
    @DisplayName("toOutsourcingDto: все поля + alias + null")
    void toOutsourcingDto() {
        OutsourcingOption o = OutsourcingOption.builder()
                .id("o1").gapId("g1").projectId("p1").type("CONTRACTOR")
                .operation("TURNING").contractor("A")
                .cost(new BigDecimal("100")).qualityRating(new BigDecimal("0.9"))
                .comment("ok").leadTimeDays(7).status("PENDING")
                .score(new BigDecimal("0.8")).selectedBy("ivanov")
                .selectedAt(Instant.now()).parameters(Map.of("k", "v"))
                .build();
        OutsourcingOptionDto dto = mapper.toOutsourcingDto(o);
        assertThat(dto.getId()).isEqualTo("o1");
        assertThat(dto.getOperation()).isEqualTo("TURNING");
        assertThat(dto.getQualityRating()).isEqualByComparingTo("0.9");
        assertThat(mapper.toDto(o)).isNotNull();
        assertThat(mapper.toOutsourcingDto(null)).isNull();
    }

    @Test
    @DisplayName("toOutsourcingEntity: все поля + null")
    void toOutsourcingEntity() {
        OutsourcingOptionDto d = OutsourcingOptionDto.builder()
                .id("o1").gapId("g1").projectId("p1").type("CONTRACTOR")
                .operation("TURNING").contractor("A")
                .cost(new BigDecimal("100")).qualityRating(new BigDecimal("0.9"))
                .comment("ok").leadTimeDays(7).status("PENDING")
                .score(new BigDecimal("0.8")).selectedBy("ivanov")
                .selectedAt(Instant.now()).parameters(Map.of("k", "v"))
                .build();
        OutsourcingOption entity = mapper.toOutsourcingEntity(d);
        assertThat(entity.getId()).isEqualTo("o1");
        assertThat(entity.getOperation()).isEqualTo("TURNING");
        assertThat(entity.getContractor()).isEqualTo("A");
        assertThat(mapper.toOutsourcingEntity(null)).isNull();
    }

    @Test
    @DisplayName("toJsonNode: объект + невалидный (fallback null)")
    void toJsonNode() {
        JsonNode node = mapper.toJsonNode(Map.of("k", "v"));
        assertThat(node).isNotNull();
        assertThat(node.get("k").asText()).isEqualTo("v");
    }

    @Test
    @DisplayName("toDtoList: список + null")
    void toDtoList() {
        EquipmentRecommendation r = EquipmentRecommendation.builder()
                .id("r1").type(RecommendationType.PURCHASE).build();
        assertThat(mapper.toDtoList(List.of(r))).hasSize(1);
        assertThat(mapper.toDtoList(null)).isEmpty();
    }
}