package ru.iktk.modules.machining.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.iktk.modules.machining.validator.ValidationResult;

import java.time.Instant;
import java.util.List;

/**
 * Управляющая программа для станка с ЧПУ.
 */
@Entity
@Table(name = "cnc_program")
public class CncProgram {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "process_id", nullable = false)
    private String processId;

    @Column(name = "equipment_id")
    private String equipmentId;

    @Column(name = "equipment_model")
    private String equipmentModel;

    private String postprocessor;

    @Column(name = "program_uri")
    private String programUri;

    @Column(name = "program_name")
    private String programName;

    @Column(name = "line_count")
    private int lineCount;

    @Column(name = "byte_size")
    private int byteSize;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<OperationProgram> operations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "setup_sheet", columnDefinition = "jsonb")
    private SetupSheet setupSheet;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ValidationResult validation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public CncProgram() {
    }

    private CncProgram(Builder b) {
        this.id = b.id;
        this.projectId = b.projectId;
        this.processId = b.processId;
        this.equipmentId = b.equipmentId;
        this.equipmentModel = b.equipmentModel;
        this.postprocessor = b.postprocessor;
        this.programUri = b.programUri;
        this.programName = b.programName;
        this.lineCount = b.lineCount;
        this.byteSize = b.byteSize;
        this.operations = b.operations;
        this.setupSheet = b.setupSheet;
        this.validation = b.validation;
        this.createdAt = b.createdAt;
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getProcessId() { return processId; }
    public String getEquipmentId() { return equipmentId; }
    public String getEquipmentModel() { return equipmentModel; }
    public String getPostprocessor() { return postprocessor; }
    public String getProgramUri() { return programUri; }
    public String getProgramName() { return programName; }
    public int getLineCount() { return lineCount; }
    public int getByteSize() { return byteSize; }
    public List<OperationProgram> getOperations() { return operations; }
    public SetupSheet getSetupSheet() { return setupSheet; }
    public ValidationResult getValidation() { return validation; }
    public Instant getCreatedAt() { return createdAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String projectId;
        private String processId;
        private String equipmentId;
        private String equipmentModel;
        private String postprocessor;
        private String programUri;
        private String programName;
        private int lineCount;
        private int byteSize;
        private List<OperationProgram> operations;
        private SetupSheet setupSheet;
        private ValidationResult validation;
        private Instant createdAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder processId(String v) { this.processId = v; return this; }
        public Builder equipmentId(String v) { this.equipmentId = v; return this; }
        public Builder equipmentModel(String v) { this.equipmentModel = v; return this; }
        public Builder postprocessor(String v) { this.postprocessor = v; return this; }
        public Builder programUri(String v) { this.programUri = v; return this; }
        public Builder programName(String v) { this.programName = v; return this; }
        public Builder lineCount(int v) { this.lineCount = v; return this; }
        public Builder byteSize(int v) { this.byteSize = v; return this; }
        public Builder operations(List<OperationProgram> v) { this.operations = v; return this; }
        public Builder setupSheet(SetupSheet v) { this.setupSheet = v; return this; }
        public Builder validation(ValidationResult v) { this.validation = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }

        public CncProgram build() { return new CncProgram(this); }
    }
}