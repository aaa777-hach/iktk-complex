package ru.iktk.recommendation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.iktk.recommendation.config.RecommendationProperties;
import ru.iktk.recommendation.dto.OutsourcingOptionDto;
import ru.iktk.recommendation.exception.RecommendationException;
import ru.iktk.recommendation.mapper.RecommendationMapper;
import ru.iktk.recommendation.model.OutsourcingOption;
import ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository;
import ru.iktk.recommendation.repository.OutsourcingOptionRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OutsourcingService — варианты аутсорса")
class OutsourcingServiceTest {

    @Mock private OutsourcingOptionRepository outsourcingRepository;
    @Mock private EquipmentGapAnalysisRepository gapRepository;
    @Mock private RecommendationMapper mapper;
    @Mock private RecommendationProperties properties;

    @InjectMocks private OutsourcingService service;

    @Test
    @DisplayName("getByGap: null -> validation")
    void getByGap_null_throws() {
        assertThatThrownBy(() -> service.getByGap(null))
                .isInstanceOf(RecommendationException.class);
    }

    @Test
    @DisplayName("getByProject: null -> validation")
    void getByProject_null_throws() {
        assertThatThrownBy(() -> service.getByProject(null))
                .isInstanceOf(RecommendationException.class);
    }

    @Test
    @DisplayName("getById: не найден -> NOT_FOUND")
    void getById_notFound_throws() {
        when(outsourcingRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById("missing"))
                .isInstanceOfSatisfying(RecommendationException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("getById: найден -> mapper")
    void getById_found_returnsMapped() {
        OutsourcingOption o = OutsourcingOption.builder().id("o1").build();
        OutsourcingOptionDto dto = OutsourcingOptionDto.builder().id("o1").build();
        when(outsourcingRepository.findById("o1")).thenReturn(Optional.of(o));
        when(mapper.toOutsourcingDto(o)).thenReturn(dto);
        assertThat(service.getById("o1").getId()).isEqualTo("o1");
    }

    @Test
    @DisplayName("getByProjectAndType: null projectId -> validation")
    void getByProjectAndType_nullProject_throws() {
        assertThatThrownBy(() -> service.getByProjectAndType(null, "CONTRACTOR"))
                .isInstanceOf(RecommendationException.class);
    }

    @Test
    @DisplayName("getByProjectAndType: null type -> validation")
    void getByProjectAndType_nullType_throws() {
        assertThatThrownBy(() -> service.getByProjectAndType("p1", null))
                .isInstanceOf(RecommendationException.class);
    }

    @Test
    @DisplayName("select: не найден -> NOT_FOUND")
    void select_notFound_throws() {
        when(outsourcingRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.select("missing", "ok"))
                .isInstanceOfSatisfying(RecommendationException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("reject: не найден -> NOT_FOUND")
    void reject_notFound_throws() {
        when(outsourcingRepository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reject("missing", "no"))
                .isInstanceOfSatisfying(RecommendationException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    @DisplayName("getSelected: не найден -> null")
    void getSelected_notFound_returnsNull() {
        when(outsourcingRepository.findByGapIdAndStatus("g1", "SELECTED"))
            .thenReturn(Optional.empty());
        assertThat(service.getSelected("g1")).isNull();
    }

    @Test
    @DisplayName("hasOptions: true/false")
    void hasOptions_returnsBoolean() {
        when(outsourcingRepository.countByGapId("g1")).thenReturn(3L);
        when(outsourcingRepository.countByGapId("g2")).thenReturn(0L);
        assertThat(service.hasOptions("g1")).isTrue();
        assertThat(service.hasOptions("g2")).isFalse();
    }
}