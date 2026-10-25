package ru.iktk.modules.norming.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.iktk.core.domain.Stage;
import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.modules.norming.client.dto.AssemblyProcessDto;
import ru.iktk.modules.norming.client.dto.AssemblyStageDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Маппер: AssemblyProcess/AssemblyStage (assembly-process-service)
 * -> TechnologyProcess/Stage (core.domain, используются в norming).
 */
@Component
public class TechProcessMapper {

    private static final Logger log = LoggerFactory.getLogger(TechProcessMapper.class);

    /**
     * Таблица соответствия operationType из assembly-process-service
     * и core.domain.Stage.OperationType.
     *
     * <p>assembly использует свои обозначения (PREPARATION, BASIC_ASSEMBLY, ...),
     * которые не совпадают с enum Stage.OperationType. Прямое valueOf() падает,
     * поэтому используем явную таблицу.
     */
    private static final Map<String, Stage.OperationType> OPERATION_TYPE_MAP = Map.ofEntries(
            // assembly-специфичные -> Stage.OperationType
            Map.entry("PREPARATION",       Stage.OperationType.BLANK_PREPARATION),
            Map.entry("BASIC_ASSEMBLY",    Stage.OperationType.ASSEMBLY),
            Map.entry("NODE_ASSEMBLY",     Stage.OperationType.ASSEMBLY),
            Map.entry("GENERAL_ASSEMBLY",  Stage.OperationType.ASSEMBLY),
            Map.entry("CONTROL",           Stage.OperationType.TECHNICAL_CONTROL),
            // прямые соответствия
            Map.entry("BLANK_PREPARATION", Stage.OperationType.BLANK_PREPARATION),
            Map.entry("CASTING",           Stage.OperationType.CASTING),
            Map.entry("HOT_FORGING",       Stage.OperationType.HOT_FORGING),
            Map.entry("COLD_FORGING",      Stage.OperationType.COLD_FORGING),
            Map.entry("SHEET_STAMPING",    Stage.OperationType.SHEET_STAMPING),
            Map.entry("MACHINING",         Stage.OperationType.MACHINING),
            Map.entry("CNC_MACHINING",     Stage.OperationType.CNC_MACHINING),
            Map.entry("HEAT_TREATMENT",    Stage.OperationType.HEAT_TREATMENT),
            Map.entry("COATING",           Stage.OperationType.COATING),
            Map.entry("ASSEMBLY",          Stage.OperationType.ASSEMBLY),
            Map.entry("TECHNICAL_CONTROL", Stage.OperationType.TECHNICAL_CONTROL),
            Map.entry("NORMING",           Stage.OperationType.NORMING),
            Map.entry("AUXILIARY",         Stage.OperationType.AUXILIARY)
    );

    public TechnologyProcess toTechnologyProcess(AssemblyProcessDto process,
                                                List<AssemblyStageDto> stages) {
        if (process == null) {
            throw new IllegalArgumentException("AssemblyProcess is null");
        }

        TechnologyProcess tp = new TechnologyProcess(
                process.getId(),
                process.getObjectRef(),
                null,
                process.getObjectRef(),
                null,
                null
        );
        tp.setName(process.getNotes() != null ? process.getNotes() : "TP from assembly " + process.getId());
        tp.setVersion("1.0");
        tp.setAuthor("assembly-process-service");

        List<Stage> mappedStages = new ArrayList<>();
        if (stages != null) {
            int order = 1;
            for (AssemblyStageDto s : stages) {
                Stage stage = toStage(s, order++);
                if (stage != null) mappedStages.add(stage);
            }
        }
        tp.setStages(mappedStages);
        log.debug("TechProcessMapper: processId={}, stages mapped={}", process.getId(), mappedStages.size());
        return tp;
    }

    private Stage toStage(AssemblyStageDto s, int defaultOrder) {
        if (s == null) return null;
        int order = s.getStageOrder() > 0 ? s.getStageOrder() : defaultOrder;
        Stage.OperationType op = parseOperationType(s.getOperationType());

        Stage stage = new Stage(
                s.getId(),
                s.getDesignation(),
                s.getName() != null ? s.getName() : "Stage " + order,
                op,
                order
        );
        stage.setEquipmentId(s.getEquipmentId());
        stage.setToolingId(s.getToolingId());
        stage.setTimeNormMin(s.getTimeNormMin() != null ? s.getTimeNormMin() : BigDecimal.ZERO);
        stage.setSetupTimeMin(s.getSetupTimeMin() != null ? s.getSetupTimeMin() : BigDecimal.ZERO);
        stage.setComment(s.getComment());
        return stage;
    }

    /**
     * Маппинг operationType: assembly-строка -> Stage.OperationType.
     *
     * <p>Сначала ищем в OPERATION_TYPE_MAP (явные соответствия),
     * затем — fallback на AUXILIARY (с WARN в лог).
     */
    private Stage.OperationType parseOperationType(String raw) {
        if (raw == null || raw.isBlank()) return Stage.OperationType.AUXILIARY;
        String key = raw.trim().toUpperCase();
        Stage.OperationType mapped = OPERATION_TYPE_MAP.get(key);
        if (mapped == null) {
            log.warn("TechProcessMapper: unknown operationType={} — fallback to AUXILIARY", raw);
            return Stage.OperationType.AUXILIARY;
        }
        return mapped;
    }
}
