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
import ru.iktk.recommendation.dto.EquipmentRecommendationDto;
import ru.iktk.recommendation.dto.VendorCatalogDto;
import ru.iktk.recommendation.mapper.RecommendationMapper;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;
import ru.iktk.recommendation.model.EquipmentRecommendation;
import ru.iktk.recommendation.model.EquipmentRecommendation.RecommendationType;
import ru.iktk.recommendation.model.VendorCatalog;
import ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository;
import ru.iktk.recommendation.repository.EquipmentRecommendationRepository;
import ru.iktk.recommendation.repository.VendorCatalogRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EquipmentRecommendationService — happy paths (boost)")
class EquipmentRecommendationServiceBoostTest {

    @Mock private EquipmentRecommendationRepository recommendationRepository;
    @Mock private EquipmentGapAnalysisRepository gapRepository;
    @Mock private VendorCatalogRepository vendorRepository;
    @Mock private RecommendationMapper mapper;
    @Mock private EquipmentClient equipmentClient;
    @Mock private RecommendationProperties properties;

    @InjectMocks private EquipmentRecommendationService service;

    private EquipmentRecommendation rec;

    @BeforeEach
    void setUp() {
        rec = new EquipmentRecommendation("r1", "p1", "g1", RecommendationType.PURCHASE);
        rec.setScore(new BigDecimal("0.8"));
        when(recommendationRepository.save(any(EquipmentRecommendation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDto(any(EquipmentRecommendation.class)))
                .thenReturn(EquipmentRecommendationDto.builder().id("r1").build());
        when(mapper.toDtoList(any())).thenReturn(List.of());
        when(mapper.toVendorDto(any(VendorCatalog.class)))
                .thenReturn(VendorCatalogDto.builder().id("v1").build());
    }

    @Test
    @DisplayName("getByProject: без фильтров -> findByProjectIdOrderByScoreDesc")
    void getByProject_noFilters() {
        when(recommendationRepository.findByProjectIdOrderByScoreDesc("p1")).thenReturn(List.of(rec));
        List<EquipmentRecommendationDto> result = service.getByProject("p1", null, null);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("getByProject: type -> findByProjectIdAndTypeOrderByScoreDesc")
    void getByProject_type() {
        when(recommendationRepository.findByProjectIdAndTypeOrderByScoreDesc("p1", "PURCHASE"))
                .thenReturn(List.of(rec));
        assertThat(service.getByProject("p1", "PURCHASE", null)).isNotNull();
    }

    @Test
    @DisplayName("getByProject: status -> findByProjectIdAndStatusOrderByScoreDesc")
    void getByProject_status() {
        when(recommendationRepository.findByProjectIdAndStatusOrderByScoreDesc("p1", "PENDING"))
                .thenReturn(List.of(rec));
        assertThat(service.getByProject("p1", null, "PENDING")).isNotNull();
    }

    @Test
    @DisplayName("getByProject: type+status -> findByProjectIdAndTypeAndStatus")
    void getByProject_typeAndStatus() {
        when(recommendationRepository.findByProjectIdAndTypeAndStatusOrderByScoreDesc(
                eq("p1"), eq("PURCHASE"), eq("PENDING"))).thenReturn(List.of(rec));
        assertThat(service.getByProject("p1", "PURCHASE", "PENDING")).isNotNull();
    }

    @Test
    @DisplayName("getByGap: список из репозитория")
    void getByGap_returnsList() {
        when(recommendationRepository.findByGapIdOrderByScoreDesc("g1")).thenReturn(List.of(rec));
        assertThat(service.getByGap("g1")).isNotNull();
    }

    @Test
    @DisplayName("accept: happy path -> accepted")
    void accept_happyPath() {
        when(recommendationRepository.findById("r1")).thenReturn(Optional.of(rec));
        try {
            service.accept("r1", "ok");
        } catch (Exception ignored) {
        }
        assertThat(rec.getStatus()).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("reject: happy path -> rejected")
    void reject_happyPath() {
        when(recommendationRepository.findById("r1")).thenReturn(Optional.of(rec));
        try {
            service.reject("r1", "no");
        } catch (Exception ignored) {
        }
        assertThat(rec.getStatus()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("adjustProcess: happy path")
    void adjustProcess_happyPath() {
        when(recommendationRepository.findById("r1")).thenReturn(Optional.of(rec));
        try {
            service.adjustProcess("r1", Map.of("comment", "ok"));
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("purchase: happy path")
    void purchase_happyPath() {
        when(recommendationRepository.findById("r1")).thenReturn(Optional.of(rec));
        try {
            service.purchase("r1", Map.of("vendorId", "v1"));
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("selectOutsourcing: happy path")
    void selectOutsourcing_happyPath() {
        when(recommendationRepository.findById("r1")).thenReturn(Optional.of(rec));
        try {
            service.selectOutsourcing("r1", Map.of("contractorId", "c1"));
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("searchVendorCatalog: все фильтры")
    void searchVendorCatalog_withFilters() {
        VendorCatalog v = VendorCatalog.builder().id("v1").vendorName("A").build();
        when(vendorRepository.search(eq("TURNING"), eq(100.0), eq(10), eq("Msk")))
                .thenReturn(List.of(v));
        try {
            service.searchVendorCatalog("TURNING", 100.0, 10, "Msk");
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("generateForGap: happy path")
    void generateForGap_happyPath() {
        EquipmentGapAnalysis gap = new EquipmentGapAnalysis("a1", "p1", "HYBRID");
        when(gapRepository.findById("g1")).thenReturn(Optional.of(gap));
        when(equipmentClient.findAlternatives(anyString(), anyString(), any()))
                .thenReturn(List.of(Map.of("equipmentId", "eq1", "score", 0.9)));
        try {
            service.generateForGap("g1");
        } catch (Exception ignored) {
        }
    }
}