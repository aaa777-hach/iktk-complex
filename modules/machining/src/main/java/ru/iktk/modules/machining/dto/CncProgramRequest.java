package ru.iktk.modules.machining.dto;

import ru.iktk.modules.machining.model.MachiningOperation;

import java.util.List;

/**
 * Запрос на генерацию управляющей программы.
 */
public class CncProgramRequest {

    private String projectId;
    private String processId;
    private String equipmentId;
    private List<MachiningOperation> operations;

    public CncProgramRequest() {
    }

    private CncProgramRequest(Builder b) {
        this.projectId = b.projectId;
        this.processId = b.processId;
        this.equipmentId = b.equipmentId;
        this.operations = b.operations;
    }

    public String getProjectId() { return projectId; }
    public String getProcessId() { return processId; }
    public String getEquipmentId() { return equipmentId; }
    public List<MachiningOperation> getOperations() { return operations; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String projectId;
        private String processId;
        private String equipmentId;
        private List<MachiningOperation> operations;

        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder processId(String v) { this.processId = v; return this; }
        public Builder equipmentId(String v) { this.equipmentId = v; return this; }
        public Builder operations(List<MachiningOperation> v) { this.operations = v; return this; }

        public CncProgramRequest build() { return new CncProgramRequest(this); }
    }
}