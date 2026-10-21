package ru.iktk.recommendation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "outsourcing_options", indexes = {
        @Index(name = "idx_outs_gap", columnList = "gap_id"),
        @Index(name = "idx_outs_project", columnList = "project_id"),
        @Index(name = "idx_outs_type", columnList = "type"),
        @Index(name = "idx_outs_status", columnList = "status")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutsourcingOption {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @Column(name = "gap_id", length = 64)
    private String gapId;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(name = "type", length = 32)
    private String type;

    @Column(name = "operation", length = 128)
    private String operation;

    @Column(name = "contractor", length = 256)
    private String contractor;

    @Column(name = "cost", precision = 14, scale = 2)
    private BigDecimal cost;

    @Column(name = "quality_rating", precision = 8, scale = 4)
    private BigDecimal qualityRating;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "score", precision = 8, scale = 4)
    private BigDecimal score;

    @Column(name = "selected_by", length = 128)
    private String selectedBy;

    @Column(name = "selected_at")
    private Instant selectedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; updatedAt = n; }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutsourcingOption x)) return false;
        return Objects.equals(id, x.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
}