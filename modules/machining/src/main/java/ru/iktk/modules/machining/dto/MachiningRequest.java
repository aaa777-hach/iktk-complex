package ru.iktk.modules.machining.dto;

/**
 * Запрос на проектирование ТП механообработки.
 */
public class MachiningRequest {

    private String projectId;
    private String objectRef;

    public MachiningRequest() {
    }

    private MachiningRequest(Builder b) {
        this.projectId = b.projectId;
        this.objectRef = b.objectRef;
    }

    public String getProjectId() { return projectId; }
    public String getObjectRef() { return objectRef; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String projectId;
        private String objectRef;

        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder objectRef(String v) { this.objectRef = v; return this; }

        public MachiningRequest build() { return new MachiningRequest(this); }
    }
}