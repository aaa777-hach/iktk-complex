package ru.iktk.modules.norming.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.iktk.modules.norming.model.MaterialConsumption;
import ru.iktk.modules.norming.model.MaterialInfo;
import ru.iktk.modules.norming.model.ToolConsumption;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialNormResponse {
    private String projectId;
    private String processId;
    private String normId;
    private MaterialInfo material;
    private BigDecimal blankMass;
    private BigDecimal partMass;
    @Builder.Default
    private List<MaterialConsumption> consumptions = new ArrayList<>();
    @Builder.Default
    private List<ToolConsumption> toolConsumptions = new ArrayList<>();
    private BigDecimal normPerPart;
    private BigDecimal normPerBatch;
    private BigDecimal batchSize;
    private BigDecimal materialUtilization;
    private BigDecimal techLossCoefficient;
    private BigDecimal defectCoefficient;
}
