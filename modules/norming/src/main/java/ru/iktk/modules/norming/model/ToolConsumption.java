package ru.iktk.modules.norming.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolConsumption {
    private String stageId;
    private String stageName;
    private String toolType;
    private String toolMaterial;
    private BigDecimal machineTimeMin;
    private BigDecimal toolLifeMin;
    private Integer regrindCount;
    private BigDecimal toolsPerPart;
    private BigDecimal toolsPerBatch;
}
