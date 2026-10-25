package ru.iktk.modules.norming.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssemblyProcessDto {
    private String id;
    private String projectId;
    private String objectRef;
    private String strategy;
    private Integer stageCount;
    private Integer componentCount;
    private BigDecimal totalTimeMin;
    private String status;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
