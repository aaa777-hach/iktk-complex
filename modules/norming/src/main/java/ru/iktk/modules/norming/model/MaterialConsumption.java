package ru.iktk.modules.norming.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.iktk.core.domain.Stage.OperationType;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialConsumption {
    private String stageId;
    private String stageDesignation;
    private String stageName;
    private OperationType operationType;
    private String materialCode;
    private BigDecimal lossMass;
    private BigDecimal consumptionMass;
    private BigDecimal lossPercent;
}
