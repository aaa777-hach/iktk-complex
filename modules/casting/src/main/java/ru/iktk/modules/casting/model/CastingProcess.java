package ru.iktk.modules.casting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import ru.iktk.core.domain.Stage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "casting_process")
public class CastingProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 64, updatable = false)
    private String id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "object_ref", length = 255)
    private String objectRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", length = 50)
    private CastingMethod method;

    @Embedded
    private ShrinkageCalculation shrinkage;

    @Embedded
    private GatingSystem gatingSystem;

    @Transient
    private Object tooling;

    @Transient
    private List<Stage> stages = new ArrayList<>();

    @Transient
    private Object manufacturabilityReport;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public CastingProcess() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getObjectRef() { return objectRef; }
    public void setObjectRef(String objectRef) { this.objectRef = objectRef; }
    public CastingMethod getMethod() { return method; }
    public void setMethod(CastingMethod method) { this.method = method; }
    public ShrinkageCalculation getShrinkage() { return shrinkage; }
    public void setShrinkage(ShrinkageCalculation shrinkage) { this.shrinkage = shrinkage; }
    public GatingSystem getGatingSystem() { return gatingSystem; }
    public void setGatingSystem(GatingSystem gatingSystem) { this.gatingSystem = gatingSystem; }
    public Object getTooling() { return tooling; }
    public void setTooling(Object tooling) { this.tooling = tooling; }
    public List<Stage> getStages() { return stages; }
    public void setStages(List<Stage> stages) { this.stages = stages; }
    public Object getManufacturabilityReport() { return manufacturabilityReport; }
    public void setManufacturabilityReport(Object v) { this.manufacturabilityReport = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CastingProcess p = new CastingProcess();
        public Builder id(String v) { p.id = v; return this; }
        public Builder projectId(String v) { p.projectId = v; return this; }
        public Builder objectRef(String v) { p.objectRef = v; return this; }
        public Builder method(CastingMethod v) { p.method = v; return this; }
        public Builder shrinkage(ShrinkageCalculation v) { p.shrinkage = v; return this; }
        public Builder gatingSystem(GatingSystem v) { p.gatingSystem = v; return this; }
        public Builder tooling(Object v) { p.tooling = v; return this; }
        public Builder stages(List<Stage> v) { p.stages = v; return this; }
        public Builder manufacturabilityReport(Object v) { p.manufacturabilityReport = v; return this; }
        public Builder createdAt(Instant v) { p.createdAt = v; return this; }
        public CastingProcess build() { return p; }
    }
}