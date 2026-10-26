package ru.iktk.modules.machining.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.iktk.core.domain.Stage;
import ru.iktk.modules.machining.dto.ManufacturabilityReport;
import ru.iktk.modules.machining.dto.Setup;

import java.time.Instant;
import java.util.List;

/**
 * Технологический процесс механической обработки.
 */
@Entity
@Table(name = "machining_process")
public class MachiningProcess {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "object_ref", nullable = false)
    private String objectRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Setup> setups;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<MachiningOperation> operations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Stage> stages;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manufacturability_report", columnDefinition = "jsonb")
    private ManufacturabilityReport manufacturabilityReport;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public MachiningProcess() {
    }

    private MachiningProcess(Builder b) {
        this.id = b.id;
        this.projectId = b.projectId;
        this.objectRef = b.objectRef;
        this.setups = b.setups;
        this.operations = b.operations;
        this.stages = b.stages;
        this.manufacturabilityReport = b.manufacturabilityReport;
        this.createdAt = b.createdAt;
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getObjectRef() { return objectRef; }
    public List<Setup> getSetups() { return setups; }
    public List<MachiningOperation> getOperations() { return operations; }
    public List<Stage> getStages() { return stages; }
    public ManufacturabilityReport getManufacturabilityReport() { return manufacturabilityReport; }
    public Instant getCreatedAt() { return createdAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String projectId;
        private String objectRef;
        private List<Setup> setups;
        private List<MachiningOperation> operations;
        private List<Stage> stages;
        private ManufacturabilityReport manufacturabilityReport;
        private Instant createdAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder objectRef(String v) { this.objectRef = v; return this; }
        public Builder setups(List<Setup> v) { this.setups = v; return this; }
        public Builder operations(List<MachiningOperation> v) { this.operations = v; return this; }
        public Builder stages(List<Stage> v) { this.stages = v; return this; }
        public Builder manufacturabilityReport(ManufacturabilityReport v) { this.manufacturabilityReport = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }

        public MachiningProcess build() { return new MachiningProcess(this); }
    }
}