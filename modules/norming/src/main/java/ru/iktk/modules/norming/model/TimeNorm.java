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
        name = "time_norms",
        indexes = {
                @Index(name = "idx_tn_project", columnList = "project_id"),
                @Index(name = "idx_tn_process", columnList = "process_id"),
                @Index(name = "idx_tn_created", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeNorm {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "process_id", length = 64)
    private String processId;

    @Transient
    private TechnologyProcess process;

    @Column(name = "source", length = 32)
    private String source;

    @Column(name = "batch_size", precision = 12, scale = 4)
    private BigDecimal batchSize;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stage_norms", columnDefinition = "jsonb")
    @Builder.Default
    private List<StageTimeNorm> stageNorms = new ArrayList<>();

    @Column(name = "total_tpz", precision = 12, scale = 4) private BigDecimal totalTpz;
    @Column(name = "total_tsh", precision = 12, scale = 4) private BigDecimal totalTsh;
    @Column(name = "total_tsh_t", precision = 12, scale = 4) private BigDecimal totalTshT;
    @Column(name = "total_tsh_k", precision = 12, scale = 4) private BigDecimal totalTshK;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
