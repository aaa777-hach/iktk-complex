package ru.iktk.modules.assembly.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Этап (операция) технологического процесса сборки.
 */
@Entity
@Table(
        name = "assembly_stage",
        indexes = {
                @Index(name = "idx_assembly_stage_process", columnList = "assembly_process_id"),
                @Index(name = "idx_assembly_stage_order",   columnList = "assembly_process_id, stage_order")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssemblyStage {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @NotBlank @Size(max = 64)
    @Column(name = "assembly_process_id", nullable = false, length = 64)
    private String assemblyProcessId;

    @Column(name = "stage_order", nullable = false)
    private int stageOrder;

    @Size(max = 64)
    @Column(name = "designation", length = 64)
    private String designation;

    @Size(max = 255)
    @Column(name = "name", length = 255)
    private String name;

    @Size(max = 64)
    @Column(name = "operation_type", length = 64)
    private String operationType;

    @Size(max = 64)
    @Column(name = "equipment_id", length = 64)
    private String equipmentId;

    @Size(max = 64)
    @Column(name = "tooling_id", length = 64)
    private String toolingId;

    @Column(name = "time_norm_min", precision = 12, scale = 4)
    private BigDecimal timeNormMin;

    @Column(name = "setup_time_min", precision = 12, scale = 4)
    private BigDecimal setupTimeMin;

    @Size(max = 1000)
    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssemblyStage() {
    }

    public AssemblyStage(String id, String assemblyProcessId, int stageOrder) {
        this.id = id;
        this.assemblyProcessId = assemblyProcessId;
        this.stageOrder = stageOrder;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAssemblyProcessId() { return assemblyProcessId; }
    public void setAssemblyProcessId(String assemblyProcessId) { this.assemblyProcessId = assemblyProcessId; }
    public int getStageOrder() { return stageOrder; }
    public void setStageOrder(int stageOrder) { this.stageOrder = stageOrder; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getEquipmentId() { return equipmentId; }
    public void setEquipmentId(String equipmentId) { this.equipmentId = equipmentId; }
    public String getToolingId() { return toolingId; }
    public void setToolingId(String toolingId) { this.toolingId = toolingId; }
    public BigDecimal getTimeNormMin() { return timeNormMin; }
    public void setTimeNormMin(BigDecimal timeNormMin) { this.timeNormMin = timeNormMin; }
    public BigDecimal getSetupTimeMin() { return setupTimeMin; }
    public void setSetupTimeMin(BigDecimal setupTimeMin) { this.setupTimeMin = setupTimeMin; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }

    public BigDecimal totalTimeMin() {
        BigDecimal a = timeNormMin == null ? BigDecimal.ZERO : timeNormMin;
        BigDecimal b = setupTimeMin == null ? BigDecimal.ZERO : setupTimeMin;
        return a.add(b);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssemblyStage other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "AssemblyStage{" +
                "id='" + id + '\'' +
                ", assemblyProcessId='" + assemblyProcessId + '\'' +
                ", stageOrder=" + stageOrder +
                ", name='" + name + '\'' +
                ", operationType='" + operationType + '\'' +
                '}';
    }
}