package ru.iktk.modules.casting.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.iktk.core.domain.Stage;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.modules.casting.client.EquipmentClient;
import ru.iktk.modules.casting.client.GeometryClient;
import ru.iktk.modules.casting.config.CastingProperties;
import ru.iktk.modules.casting.dto.CastingRequest;
import ru.iktk.modules.casting.model.CastingMethod;
import ru.iktk.modules.casting.repository.CastingProcessRepository;
import ru.iktk.modules.casting.service.CastingProcessService.GeometryData;
import ru.iktk.modules.casting.service.CastingProcessService.ManufacturabilityReport;
import ru.iktk.modules.casting.service.CastingProcessService.ToolingSpec;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Тесты CastingProcessService — 3 публичных метода + валидация.
 *
 * <p>designProcessWithGeometry happy-path не тестируется: calculateShrinkage /
 * calculateGatingSystem / designTooling — private, и buildStages использует
 * tooling.getItems().get(0) (NPE если items пуст). Покрываем analyze,
 * buildStages, designProcess (валидация).
 */
@ExtendWith(MockitoExtension.class)
class CastingProcessServiceTest {

    @Mock private CastingProcessRepository repository;
    @Mock private GeometryClient geometryClient;
    @Mock private EquipmentClient equipmentClient;

    private CastingProperties properties;
    private CastingProcessService service;

    @BeforeEach
    void setUp() {
        properties = new CastingProperties();
        service = new CastingProcessService(repository, geometryClient, equipmentClient, properties);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private GeometryData geometry(BigDecimal minWall, BigDecimal minFillet,
                                   BigDecimal minDraft, boolean undercuts,
                                   boolean hotSpots) {
        return new GeometryData(
                "geo-1", "Steel-45",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(7.85),
                minWall, minFillet, minDraft,
                BigDecimal.valueOf(100),
                2, undercuts, hotSpots
        );
    }

    private GeometryData goodGeometry() {
        return geometry(
                BigDecimal.valueOf(10), BigDecimal.valueOf(10),
                BigDecimal.valueOf(5), false, false);
    }

    private ToolingSpec tooling() {
        return ToolingSpec.builder()
                .items(List.of("Пресс-форма"))
                .material("5ХНМ")
                .estimatedCost(BigDecimal.valueOf(150000))
                .build();
    }

    // ------------------------------------------------------------------
    // analyzeManufacturability
    // ------------------------------------------------------------------

    @Test
    void analyze_passed_whenAllGood() {
        ManufacturabilityReport r = service.analyzeManufacturability(goodGeometry());
        assertThat(r.isPassed()).isTrue();
        assertThat(r.getIssues()).isEmpty();
        assertThat(r.getWarnings()).isEmpty();
    }

    @Test
    void analyze_issue_whenThinWall() {
        var g = geometry(
                BigDecimal.valueOf(0.5), BigDecimal.valueOf(10),
                BigDecimal.valueOf(5), false, false);
        ManufacturabilityReport r = service.analyzeManufacturability(g);
        assertThat(r.isPassed()).isFalse();
        assertThat(r.getIssues()).anyMatch(s -> s.contains("Толщина стенки"));
    }

    @Test
    void analyze_issue_whenSmallFillet() {
        var g = geometry(
                BigDecimal.valueOf(10), BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(5), false, false);
        ManufacturabilityReport r = service.analyzeManufacturability(g);
        assertThat(r.isPassed()).isFalse();
        assertThat(r.getIssues()).anyMatch(s -> s.contains("Радиус скругления"));
    }

    @Test
    void analyze_warning_whenSmallDraft() {
        var g = geometry(
                BigDecimal.valueOf(10), BigDecimal.valueOf(10),
                BigDecimal.valueOf(0.1), false, false);
        ManufacturabilityReport r = service.analyzeManufacturability(g);
        assertThat(r.isPassed()).isTrue(); // warning, не issue
        assertThat(r.getWarnings()).anyMatch(s -> s.contains("Литейный уклон"));
    }

    @Test
    void analyze_warnings_whenUndercutsAndHotSpots() {
        var g = geometry(
                BigDecimal.valueOf(10), BigDecimal.valueOf(10),
                BigDecimal.valueOf(5), true, true);
        ManufacturabilityReport r = service.analyzeManufacturability(g);
        assertThat(r.isPassed()).isTrue();
        assertThat(r.getWarnings()).anyMatch(s -> s.contains("поднутрения"));
        assertThat(r.getWarnings()).anyMatch(s -> s.contains("теплоузлы"));
    }

    // ------------------------------------------------------------------
    // buildStages
    // ------------------------------------------------------------------

    @Test
    void buildStages_6stages_anyMethod() {
        // buildStages не зависит от method: всегда 6 (BLANK + 4×CASTING + CONTROL)
        for (CastingMethod m : CastingMethod.values()) {
            var stages = service.buildStages(m, null, tooling());
            assertThat(stages).hasSize(6);
            assertThat(stages.get(0).getName()).contains("Подготовка шихты");
            assertThat(stages.get(5).getName()).contains("Контроль");
        }
    }

    @Test
    void buildStages_firstStageBlankPreparation_lastTechnicalControl() {
        var stages = service.buildStages(CastingMethod.SAND_CASTING, null, tooling());
        assertThat(stages.get(0).getOperationType()).isEqualTo(Stage.OperationType.BLANK_PREPARATION);
        assertThat(stages.get(5).getOperationType()).isEqualTo(Stage.OperationType.TECHNICAL_CONTROL);
    }

    // ------------------------------------------------------------------
    // designProcess: валидация
    // ------------------------------------------------------------------

    @Test
    void designProcess_nullRequest_throws() {
        assertThatThrownBy(() -> service.designProcess(null))
                .isInstanceOf(OrchestrationException.class);
    }

    @Test
    void designProcess_missingProjectId_throws() {
        var req = new CastingRequest();
        req.setObjectRef("geo-1");
        assertThatThrownBy(() -> service.designProcess(req))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("projectId");
    }

    @Test
    void designProcess_geometryNull_throws() {
        var req = new CastingRequest();
        req.setProjectId("p1");
        req.setObjectRef("geo-1");
        when(geometryClient.fetchGeometry("p1", "geo-1")).thenReturn(null);
        assertThatThrownBy(() -> service.designProcess(req))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("Геометрия не найдена");
    }
}