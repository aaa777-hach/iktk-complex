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
 * Доменная модель технологического процесса сборки ИКТК.
 */
@Entity
@Table(
        name = "assembly_process",
        indexes = {
                @Index(name = "idx_assembly_process_project", columnList = "project_id"),
                @Index(name = "idx_assembly_process_status",  columnList = "status")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssemblyProcess {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @NotBlank @Size(max = 64)
    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Size(max = 255)
    @Column(name = "object_ref", length = 255)
    private String objectRef;

    @Size(max = 50)
    @Column(name = "strategy", length = 50)
    private String strategy;

    @Column(name = "stage_count")     private Integer stageCount;
    @Column(name = "component_count") private Integer componentCount;
    @Column(name = "total_time_min", precision = 12, scale = 4) private BigDecimal totalTimeMin;

    @Size(max = 32)
    @Column(name = "status", length = 32)
    private String status;

    @Size(max = 2000)
    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected AssemblyProcess() {
    }

    public AssemblyProcess(String id, String projectId) {
        this.id = id;
        this.projectId = projectId;
        this.strategy = "SEQUENTIAL";
        this.status = "DRAFT";
        this.stageCount = 0;
        this.componentCount = 0;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; touch(); }
    public String getObjectRef() { return objectRef; }
    public void setObjectRef(String objectRef) { this.objectRef = objectRef; touch(); }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; touch(); }
    public Integer getStageCount() { return stageCount; }
    public void setStageCount(Integer stageCount) { this.stageCount = stageCount; touch(); }
    public Integer getComponentCount() { return componentCount; }
    public void setComponentCount(Integer componentCount) { this.componentCount = componentCount; touch(); }
    public BigDecimal getTotalTimeMin() { return totalTimeMin; }
    public void setTotalTimeMin(BigDecimal totalTimeMin) { this.totalTimeMin = totalTimeMin; touch(); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; touch(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void approve()  { this.status = "APPROVED";  touch(); }
    public void reject()   { this.status = "REJECTED";  touch(); }
    public void archive()  { this.status = "ARCHIVED";  touch(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssemblyProcess other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "AssemblyProcess{" +
                "id='" + id + '\'' +
                ", projectId='" + projectId + '\'' +
                ", strategy='" + strategy + '\'' +
                ", status='" + status + '\'' +
                ", stageCount=" + stageCount +
                ", componentCount=" + componentCount +
                '}';
    }
}