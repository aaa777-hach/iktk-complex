package ru.iktk.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.iktk.recommendation.model.EquipmentGapAnalysis;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GapAnalysisResponse {
    private String id;
    private String analysisId;
    private String projectId;
    private String strategy;
    private String status;
    private String requestedBy;
    private Integer gapsCount;
    private Integer recommendationsCount;
    private List<EquipmentGapAnalysis.Gap> gaps;
    private Map<String, Object> summary;
    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;
    private String error;
}