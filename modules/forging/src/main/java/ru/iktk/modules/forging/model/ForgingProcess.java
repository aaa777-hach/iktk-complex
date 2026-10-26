package ru.iktk.modules.forging.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.iktk.core.domain.Stage;
import ru.iktk.modules.forging.service.ForgingProcessService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "forging_process",
        indexes = {
                @Index(name = "idx_fp_project", columnList = "project_id"),
                @Index(name = "idx_fp_method", columnList = "method")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgingProcess {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "object_ref", length = 255)
    private String objectRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 32)
    private ForgingMethod method;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "billet", columnDefinition = "jsonb")
    private BilletCalculation billet;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transitions", columnDefinition = "jsonb")
    @Builder.Default
    private List<ForgingTransition> transitions = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forces", columnDefinition = "jsonb")
    @Builder.Default
    private List<ForgingProcessService.ForceCalculation> forces = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tooling", columnDefinition = "jsonb")
    private ForgingTooling tooling;

    @Transient
    @Builder.Default
    private List<Stage> stages = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manufacturability_report", columnDefinition = "jsonb")
    private ForgingProcessService.ManufacturabilityReport manufacturabilityReport;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
