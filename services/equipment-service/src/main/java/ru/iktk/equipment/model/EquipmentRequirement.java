package ru.iktk.equipment.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(
        name = "equipment_requirements",
        indexes = {
                @Index(name = "idx_req_project",   columnList = "project_id"),
                @Index(name = "idx_req_process",   columnList = "process_id"),
                @Index(name = "idx_req_operation", columnList = "operation_type"),
                @Index(name = "idx_req_priority",  columnList = "priority")
        }
)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EquipmentRequirement {

    @Id
    @Column(name = "id", nullable = false, length = 64, updatable = false)
    private String id;

    @NotBlank @Size(max = 64)
    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "process_id", length = 64)   private String processId;
    @Column(name = "stage_id", length = 64)     private String stageId;
    @Column(name = "operation_id", length = 64) private String operationId;
    @Column(name = "analysis_id", length = 64)  private String analysisId;

    @NotBlank @Size(max = 64)
    @Column(name = "operation_type", nullable = false, length = 64)
    private String operationType;

    @Column(name = "required_equipment_type", length = 64) private String requiredEquipmentType;
    @Column(name = "required_accuracy", length = 32)       private String requiredAccuracy;
    @Column(name = "max_dimensions", length = 64)          private String maxDimensions;
    @Column(name = "min_dimensions", length = 64)          private String minDimensions;
    @Column(name = "material", length = 128)               private String material;

    @Column(name = "required_power_kw")  private BigDecimal requiredPowerKw;
    @Column(name = "required_force_kn")  private BigDecimal requiredForceKn;
    @Column(name = "required_cnc", nullable = false) private Boolean requiredCnc = false;
    @Column(name = "required_axes")      private Integer requiredAxes;
    @Column(name = "required_temperature_c") private BigDecimal requiredTemperatureC;

    @Column(name = "quantity", nullable = false) private Integer quantity = 1;
    @Min(0) @Max(10)
    @Column(name = "priority", nullable = false) private Integer priority = 0;

    @Size(max = 1000)
    @Column(name = "notes", length = 1000) private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected EquipmentRequirement() {}

    public EquipmentRequirement(String id, String projectId, String operationType) {
        this.id = id;
        this.projectId = projectId;
        this.operationType = operationType;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; touch(); }
    public String getProcessId() { return processId; }
    public void setProcessId(String processId) { this.processId = processId; touch(); }
    public String getStageId() { return stageId; }
    public void setStageId(String stageId) { this.stageId = stageId; touch(); }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; touch(); }
    public String getAnalysisId() { return analysisId; }
    public void setAnalysisId(String analysisId) { this.analysisId = analysisId; touch(); }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; touch(); }
    public String getRequiredEquipmentType() { return requiredEquipmentType; }
    public void setRequiredEquipmentType(String v) { this.requiredEquipmentType = v; touch(); }
    public String getRequiredAccuracy() { return requiredAccuracy; }
    public void setRequiredAccuracy(String v) { this.requiredAccuracy = v; touch(); }
    public String getMaxDimensions() { return maxDimensions; }
    public void setMaxDimensions(String v) { this.maxDimensions = v; touch(); }
    public String getMinDimensions() { return minDimensions; }
    public void setMinDimensions(String v) { this.minDimensions = v; touch(); }
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; touch(); }
    public BigDecimal getRequiredPowerKw() { return requiredPowerKw; }
    public void setRequiredPowerKw(BigDecimal v) { this.requiredPowerKw = v; touch(); }
    public BigDecimal getRequiredForceKn() { return requiredForceKn; }
    public void setRequiredForceKn(BigDecimal v) { this.requiredForceKn = v; touch(); }
    public Boolean getRequiredCnc() { return requiredCnc; }
    public void setRequiredCnc(Boolean v) { this.requiredCnc = v; touch(); }
    public Integer getRequiredAxes() { return requiredAxes; }
    public void setRequiredAxes(Integer v) { this.requiredAxes = v; touch(); }
    public BigDecimal getRequiredTemperatureC() { return requiredTemperatureC; }
    public void setRequiredTemperatureC(BigDecimal v) { this.requiredTemperatureC = v; touch(); }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer v) { this.quantity = v; touch(); }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer v) { this.priority = v; touch(); }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; touch(); }
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; touch(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EquipmentRequirement other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "EquipmentRequirement{id='" + id + "', projectId='" + projectId
                + "', operationType='" + operationType + "', priority=" + priority + '}';
    }
}