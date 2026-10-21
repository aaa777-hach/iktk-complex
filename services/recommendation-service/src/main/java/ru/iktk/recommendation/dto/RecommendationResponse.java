package ru.iktk.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {
    private String recommendationId;
    private String projectId;
    private String gapId;
    private String type;
    private String status;
    private String state;
    private String message;
    private String acceptedBy;
    private Instant acceptedAt;
    private EquipmentRecommendationDto recommendation;
}