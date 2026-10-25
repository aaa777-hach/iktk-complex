package ru.iktk.modules.norming.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import ru.iktk.core.domain.TechnologyProcess;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "material_norms",
        indexes = {
                @Index(name = "idx_mn_project", columnList = "project_id"),
                @Index(name = "idx_mn_process", columnList = "process_id"),
                @Index(name = "idx_mn_created", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialNorm {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "process_id", length = 64)
    private String processId;

    @Transient
    private TechnologyProcess process;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "material", columnDefinition = "jsonb")
    private MaterialInfo material;

    @Column(name = "blank_mass", precision = 12, scale = 4) private BigDecimal blankMass;
    @Column(name = "part_mass",  precision = 12, scale = 4) private BigDecimal partMass;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "consumptions", columnDefinition = "jsonb")
    @Builder.Default
    private List<MaterialConsumption> consumptions = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_consumptions", columnDefinition = "jsonb")
    @Builder.Default
    private List<ToolConsumption> toolConsumptions = new ArrayList<>();

    @Column(name = "norm_per_part",  precision = 12, scale = 4) private BigDecimal normPerPart;
    @Column(name = "norm_per_batch", precision = 12, scale = 4) private BigDecimal normPerBatch;
    @Column(name = "batch_size",     precision = 12, scale = 4) private BigDecimal batchSize;
    @Column(name = "material_utilization",   precision = 12, scale = 4) private BigDecimal materialUtilization;
    @Column(name = "tech_loss_coefficient",  precision = 12, scale = 4) private BigDecimal techLossCoefficient;
    @Column(name = "defect_coefficient",     precision = 12, scale = 4) private BigDecimal defectCoefficient;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
