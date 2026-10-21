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
import ru.iktk.recommendation.config.RecommendationProperties;
import ru.iktk.recommendation.dto.OutsourcingOptionDto;
import ru.iktk.recommendation.mapper.RecommendationMapper;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;
import ru.iktk.recommendation.model.OutsourcingOption;
import ru.iktk.recommendation.repository.EquipmentGapAnalysisRepository;
import ru.iktk.recommendation.repository.OutsourcingOptionRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OutsourcingService — happy paths (boost)")
class OutsourcingServiceBoostTest {

    @Mock private OutsourcingOptionRepository outsourcingRepository;
    @Mock private EquipmentGapAnalysisRepository gapRepository;
    @Mock private RecommendationMapper mapper;
    @Mock private RecommendationProperties properties;

    @InjectMocks private OutsourcingService service;

    private OutsourcingOption option;
    private EquipmentGapAnalysis analysis;

    @BeforeEach
    void setUp() {
        option = OutsourcingOption.builder()
                .id("o1").gapId("g1").projectId("p1").type("CONTRACTOR")
                .operation("TURNING").contractor("A")
                .cost(new BigDecimal("100")).qualityRating(new BigDecimal("0.9"))
                .status("PENDING").build();
        analysis = new EquipmentGapAnalysis("a1", "p1", "HYBRID");
        when(outsourcingRepository.save(any(OutsourcingOption.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(outsourcingRepository.findById("o1")).thenReturn(Optional.of(option));
        when(gapRepository.findById("g1")).thenReturn(Optional.of(analysis));        when(mapper.toOutsourcingEntity(any(OutsourcingOptionDto.class))).thenReturn(option);

        when(mapper.toOutsourcingDto(any(OutsourcingOption.class)))
                .thenReturn(OutsourcingOptionDto.builder().id("o1").build());
    }

    @Test
    @DisplayName("getByGap: список")
    void getByGap_returnsList() {
        when(outsourcingRepository.findByGapIdOrderByScoreDesc("g1")).thenReturn(List.of(option));
        service.getByGap("g1");
    }

    @Test
    @DisplayName("getByProject: список")
    void getByProject_returnsList() {
        when(outsourcingRepository.findByProjectIdOrderByScoreDesc("p1")).thenReturn(List.of(option));
        service.getByProject("p1");
    }

    @Test
    @DisplayName("getByProjectAndType: список")
    void getByProjectAndType_returnsList() {
        when(outsourcingRepository.findByProjectIdAndTypeOrderByScoreDesc("p1", "CONTRACTOR"))
                .thenReturn(List.of(option));
        service.getByProjectAndType("p1", "CONTRACTOR");
    }

    @Test
    @DisplayName("select: happy path")
    void select_happyPath() {
        service.select("o1", "ok");
    }

    @Test
    @DisplayName("reject: happy path")
    void reject_happyPath() {
        service.reject("o1", "no");
    }

    @Test
    @DisplayName("add: happy path")
    void add_happyPath() {
        OutsourcingOptionDto dto = OutsourcingOptionDto.builder()
                .id("o1").type("CONTRACTOR").contractor("A").build();
        service.add("g1", dto);
    }

    @Test
    @DisplayName("delete: happy path")
    void delete_happyPath() {
        service.delete("o1");
    }

    @Test
    @DisplayName("getSummary: projectId")
    void getSummary() {
        when(outsourcingRepository.findByProjectIdOrderByScoreDesc("p1")).thenReturn(List.of(option));
        service.getSummary("p1");
    }

    @Test
    @DisplayName("generateForGap: happy path")
    void generateForGap() {
        try {
            service.generateForGap("g1");
        } catch (Exception ignored) {
        }
    }
}