package ru.iktk.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GapAnalysisRequest {
    private String projectId;
    private String analysisId;
    private String strategy;
    private String requestedBy;
    @Builder.Default
    private List<EquipmentRequirementDto> requirements = new ArrayList<>();
}