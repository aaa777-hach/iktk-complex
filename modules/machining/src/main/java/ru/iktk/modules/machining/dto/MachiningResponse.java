package ru.iktk.modules.machining.dto;

import ru.iktk.core.domain.Stage;
import ru.iktk.modules.machining.model.MachiningOperation;

import java.util.List;

/**
 * Результат проектирования ТП механообработки.
 */
public class MachiningResponse {

    private String projectId;
    private String processId;
    private List<Setup> setups;
    private List<MachiningOperation> operations;
    private List<Stage> stages;
    private boolean requiresCncProgram;
    private List<String> warnings;

    public MachiningResponse() {
    }

    private MachiningResponse(Builder b) {
        this.projectId = b.projectId;
        this.processId = b.processId;
        this.setups = b.setups;
        this.operations = b.operations;
        this.stages = b.stages;
        this.requiresCncProgram = b.requiresCncProgram;
        this.warnings = b.warnings;
    }

    public String getProjectId() { return projectId; }
    public String getProcessId() { return processId; }
    public List<Setup> getSetups() { return setups; }
    public List<MachiningOperation> getOperations() { return operations; }
    public List<Stage> getStages() { return stages; }
    public boolean isRequiresCncProgram() { return requiresCncProgram; }
    public List<String> getWarnings() { return warnings; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String projectId;
        private String processId;
        private List<Setup> setups;
        private List<MachiningOperation> operations;
        private List<Stage> stages;
        private boolean requiresCncProgram;
        private List<String> warnings;

        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder processId(String v) { this.processId = v; return this; }
        public Builder setups(List<Setup> v) { this.setups = v; return this; }
        public Builder operations(List<MachiningOperation> v) { this.operations = v; return this; }
        public Builder stages(List<Stage> v) { this.stages = v; return this; }
        public Builder requiresCncProgram(boolean v) { this.requiresCncProgram = v; return this; }
        public Builder warnings(List<String> v) { this.warnings = v; return this; }

        public MachiningResponse build() { return new MachiningResponse(this); }
    }
}