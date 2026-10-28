package ru.iktk.modules.norming.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.iktk.core.domain.Stage;
import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.modules.norming.client.dto.AssemblyProcessDto;
import ru.iktk.modules.norming.client.dto.AssemblyStageDto;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TechProcessMapper — assembly DTO -> core TechnologyProcess/Stage")
class TechProcessMapperTest {

    private TechProcessMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new TechProcessMapper();
    }

    // ---------- helpers ----------

    private AssemblyProcessDto process(String id) {
        return AssemblyProcessDto.builder()
                .id(id)
                .objectRef("geo-12345")
                .build();
    }

    private AssemblyStageDto stage(String id, int order, String opType, String name) {
        return AssemblyStageDto.builder()
                .id(id)
                .assemblyProcessId("proc-1")
                .stageOrder(order)
                .designation("0" + order + "0")
                .name(name)
                .operationType(opType)
                .build();
    }

    // ---------- toTechnologyProcess ----------

    @Test
    @DisplayName("null process -> IllegalArgumentException('AssemblyProcess is null')")
    void nullProcess_throws() {
        assertThatThrownBy(() -> mapper.toTechnologyProcess(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AssemblyProcess is null");
    }

    @Test
    @DisplayName("valid process -> id/name/version/author установлены")
    void validProcess_setsMetadata() {
        TechnologyProcess tp = mapper.toTechnologyProcess(process("proc-1"), List.of());

        assertThat(tp.getId()).isEqualTo("proc-1");
        assertThat(tp.getName()).isEqualTo("TP from assembly proc-1");
        assertThat(tp.getVersion()).isEqualTo("1.0");
        assertThat(tp.getAuthor()).isEqualTo("assembly-process-service");
    }

    @Test
    @DisplayName("notes != null -> name = notes")
    void notes_present_usedAsName() {
        AssemblyProcessDto p = process("proc-1");
        p.setNotes("Мой ТП");
        TechnologyProcess tp = mapper.toTechnologyProcess(p, List.of());
        assertThat(tp.getName()).isEqualTo("Мой ТП");
    }

    @Test
    @DisplayName("null stages list -> пустой список stages")
    void nullStagesList_empty() {
        TechnologyProcess tp = mapper.toTechnologyProcess(process("proc-1"), null);
        assertThat(tp.getStages()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("5 stages -> 5 Stage в ТП, порядок как в списке")
    void fiveStages_mapped() {
        List<AssemblyStageDto> stages = List.of(
                stage("s-1", 1, "PREPARATION", "Подготовка"),
                stage("s-2", 2, "BASIC_ASSEMBLY", "Базовая"),
                stage("s-3", 3, "NODE_ASSEMBLY", "Узловая"),
                stage("s-4", 4, "GENERAL_ASSEMBLY", "Общая"),
                stage("s-5", 5, "CONTROL", "Контроль")
        );
        TechnologyProcess tp = mapper.toTechnologyProcess(process("proc-1"), stages);

        assertThat(tp.getStages()).hasSize(5);
        assertThat(tp.getStages().get(0).getId()).isEqualTo("s-1");
        assertThat(tp.getStages().get(4).getId()).isEqualTo("s-5");
    }

    @Test
    @DisplayName("null stage в списке -> пропускается")
    void nullStageInList_skipped() {
        AssemblyStageDto s1 = stage("s-1", 1, "MACHINING", "Черновая");
        AssemblyStageDto s3 = stage("s-3", 3, "CONTROL", "Контроль");

        TechnologyProcess tp = mapper.toTechnologyProcess(
                process("proc-1"), Arrays.asList(s1, null, s3));

        assertThat(tp.getStages()).hasSize(2);
        assertThat(tp.getStages().get(0).getId()).isEqualTo("s-1");
        assertThat(tp.getStages().get(1).getId()).isEqualTo("s-3");
    }

    @Test
    @DisplayName("stageOrder > 0 -> order = stageOrder; иначе — defaultOrder (index+1)")
    void order_respectsStageOrderOrDefault() {
        AssemblyStageDto s1 = stage("s-1", 5, "MACHINING", "Черновая");   // order=5
        AssemblyStageDto s2 = stage("s-2", 0, "MACHINING", "Получистовая"); // order=0 -> default=2

        TechnologyProcess tp = mapper.toTechnologyProcess(process("proc-1"), List.of(s1, s2));

        assertThat(tp.getStages().get(0).getOrder()).isEqualTo(5);
        assertThat(tp.getStages().get(1).getOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("name == null -> fallback 'Stage N'")
    void nameNull_fallback() {
        AssemblyStageDto s = stage("s-1", 1, "MACHINING", null);
        TechnologyProcess tp = mapper.toTechnologyProcess(process("proc-1"), List.of(s));
        assertThat(tp.getStages().get(0).getName()).isEqualTo("Stage 1");
    }

    @Test
    @DisplayName("timeNormMin/setupTimeMin null -> BigDecimal.ZERO")
    void nullTimeNorm_defaultsToZero() {
        AssemblyStageDto s = stage("s-1", 1, "MACHINING", "Черновая");
        // timeNormMin и setupTimeMin не заданы -> null

        TechnologyProcess tp = mapper.toTechnologyProcess(process("proc-1"), List.of(s));
        Stage mapped = tp.getStages().get(0);

        assertThat(mapped.getTimeNormMin()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mapped.getSetupTimeMin()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("equipmentId/toolingId/comment переносятся")
    void resourceFields_copied() {
        AssemblyStageDto s = stage("s-1", 1, "MACHINING", "Черновая");
        s.setEquipmentId("EQ-1");
        s.setToolingId("T-1");
        s.setComment("примечание");
        s.setTimeNormMin(BigDecimal.valueOf(12));
        s.setSetupTimeMin(BigDecimal.valueOf(3));

        TechnologyProcess tp = mapper.toTechnologyProcess(process("proc-1"), List.of(s));
        Stage mapped = tp.getStages().get(0);

        assertThat(mapped.getEquipmentId()).isEqualTo("EQ-1");
        assertThat(mapped.getToolingId()).isEqualTo("T-1");
        assertThat(mapped.getComment()).isEqualTo("примечание");
        assertThat(mapped.getTimeNormMin()).isEqualByComparingTo(BigDecimal.valueOf(12));
        assertThat(mapped.getSetupTimeMin()).isEqualByComparingTo(BigDecimal.valueOf(3));
    }

    // ---------- parseOperationType (через toTechnologyProcess) ----------

    @Test
    @DisplayName("operationType: assembly-специфичные -> Stage.OperationType")
    void operationType_assemblySpecific() {
        assertThat(mapOp("PREPARATION")).isEqualTo(Stage.OperationType.BLANK_PREPARATION);
        assertThat(mapOp("BASIC_ASSEMBLY")).isEqualTo(Stage.OperationType.ASSEMBLY);
        assertThat(mapOp("NODE_ASSEMBLY")).isEqualTo(Stage.OperationType.ASSEMBLY);
        assertThat(mapOp("GENERAL_ASSEMBLY")).isEqualTo(Stage.OperationType.ASSEMBLY);
        assertThat(mapOp("CONTROL")).isEqualTo(Stage.OperationType.TECHNICAL_CONTROL);
    }

    @Test
    @DisplayName("operationType: прямые соответствия -> тот же enum")
    void operationType_direct() {
        assertThat(mapOp("MACHINING")).isEqualTo(Stage.OperationType.MACHINING);
        assertThat(mapOp("CNC_MACHINING")).isEqualTo(Stage.OperationType.CNC_MACHINING);
        assertThat(mapOp("ASSEMBLY")).isEqualTo(Stage.OperationType.ASSEMBLY);
        assertThat(mapOp("TECHNICAL_CONTROL")).isEqualTo(Stage.OperationType.TECHNICAL_CONTROL);
        assertThat(mapOp("NORMING")).isEqualTo(Stage.OperationType.NORMING);
    }

    @Test
    @DisplayName("operationType: lowercase + пробелы -> trim + uppercase")
    void operationType_lowercaseTrimmed() {
        assertThat(mapOp("machining")).isEqualTo(Stage.OperationType.MACHINING);
        assertThat(mapOp("  cnc_machining  ")).isEqualTo(Stage.OperationType.CNC_MACHINING);
    }

    @Test
    @DisplayName("operationType: null/blank/unknown -> AUXILIARY (fallback)")
    void operationType_fallback() {
        assertThat(mapOp(null)).isEqualTo(Stage.OperationType.AUXILIARY);
        assertThat(mapOp("   ")).isEqualTo(Stage.OperationType.AUXILIARY);
        assertThat(mapOp("UNKNOWN_OP")).isEqualTo(Stage.OperationType.AUXILIARY);
    }

    private Stage.OperationType mapOp(String raw) {
        AssemblyStageDto s = stage("s-1", 1, raw, "n");
        TechnologyProcess tp = mapper.toTechnologyProcess(process("proc-1"), List.of(s));
        return tp.getStages().get(0).getOperationType();
    }
}