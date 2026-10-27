package ru.iktk.modules.forging.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.modules.forging.client.EquipmentClient;
import ru.iktk.modules.forging.client.GeometryClient;
import ru.iktk.modules.forging.config.ForgingProperties;
import ru.iktk.modules.forging.dto.ForgingRequest;
import ru.iktk.modules.forging.model.BilletCalculation;
import ru.iktk.modules.forging.model.ForgingMethod;
import ru.iktk.modules.forging.model.ForgingTransition;
import ru.iktk.modules.forging.repository.ForgingProcessRepository;
import ru.iktk.modules.forging.service.ForgingProcessService.ForceCalculation;
import ru.iktk.modules.forging.service.ForgingProcessService.GeometryData;
import ru.iktk.modules.forging.service.ForgingProcessService.ManufacturabilityReport;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Тесты ForgingProcessService — 4 публичных метода + валидация designProcess.
 *
 * <p>Полный happy path designProcess не тестируется отдельно: selectForgingMethod
 * и calculateBillet — private, и зависят от ForgingProperties (реальные значения).
 * Покрываем публичные analyzeManufacturability / planTransitions /
 * calculateForces / buildStages + валидацию.
 */
@ExtendWith(MockitoExtension.class)
class ForgingProcessServiceTest {

    @Mock private ForgingProcessRepository repository;
    @Mock private GeometryClient geometryClient;
    @Mock private EquipmentClient equipmentClient;

    private ForgingProperties properties;
    private ForgingProcessService service;

    @BeforeEach
    void setUp() {
        properties = new ForgingProperties();
        service = new ForgingProcessService(repository, geometryClient, equipmentClient, properties);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private GeometryData geometry(int complexity, boolean holes, boolean thinRibs,
                                   boolean undercuts, BigDecimal heightToDiameter,
                                   BigDecimal minFilletRadius, BigDecimal minWallThickness,
                                   BigDecimal minDraftAngle) {
        return new GeometryData(
                "geo-1", "Steel-45",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(7.85),
                BigDecimal.valueOf(7.85), BigDecimal.valueOf(20),
                minWallThickness, minFilletRadius, minDraftAngle,
                BigDecimal.valueOf(500),
                heightToDiameter,
                BigDecimal.valueOf(1200),
                complexity, undercuts, holes, thinRibs
        );
    }

    private GeometryData goodGeometry() {
        return geometry(2, false, false, false,
                BigDecimal.valueOf(2), BigDecimal.valueOf(10),
                BigDecimal.valueOf(10), BigDecimal.valueOf(5));
    }

    private BilletCalculation billet() {
        return BilletCalculation.builder()
                .partVolumeMm3(BigDecimal.valueOf(1000))
                .billetVolumeMm3(BigDecimal.valueOf(1200))
                .billetMassKg(BigDecimal.valueOf(9.42))
                .diameterMm(BigDecimal.valueOf(50))
                .lengthMm(BigDecimal.valueOf(100))
                .materialUtilization(BigDecimal.valueOf(0.83))
                .method(ForgingMethod.HOT_DIE_FORGING)
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
    void analyze_issue_whenSmallFillet() {
        var g = geometry(2, false, false, false,
                BigDecimal.valueOf(2), BigDecimal.valueOf(0.5), BigDecimal.valueOf(10), BigDecimal.valueOf(5));
        ManufacturabilityReport r = service.analyzeManufacturability(g);
        assertThat(r.isPassed()).isFalse();
        assertThat(r.getIssues()).anyMatch(s -> s.contains("Радиус скругления"));
    }

    @Test
    void analyze_issue_whenThinWall() {
        var g = geometry(2, false, false, false,
                BigDecimal.valueOf(2), BigDecimal.valueOf(10), BigDecimal.valueOf(0.1), BigDecimal.valueOf(5));
        ManufacturabilityReport r = service.analyzeManufacturability(g);
        assertThat(r.isPassed()).isFalse();
        assertThat(r.getIssues()).anyMatch(s -> s.contains("Толщина стенки"));
    }

    @Test
    void analyze_warning_whenSmallDraft() {
        var g = geometry(2, false, false, false,
                BigDecimal.valueOf(2), BigDecimal.valueOf(10), BigDecimal.valueOf(10), BigDecimal.valueOf(0.1));
        ManufacturabilityReport r = service.analyzeManufacturability(g);
        assertThat(r.isPassed()).isTrue(); // warning, не issue
        assertThat(r.getWarnings()).anyMatch(s -> s.contains("Штамповочный уклон"));
    }

    @Test
    void analyze_warnings_whenUndercutsThinRibsHighRatio() {
        var g = geometry(2, false, true, true,
                BigDecimal.valueOf(100), BigDecimal.valueOf(10), BigDecimal.valueOf(10), BigDecimal.valueOf(5));
        ManufacturabilityReport r = service.analyzeManufacturability(g);
        assertThat(r.isPassed()).isTrue();
        assertThat(r.getWarnings()).anyMatch(s -> s.contains("поднутрения"));
        assertThat(r.getWarnings()).anyMatch(s -> s.contains("тонкие рёбра"));
        assertThat(r.getWarnings()).anyMatch(s -> s.contains("высоты к диаметру"));
    }

    // ------------------------------------------------------------------
    // planTransitions
    // ------------------------------------------------------------------

    @Test
    void planTransitions_minimal4() {
        // complexity=2, holes=false -> UPSETTING, BLOCKING, FINISHING, TRIMMING = 4
        List<ForgingTransition> t = service.planTransitions(goodGeometry(),
                ForgingMethod.HOT_DIE_FORGING, billet());
        assertThat(t).hasSize(4);
        assertThat(t).extracting(ForgingTransition::getType)
                .containsExactly(
                        ForgingTransition.TransitionType.UPSETTING,
                        ForgingTransition.TransitionType.BLOCKING,
                        ForgingTransition.TransitionType.FINISHING,
                        ForgingTransition.TransitionType.TRIMMING
                );
    }

    @Test
    void planTransitions_complex5_addsDrawing() {
        // complexity=5 (>3) -> DRAWING = 5
        var g = geometry(5, false, false, false,
                BigDecimal.valueOf(2), BigDecimal.valueOf(10), BigDecimal.valueOf(10), BigDecimal.valueOf(5));
        List<ForgingTransition> t = service.planTransitions(g, ForgingMethod.HOT_DIE_FORGING, billet());
        assertThat(t).hasSize(5);
        assertThat(t).extracting(ForgingTransition::getType)
                .contains(ForgingTransition.TransitionType.DRAWING);
    }

    @Test
    void planTransitions_holes_addsPiercing() {
        var g = geometry(2, true, false, false,
                BigDecimal.valueOf(2), BigDecimal.valueOf(10), BigDecimal.valueOf(10), BigDecimal.valueOf(5));
        List<ForgingTransition> t = service.planTransitions(g, ForgingMethod.HOT_DIE_FORGING, billet());
        assertThat(t).hasSize(5);
        assertThat(t).extracting(ForgingTransition::getType)
                .contains(ForgingTransition.TransitionType.PIERCING);
    }

    @Test
    void planTransitions_complex5AndHoles_6() {
        var g = geometry(5, true, false, false,
                BigDecimal.valueOf(2), BigDecimal.valueOf(10), BigDecimal.valueOf(10), BigDecimal.valueOf(5));
        List<ForgingTransition> t = service.planTransitions(g, ForgingMethod.HOT_DIE_FORGING, billet());
        assertThat(t).hasSize(6);
    }

    // ------------------------------------------------------------------
    // calculateForces
    // ------------------------------------------------------------------

    @Test
    void calculateForces_onePerTransition() {
        var g = goodGeometry();
        List<ForgingTransition> t = service.planTransitions(g, ForgingMethod.HOT_DIE_FORGING, billet());
        List<ForceCalculation> f = service.calculateForces(g, ForgingMethod.HOT_DIE_FORGING, t);
        assertThat(f).hasSize(t.size());
        assertThat(f).allMatch(fc -> fc.getForceKn() != null
                && fc.getForceKn().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void calculateForces_usesProjectedArea() {
        var g = goodGeometry(); // projectedArea = 500
        var t = List.of(ForgingTransition.builder().order(1).name("X")
                .type(ForgingTransition.TransitionType.UPSETTING).build());
        List<ForceCalculation> f = service.calculateForces(g, ForgingMethod.HOT_DIE_FORGING, t);
        assertThat(f).hasSize(1);
        assertThat(f.get(0).getContactAreaMm2()).isEqualByComparingTo("500");
    }

    // ------------------------------------------------------------------
    // buildStages
    // ------------------------------------------------------------------

    @Test
    void buildStages_hotDie_minimalStructure() {
        var g = goodGeometry();
        var t = service.planTransitions(g, ForgingMethod.HOT_DIE_FORGING, billet());
        var tooling = ru.iktk.modules.forging.model.ForgingTooling.builder().build();
        var stages = service.buildStages(ForgingMethod.HOT_DIE_FORGING, t, tooling);
        // BLANK_PREPARATION + HEAT_TREATMENT + 4 transitions + COLD_FORGING + TECHNICAL_CONTROL = 8
        assertThat(stages).hasSize(8);
        assertThat(stages.get(0).getName()).contains("Резка");
        assertThat(stages.get(stages.size() - 1).getName()).contains("Контроль");
    }

    @Test
    void buildStages_coldDie_noHeatTreatment() {
        var g = goodGeometry();
        var t = service.planTransitions(g, ForgingMethod.COLD_DIE_FORGING, billet());
        var tooling = ru.iktk.modules.forging.model.ForgingTooling.builder().build();
        var stages = service.buildStages(ForgingMethod.COLD_DIE_FORGING, t, tooling);
        // без HEAT_TREATMENT: 1 + 4 + 1 + 1 = 7
        assertThat(stages).hasSize(7);
    }

    // ------------------------------------------------------------------
    // designProcess: валидация (не happy path)
    // ------------------------------------------------------------------

    @Test
    void designProcess_nullRequest_throws() {
        assertThatThrownBy(() -> service.designProcess(null))
                .isInstanceOf(OrchestrationException.class);
    }

    @Test
    void designProcess_missingProjectId_throws() {
        var req = ForgingRequest.builder().objectRef("geo-1").build();
        assertThatThrownBy(() -> service.designProcess(req))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("projectId");
    }

    @Test
    void designProcess_geometryNull_throws() {
        var req = ForgingRequest.builder().projectId("p1").objectRef("geo-1").build();
        when(geometryClient.fetchGeometry("p1", "geo-1")).thenReturn(null);
        assertThatThrownBy(() -> service.designProcess(req))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("Геометрия не найдена");
    }
}