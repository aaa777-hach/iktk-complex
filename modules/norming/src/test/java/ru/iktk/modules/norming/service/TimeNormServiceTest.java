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
import ru.iktk.modules.norming.client.EquipmentClient;
import ru.iktk.modules.norming.config.NormingProperties;
import ru.iktk.modules.norming.dto.TimeNormRequest;
import ru.iktk.modules.norming.dto.TimeNormResponse;
import ru.iktk.modules.norming.model.StageTimeNorm;
import ru.iktk.modules.norming.model.TimeNorm;
import ru.iktk.modules.norming.model.TimeNormSource;
import ru.iktk.modules.norming.repository.TimeNormRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TimeNormService — формулы То/Тв/Тшт/Тшт.к и источник норм")
class TimeNormServiceTest {

    @Mock private TimeNormRepository repository;
    @Mock private EquipmentClient equipmentClient;

    private NormingProperties properties;
    private TimeNormService service;

    @BeforeEach
    void setUp() {
        properties = new NormingProperties(); // реальные defaults — цифры как в E2E
        service = new TimeNormService(repository, equipmentClient, properties);
        when(repository.save(any(TimeNorm.class))).thenAnswer(inv -> inv.getArgument(0));
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

    private TimeNormRequest request(TechnologyProcess tp, TimeNormSource source, BigDecimal batch) {
        return TimeNormRequest.builder()
                .projectId("p1")
                .process(tp)
                .source(source)
                .batchSize(batch)
                .build();
    }

    private StageTimeNorm calc(Stage s, TimeNormSource source, BigDecimal batch) {
        return service.calculateStage(s, process(s), source, batch);
    }

    // ================= calculate: validate =================

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
        TimeNormRequest r = TimeNormRequest.builder()
                .process(process(stage("s-1", Stage.OperationType.MACHINING)))
                .build();
        assertThatThrownBy(() -> service.calculate(r))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("projectId");
    }

    @Test
    @DisplayName("calculate: null process -> OrchestrationException")
    void calculate_nullProcess_throws() {
        TimeNormRequest r = TimeNormRequest.builder().projectId("p1").build();
        assertThatThrownBy(() -> service.calculate(r))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("технологический процесс");
    }

    @Test
    @DisplayName("calculate: пустой stages -> OrchestrationException")
    void calculate_emptyStages_throws() {
        TechnologyProcess tp = new TechnologyProcess("proc-1", "geo-1", null, "p1", null, null);
        tp.setStages(List.of());
        TimeNormRequest r = TimeNormRequest.builder().projectId("p1").process(tp).build();
        assertThatThrownBy(() -> service.calculate(r))
                .isInstanceOf(OrchestrationException.class)
                .hasMessageContaining("не содержит операций");
    }

    // ================= calculate: happy =================

    @Test
    @DisplayName("calculate: 2 stages CALCULATED -> агрегаты, save, normId")
    void calculate_happy_aggregates() {
        Stage s1 = stage("s-1", Stage.OperationType.MACHINING);
        Stage s2 = stage("s-2", Stage.OperationType.CASTING);
        TechnologyProcess tp = process(s1, s2);

        TimeNormResponse resp = service.calculate(
                request(tp, TimeNormSource.CALCULATED, BigDecimal.TEN));

        assertThat(resp.getNormId()).isNotBlank();
        assertThat(resp.getSource()).isEqualTo(TimeNormSource.CALCULATED);
        assertThat(resp.getStageNorms()).hasSize(2);
        // s1 MACHINING: Tsh=19.260, TshK=24.449, Tpz=20.000
        // s2 CASTING:  To=9.000, Tv=17, Top=26, Tsh=27.820, Tpz=20, TshK=...*1.15
        assertThat(resp.getTotalTsh()).isEqualByComparingTo(new BigDecimal("47.080"));
        assertThat(resp.getTotalTpz()).isEqualByComparingTo(new BigDecimal("40.000"));
        verify(repository).save(any(TimeNorm.class));

        ArgumentCaptor<TimeNorm> cap = ArgumentCaptor.forClass(TimeNorm.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getProjectId()).isEqualTo("p1");
        assertThat(cap.getValue().getProcessId()).isEqualTo("proc-1");
        assertThat(cap.getValue().getStageNorms()).hasSize(2);
    }

    @Test
    @DisplayName("calculate: batchSize null -> BigDecimal.ONE")
    void calculate_nullBatch_defaultsToOne() {
        Stage s = stage("s-1", Stage.OperationType.MACHINING);
        TimeNormResponse resp = service.calculate(
                request(process(s), TimeNormSource.CALCULATED, null));
        assertThat(resp.getBatchSize()).isEqualByComparingTo(BigDecimal.ONE);
    }

    // ================= calculateStage: ACTUAL =================

    @Test
    @DisplayName("ACTUAL: To=timeNormMin, Tv=0, Tsh=To, Tpz=setupTimeMin, TshK=To+Tpz/n")
    void calculateStage_actual() {
        Stage s = stage("s-1", Stage.OperationType.MACHINING);
        s.setTimeNormMin(BigDecimal.valueOf(10));
        s.setSetupTimeMin(BigDecimal.valueOf(20));

        StageTimeNorm n = calc(s, TimeNormSource.ACTUAL, BigDecimal.TEN);

        assertThat(n.getTo()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(n.getTv()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(n.getTop()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(n.getTob()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(n.getTotl()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(n.getTsh()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(n.getTpz()).isEqualByComparingTo(new BigDecimal("20"));
        // TshK = 10 + 20/10 = 12, * 1.0 (ACTUAL) = 12.000
        assertThat(n.getTshK()).isEqualByComparingTo(new BigDecimal("12.000"));
        assertThat(n.getTshT()).isEqualByComparingTo(new BigDecimal("12.000"));
        assertThat(n.getSource()).isEqualTo(TimeNormSource.ACTUAL);
    }

    @Test
    @DisplayName("ACTUAL без timeNormMin -> To = calculateMainTime (MACHINING = 1.000)")
    void calculateStage_actual_noTimeNorm_fallsBackToMainTime() {
        Stage s = stage("s-1", Stage.OperationType.MACHINING);
        // timeNormMin не задан
        StageTimeNorm n = calc(s, TimeNormSource.ACTUAL, BigDecimal.TEN);
        // MACHINING: L=100, i=2, S=0.2, n=1000 -> To = 200/200 = 1.000
        assertThat(n.getTo()).isEqualByComparingTo(new BigDecimal("1.000"));
    }

    // ================= calculateStage: CALCULATED =================

    @Test
    @DisplayName("CALCULATED MACHINING: To=1.000, Tv=17, Tsh=19.260, Tpz=20, TshK=24.449")
    void calculateStage_calculated_machining() {
        StageTimeNorm n = calc(stage("s-1", Stage.OperationType.MACHINING),
                TimeNormSource.CALCULATED, BigDecimal.TEN);

        assertThat(n.getTo()).isEqualByComparingTo(new BigDecimal("1.000"));
        assertThat(n.getTv()).isEqualByComparingTo(new BigDecimal("17.000"));
        assertThat(n.getTop()).isEqualByComparingTo(new BigDecimal("18.000"));
        assertThat(n.getTob()).isEqualByComparingTo(new BigDecimal("0.540"));
        assertThat(n.getTotl()).isEqualByComparingTo(new BigDecimal("0.720"));
        assertThat(n.getTsh()).isEqualByComparingTo(new BigDecimal("19.260"));
        assertThat(n.getTpz()).isEqualByComparingTo(new BigDecimal("20.000"));
        assertThat(n.getTshT()).isEqualByComparingTo(new BigDecimal("21.260"));
        assertThat(n.getTshK()).isEqualByComparingTo(new BigDecimal("24.449"));
    }

    @Test
    @DisplayName("CALCULATED CASTING: To = base(3) + mass(5)*complexity(1.2) = 9.000")
    void calculateStage_calculated_casting() {
        StageTimeNorm n = calc(stage("s-1", Stage.OperationType.CASTING),
                TimeNormSource.CALCULATED, BigDecimal.TEN);
        assertThat(n.getTo()).isEqualByComparingTo(new BigDecimal("9.000"));
    }

    @Test
    @DisplayName("CALCULATED HOT_FORGING: To = base(2) + mass(5)*factor(0.5) = 4.500")
    void calculateStage_calculated_forging() {
        StageTimeNorm n = calc(stage("s-1", Stage.OperationType.HOT_FORGING),
                TimeNormSource.CALCULATED, BigDecimal.TEN);
        assertThat(n.getTo()).isEqualByComparingTo(new BigDecimal("4.500"));
    }

    @Test
    @DisplayName("CALCULATED ASSEMBLY: To = connections(4)*time(2) = 8.000")
    void calculateStage_calculated_assembly() {
        StageTimeNorm n = calc(stage("s-1", Stage.OperationType.ASSEMBLY),
                TimeNormSource.CALCULATED, BigDecimal.TEN);
        assertThat(n.getTo()).isEqualByComparingTo(new BigDecimal("8.000"));
    }

    @Test
    @DisplayName("CALCULATED TECHNICAL_CONTROL: To = points(5)*time(0.5) = 2.500")
    void calculateStage_calculated_control() {
        StageTimeNorm n = calc(stage("s-1", Stage.OperationType.TECHNICAL_CONTROL),
                TimeNormSource.CALCULATED, BigDecimal.TEN);
        assertThat(n.getTo()).isEqualByComparingTo(new BigDecimal("2.500"));
    }

    @Test
    @DisplayName("CALCULATED: BLANK_PREPARATION=5, HEAT_TREATMENT=60, COATING=10, AUXILIARY=0.5")
    void calculateStage_calculated_simpleOps() {
        assertThat(calc(stage("s-1", Stage.OperationType.BLANK_PREPARATION),
                TimeNormSource.CALCULATED, BigDecimal.TEN).getTo())
                .isEqualByComparingTo(new BigDecimal("5"));
        assertThat(calc(stage("s-1", Stage.OperationType.HEAT_TREATMENT),
                TimeNormSource.CALCULATED, BigDecimal.TEN).getTo())
                .isEqualByComparingTo(new BigDecimal("60"));
        assertThat(calc(stage("s-1", Stage.OperationType.COATING),
                TimeNormSource.CALCULATED, BigDecimal.TEN).getTo())
                .isEqualByComparingTo(new BigDecimal("10"));
        assertThat(calc(stage("s-1", Stage.OperationType.AUXILIARY),
                TimeNormSource.CALCULATED, BigDecimal.TEN).getTo())
                .isEqualByComparingTo(new BigDecimal("0.5"));
    }

    @Test
    @DisplayName("CALCULATED + tooling + equipment -> Tpz = 20+5+8 = 33.000")
    void calculateStage_calculated_tpzWithToolingEquipment() {
        Stage s = stage("s-1", Stage.OperationType.MACHINING);
        s.setToolingId("T-1");
        s.setEquipmentId("EQ-1");
        StageTimeNorm n = calc(s, TimeNormSource.CALCULATED, BigDecimal.TEN);
        assertThat(n.getTpz()).isEqualByComparingTo(new BigDecimal("33.000"));
    }

    // ================= calculateMainTime / calculateAuxiliaryTime =================

    @Test
    @DisplayName("calculateMainTime: MACHINING 1.000, BLANK_PREPARATION 5")
    void calculateMainTime_direct() {
        TechnologyProcess tp = process(stage("s-1", Stage.OperationType.MACHINING));
        assertThat(service.calculateMainTime(stage("s-1", Stage.OperationType.MACHINING), tp))
                .isEqualByComparingTo(new BigDecimal("1.000"));
        assertThat(service.calculateMainTime(stage("s-1", Stage.OperationType.BLANK_PREPARATION), tp))
                .isEqualByComparingTo(new BigDecimal("5"));
    }

    @Test
    @DisplayName("calculateAuxiliaryTime: setup(10)+control(5)+handling(2) = 17.000")
    void calculateAuxiliaryTime_direct() {
        TechnologyProcess tp = process(stage("s-1", Stage.OperationType.MACHINING));
        assertThat(service.calculateAuxiliaryTime(stage("s-1", Stage.OperationType.MACHINING), tp))
                .isEqualByComparingTo(new BigDecimal("17.000"));
    }

    // ================= resolveSource (через calculate) =================

    @Test
    @DisplayName("source=null -> GENERAL_MACHINE_BUILDING (hasPlant/IndustryNorms=false)")
    void calculate_sourceNull_generalMachineBuilding() {
        Stage s = stage("s-1", Stage.OperationType.MACHINING);
        TimeNormResponse resp = service.calculate(
                request(process(s), null, BigDecimal.TEN));
        assertThat(resp.getSource()).isEqualTo(TimeNormSource.GENERAL_MACHINE_BUILDING);
    }

    @Test
    @DisplayName("source=CALCULATED -> остаётся CALCULATED")
    void calculate_sourceExplicit_calculated() {
        Stage s = stage("s-1", Stage.OperationType.MACHINING);
        TimeNormResponse resp = service.calculate(
                request(process(s), TimeNormSource.CALCULATED, BigDecimal.TEN));
        assertThat(resp.getSource()).isEqualTo(TimeNormSource.CALCULATED);
    }
}