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
import ru.iktk.recommendation.dto.EquipmentRecommendationDto;
import ru.iktk.recommendation.dto.VendorCatalogDto;
import ru.iktk.recommendation.exception.RecommendationException;
import ru.iktk.recommendation.mapper.RecommendationMapper;
import ru.iktk.recommendation.model.EquipmentRecommendation;
import ru.iktk.recommendation.model.VendorCatalog;
import ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository;
import ru.iktk.recommendation.repository.EquipmentRecommendationRepository;
import ru.iktk.recommendation.repository.VendorCatalogRepository;
import ru.iktk.shared.versioning.VersioningService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EquipmentRecommendationService — рекомендации")
class EquipmentRecommendationServiceTest {

    @Mock private EquipmentRecommendationRepository recommendationRepository;
    @Mock private EquipmentGapAnalysisRepository gapRepository;
    @Mock private VendorCatalogRepository vendorRepository;
    @Mock private RecommendationMapper mapper;
    @Mock private EquipmentClient equipmentClient;
    @Mock private VersioningService versioningService;
    @Mock private RecommendationProperties properties;

    @InjectMocks private EquipmentRecommendationService service;

    @Test
    @DisplayName("getByProject: null -> validation")
    void getByProject_null_throws() {
        assertThatThrownBy(() -> service.getByProject(null, null, null))
                .isInstanceOf(RecommendationException.class);
    }

    @Test
    @DisplayName("getByGap: null -> validation")
    void getByGap_null_throws() {
        assertThatThrownBy(() -> service.getByGap(null))
                .isInstanceOf(RecommendationException.class);
    }

    @Test
    @DisplayName("getById: null -> validation")
    void getById_null_throws() {
        assertThatThrownBy(() -> service.getById(null))
                .isInstanceOf(RecommendationException.class);
    }

    @Test
    @DisplayName("getById: не найден -> NOT_FOUND")
    void getById_notFound_throws() {
        when(recommendationRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById("missing"))
                .isInstanceOfSatisfying(RecommendationException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("getById: найден -> mapper")
    void getById_found_returnsMapped() {
        EquipmentRecommendation r = new EquipmentRecommendation("r1", "p1", "g1",
                EquipmentRecommendation.RecommendationType.PURCHASE);
        EquipmentRecommendationDto dto = EquipmentRecommendationDto.builder().id("r1").build();
        when(recommendationRepository.findById("r1")).thenReturn(Optional.of(r));
        when(mapper.toDto(r)).thenReturn(dto);
        assertThat(service.getById("r1").getId()).isEqualTo("r1");
    }

    @Test
    @DisplayName("accept: null -> validation")
    void accept_null_throws() {
        assertThatThrownBy(() -> service.accept(null, "ok"))
                .isInstanceOf(RecommendationException.class);
    }

    @Test
    @DisplayName("accept: не найден -> NOT_FOUND")
    void accept_notFound_throws() {
        when(recommendationRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.accept("missing", "ok"))
                .isInstanceOfSatisfying(RecommendationException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("reject: не найден -> NOT_FOUND")
    void reject_notFound_throws() {
        when(recommendationRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reject("missing", "no"))
                .isInstanceOfSatisfying(RecommendationException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("getVendorById: не найден -> NOT_FOUND")
    void getVendorById_notFound_throws() {
        when(vendorRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getVendorById("missing"))
                .isInstanceOfSatisfying(RecommendationException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("getVendorById: найден -> mapper")
    void getVendorById_found_returnsMapped() {
        VendorCatalog v = VendorCatalog.builder().id("v1").vendorName("A").build();
        VendorCatalogDto dto = VendorCatalogDto.builder().id("v1").build();
        when(vendorRepository.findById("v1")).thenReturn(Optional.of(v));
        when(mapper.toVendorDto(v)).thenReturn(dto);
        assertThat(service.getVendorById("v1").getId()).isEqualTo("v1");
    }

    @Test
    @DisplayName("searchVendorCatalog: пустой результат -> пустой список")
    void searchVendorCatalog_empty() {
        when(vendorRepository.search("TURNING", null, null, null)).thenReturn(List.of());
        when(vendorRepository.findByEquipmentType("TURNING")).thenReturn(List.of());
        assertThat(service.searchVendorCatalog("TURNING", null, null, null)).isEmpty();
    }
}