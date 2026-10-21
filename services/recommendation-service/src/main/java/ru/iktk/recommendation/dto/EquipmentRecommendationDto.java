package ru.iktk.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentRecommendationDto {
    private String id;
    private String projectId;
    private String gapId;
    private String analysisId;
    private String type;
    private String status;
    private String equipmentId;
    private String vendorId;
    private String rationale;
    private BigDecimal score;
    private BigDecimal estimatedCost;
    private Integer leadTimeDays;
    private String acceptedBy;
    private Instant acceptedAt;
    private Map<String, Object> parameters;
    private Instant createdAt;
    private Instant updatedAt;
}