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

/**
 * Маппер: AssemblyProcess/AssemblyStage (assembly-process-service)
 * -> TechnologyProcess/Stage (core.domain, используются в norming).
 */
@Component
public class TechProcessMapper {

    private static final Logger log = LoggerFactory.getLogger(TechProcessMapper.class);

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

    private Stage.OperationType parseOperationType(String raw) {
        if (raw == null || raw.isBlank()) return Stage.OperationType.AUXILIARY;
        try {
            return Stage.OperationType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("TechProcessMapper: unknown operationType={} — fallback to AUXILIARY", raw);
            return Stage.OperationType.AUXILIARY;
        }
    }
}
