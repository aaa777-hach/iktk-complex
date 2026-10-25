package ru.iktk.modules.norming.client.dto;

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
public class AssemblyStageDto {
    private String id;
    private String assemblyProcessId;
    private int stageOrder;
    private String designation;
    private String name;
    private String operationType;
    private String equipmentId;
    private String toolingId;
    private BigDecimal timeNormMin;
    private BigDecimal setupTimeMin;
    private String comment;
}
