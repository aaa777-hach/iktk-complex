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
public class OutsourcingOptionDto {
    private String id;
    private String gapId;
    private String projectId;
    private String type;
    private String operation;
    private String contractor;
    private BigDecimal cost;
    private BigDecimal qualityRating;
    private String comment;
    private Integer leadTimeDays;
    private String status;
    private BigDecimal score;
    private String selectedBy;
    private Instant selectedAt;
    private Map<String, Object> parameters;
    private Instant createdAt;
    private Instant updatedAt;
}