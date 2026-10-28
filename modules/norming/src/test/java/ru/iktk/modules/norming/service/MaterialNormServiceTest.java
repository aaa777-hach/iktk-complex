package ru.iktk.modules.norming.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.iktk.core.domain.Stage;
import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.core.exception.OrchestrationException;
import ru.iktk.modules.norming.config.NormingProperties;
import ru.iktk.modules.norming.dto.MaterialNormRequest;
import ru.iktk.modules.norming.dto.MaterialNormResponse;
import ru.iktk.modules.norming.model.MaterialConsumption;
import ru.iktk.modules.norming.model.MaterialInfo;
import ru.iktk.modules.norming.model.MaterialNorm;
import ru.iktk.modules.norming.model.ToolConsumption;
import ru.iktk.modules.norming.repository.MaterialNormRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MaterialNormService — массы, потери, КИМ, расход инструмента")
class MaterialNormServiceTest {

    @Mock private MaterialNormRepository repository;

    private NormingProperties properties;
    private MaterialNormService service;

    @BeforeEach
    void setUp() {
        properties = new NormingProperties(); // defaults: density STEEL_45 = 7850, defect = 0.02
        service = new MaterialNormService(repository, properties);
        when(repository.save(any(MaterialNorm.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- helpers ----------

    private TechnologyProcess process(Stage... stages) {
        TechnologyProcess tp = new TechnologyProcess(
                "proc-1", "geo-1", null, "p1", null, null);
        tp.setStages(List.of(stages));
        return tp;
    }

    private Stage stage(String id, Stage.OperationType op) {
        return new Stage(id, "0" + id + "0", "Операция " + id, op, 1);
    }

    private MaterialNormRequest request(TechnologyProcess tp, BigDecimal batch) {
        return MaterialNormRequest.builder()
                .projectId("p1")
                .process(tp)
                .batchSize(batch)
                .enterpriseCode("DEMO")
                .materialCode("STEEL_45")
                .blankVolumeMm3(BigDecimal.valueOf(100000))
                .partVolumeMm3(BigDecimal.valueOf(80000))
                .build();
    }

    private MaterialInfo steel45() {
        return properties.getMaterialInfo("STEEL_45");
    }

    // ================= validate =================

    @Test
    @DisplayName("calculate: null request -> OrchestrationException")
    void calculate_nullRequest_throws() {
        assertThatThrownBy(() -> service.calculate(null))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("Запрос");
    }

    @Test
    @DisplayName("calculate: null projectId -> OrchestrationException")
    void calculate_nullProjectId_throws() {
        MaterialNormRequest r = MaterialNormRequest.builder()
                .process(process(stage("s-1", Stage.OperationType.MACHINING)))
                .build();
        assertThatThrownBy(() -> service.calculate(r))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("projectId");
    }

    @Test
    @DisplayName("calculate: пустой stages -> OrchestrationException")
    void calculate_emptyStages_throws() {
        TechnologyProcess tp = new TechnologyProcess("proc-1", "geo-1", null, "p1", null, null);
        tp.setStages(List.of());
        MaterialNormRequest r = MaterialNormRequest.builder().projectId("p1").process(tp).build();
        assertThatThrownBy(() -> service.calculate(r))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("не содержит операций");
    }

    // ================= blank / part mass =================

    @Test
    @DisplayName("calculateBlankMass: V=100000, ρ=7850 -> 0.7850 кг")
    void blankMass_formula() {
        TechnologyProcess tp = process(stage("s-1", Stage.OperationType.MACHINING));
        BigDecimal m = service.calculateBlankMass(tp, steel45(), request(tp, BigDecimal.TEN));
        assertThat(m).isEqualByComparingTo(new BigDecimal("0.7850"));
    }

    @Test
    @DisplayName("calculatePartMass: V=80000, ρ=7850 -> 0.6280 кг")
    void partMass_formula() {
        TechnologyProcess tp = process(stage("s-1", Stage.OperationType.MACHINING));
        BigDecimal m = service.calculatePartMass(tp, steel45(), request(tp, BigDecimal.TEN));
        assertThat(m).isEqualByComparingTo(new BigDecimal("0.6280"));
    }


    @Test
    @DisplayName("calculatePartMass: volume=0 -> getReferencePartMass=0.8")
    void partMass_zeroVolume_reference() {
        TechnologyProcess tp = process(stage("s-1", Stage.OperationType.MACHINING));
        MaterialNormRequest r = request(tp, BigDecimal.TEN);
        r.setPartVolumeMm3(BigDecimal.ZERO);
        assertThat(service.calculatePartMass(tp, steel45(), r))
                .isEqualByComparingTo(new BigDecimal("0.8"));
    }

    // ================= stage consumption =================

    @Test
    @DisplayName("calculateStageConsumption: TECHNICAL_CONTROL -> loss=0, consumption=blankMass, lossPercent=0")
    void stageConsumption_control_zeroLoss() {
        TechnologyProcess tp = process(stage("s-1", Stage.OperationType.TECHNICAL_CONTROL));
        BigDecimal blank = new BigDecimal("0.7850");
        BigDecimal part = new BigDecimal("0.6280");

        MaterialConsumption c = service.calculateStageConsumption(
                stage("s-1", Stage.OperationType.TECHNICAL_CONTROL), tp, steel45(), blank, part);

        assertThat(c.getLossMass()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(c.getConsumptionMass()).isEqualByComparingTo(blank);
        assertThat(c.getLossPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(c.getMaterialCode()).isEqualTo("STEEL_45");
        assertThat(c.getOperationType()).isEqualTo(Stage.OperationType.TECHNICAL_CONTROL);
    }

    @Test
    @DisplayName("calculateStageConsumption: MACHINING -> loss>0, consumption>blankMass")
    void stageConsumption_machining_hasLoss() {
        TechnologyProcess tp = process(stage("s-1", Stage.OperationType.MACHINING));
        BigDecimal blank = new BigDecimal("0.7850");
        BigDecimal part = new BigDecimal("0.6280");

        MaterialConsumption c = service.calculateStageConsumption(
                stage("s-1", Stage.OperationType.MACHINING), tp, steel45(), blank, part);

        assertThat(c.getLossMass()).isGreaterThan(BigDecimal.ZERO);
        assertThat(c.getConsumptionMass()).isGreaterThan(blank);
    }

    // ================= tool consumption =================

    @Test
    @DisplayName("calculateToolConsumption: MACHINING -> 1 инструмент, toolsPerPart=0.0417, perBatch=0.4167")
    void toolConsumption_machining() {
        TechnologyProcess tp = process(
                stage("s-1", Stage.OperationType.MACHINING),
                stage("s-2", Stage.OperationType.TECHNICAL_CONTROL));

        List<ToolConsumption> tools = service.calculateToolConsumption(tp, steel45(), BigDecimal.TEN);

        assertThat(tools).hasSize(1); // только MACHINING (CONTROL не tool-consuming)
        ToolConsumption t = tools.get(0);
        assertThat(t.getStageId()).isEqualTo("s-1");
        assertThat(t.getMachineTimeMin()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(t.getToolLifeMin()).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(t.getRegrindCount()).isEqualTo(3);
        // toolsPerPart = 10/60/4 = 0.1667/4 = 0.0417
        assertThat(t.getToolsPerPart()).isEqualByComparingTo(new BigDecimal("0.0417"));
        // toolsPerBatch = 0.0417 * 10 = 0.4170 (округление 4 знака)
        assertThat(t.getToolsPerBatch()).isEqualByComparingTo(new BigDecimal("0.4170"));
    }

    // ================= calculate: happy =================

    @Test
    @DisplayName("calculate happy: MACHINING + CONTROL, batch=10 -> массы, save, normId, КИМ>0")
    void calculate_happy() {
        TechnologyProcess tp = process(
                stage("s-1", Stage.OperationType.MACHINING),
                stage("s-2", Stage.OperationType.TECHNICAL_CONTROL));

        MaterialNormResponse resp = service.calculate(request(tp, BigDecimal.TEN));

        assertThat(resp.getNormId()).isNotBlank();
        assertThat(resp.getBlankMass()).isEqualByComparingTo(new BigDecimal("0.7850"));
        assertThat(resp.getPartMass()).isEqualByComparingTo(new BigDecimal("0.6280"));
        assertThat(resp.getNormPerPart()).isGreaterThan(BigDecimal.ZERO);
        assertThat(resp.getNormPerBatch()).isEqualByComparingTo(
                resp.getNormPerPart().multiply(BigDecimal.TEN));
        assertThat(resp.getMaterialUtilization()).isGreaterThan(BigDecimal.ZERO);
        assertThat(resp.getDefectCoefficient()).isEqualByComparingTo(new BigDecimal("0.02"));
        assertThat(resp.getConsumptions()).hasSize(2);
        assertThat(resp.getToolConsumptions()).hasSize(1);
        assertThat(resp.getMaterial().getCode()).isEqualTo("STEEL_45");
        assertThat(resp.getBatchSize()).isEqualByComparingTo(BigDecimal.TEN);

        verify(repository).save(any(MaterialNorm.class));
        ArgumentCaptor<MaterialNorm> cap = ArgumentCaptor.forClass(MaterialNorm.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getProjectId()).isEqualTo("p1");
        assertThat(cap.getValue().getProcessId()).isEqualTo("proc-1");
    }

    @Test
    @DisplayName("calculate: batchSize null -> BigDecimal.ONE")
    void calculate_nullBatch_defaultsToOne() {
        TechnologyProcess tp = process(stage("s-1", Stage.OperationType.MACHINING));
        MaterialNormResponse resp = service.calculate(request(tp, null));
        assertThat(resp.getBatchSize()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("calculate: null process -> OrchestrationException")
    void calculate_nullProcess_throws() {
        MaterialNormRequest r = MaterialNormRequest.builder()
                .projectId("p1").build();
        assertThatThrownBy(() -> service.calculate(r))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("технологический процесс");
    }

    @Test
    @DisplayName("calculate: materialCode пустой -> resolveMaterial через getDefaultMaterial (не падает)")
    void calculate_blankMaterialCode_usesDefault() {
        TechnologyProcess tp = process(stage("s-1", Stage.OperationType.MACHINING));
        MaterialNormRequest r = request(tp, BigDecimal.TEN);
        r.setMaterialCode(null);

        MaterialNormResponse resp = service.calculate(r);

        assertThat(resp.getNormId()).isNotBlank();
        assertThat(resp.getMaterial()).isNotNull();
        assertThat(resp.getMaterial().getCode()).isNotBlank();
    }
}