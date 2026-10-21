package ru.iktk.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentRequirementDto {
    private String id;
    private String processId;
    private String stageId;
    private String operationId;
    private String operationType;
    private String requiredAccuracy;
    private String maxDimensions;
    private String material;
    private BigDecimal requiredPowerKw;
    private BigDecimal requiredForceKn;
    private Boolean requiredCnc;
    private Integer quantity;
    private String notes;
}