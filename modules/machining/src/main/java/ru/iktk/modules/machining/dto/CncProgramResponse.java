package ru.iktk.modules.machining.dto;

import ru.iktk.modules.machining.model.OperationProgram;
import ru.iktk.modules.machining.model.SetupSheet;
import ru.iktk.modules.machining.validator.ValidationResult;

import java.util.List;

/**
 * Результат генерации управляющей программы.
 */
public class CncProgramResponse {

    private String projectId;
    private String programId;
    private String equipmentId;
    private String equipmentModel;
    private String postprocessor;
    private String programUri;
    private String programName;
    private List<OperationProgram> operations;
    private SetupSheet setupSheet;
    private ValidationResult validation;

    public CncProgramResponse() {
    }

    private CncProgramResponse(Builder b) {
        this.projectId = b.projectId;
        this.programId = b.programId;
        this.equipmentId = b.equipmentId;
        this.equipmentModel = b.equipmentModel;
        this.postprocessor = b.postprocessor;
        this.programUri = b.programUri;
        this.programName = b.programName;
        this.operations = b.operations;
        this.setupSheet = b.setupSheet;
        this.validation = b.validation;
    }

    public String getProjectId() { return projectId; }
    public String getProgramId() { return programId; }
    public String getEquipmentId() { return equipmentId; }
    public String getEquipmentModel() { return equipmentModel; }
    public String getPostprocessor() { return postprocessor; }
    public String getProgramUri() { return programUri; }
    public String getProgramName() { return programName; }
    public List<OperationProgram> getOperations() { return operations; }
    public SetupSheet getSetupSheet() { return setupSheet; }
    public ValidationResult getValidation() { return validation; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String projectId;
        private String programId;
        private String equipmentId;
        private String equipmentModel;
        private String postprocessor;
        private String programUri;
        private String programName;
        private List<OperationProgram> operations;
        private SetupSheet setupSheet;
        private ValidationResult validation;

        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder programId(String v) { this.programId = v; return this; }
        public Builder equipmentId(String v) { this.equipmentId = v; return this; }
        public Builder equipmentModel(String v) { this.equipmentModel = v; return this; }
        public Builder postprocessor(String v) { this.postprocessor = v; return this; }
        public Builder programUri(String v) { this.programUri = v; return this; }
        public Builder programName(String v) { this.programName = v; return this; }
        public Builder operations(List<OperationProgram> v) { this.operations = v; return this; }
        public Builder setupSheet(SetupSheet v) { this.setupSheet = v; return this; }
        public Builder validation(ValidationResult v) { this.validation = v; return this; }

        public CncProgramResponse build() { return new CncProgramResponse(this); }
    }
}