package ru.iktk.modules.assembly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * DTO ответа на проектирование ТП сборки.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssemblyResponse {

    private String processId;
    private String projectId;
    private String objectRef;
    private String strategy;
    private String status;
    private Integer stageCount;
    private Integer componentCount;
    private BigDecimal totalTimeMin;
    private String message;
    private String error;

    public AssemblyResponse() {
    }

    private AssemblyResponse(Builder b) {
        this.processId = b.processId;
        this.projectId = b.projectId;
        this.objectRef = b.objectRef;
        this.strategy = b.strategy;
        this.status = b.status;
        this.stageCount = b.stageCount;
        this.componentCount = b.componentCount;
        this.totalTimeMin = b.totalTimeMin;
        this.message = b.message;
        this.error = b.error;
    }

    public static Builder builder() { return new Builder(); }

    public String getProcessId() { return processId; }
    public void setProcessId(String processId) { this.processId = processId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getObjectRef() { return objectRef; }
    public void setObjectRef(String objectRef) { this.objectRef = objectRef; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getStageCount() { return stageCount; }
    public void setStageCount(Integer stageCount) { this.stageCount = stageCount; }
    public Integer getComponentCount() { return componentCount; }
    public void setComponentCount(Integer componentCount) { this.componentCount = componentCount; }
    public BigDecimal getTotalTimeMin() { return totalTimeMin; }
    public void setTotalTimeMin(BigDecimal totalTimeMin) { this.totalTimeMin = totalTimeMin; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public static class Builder {
        private String processId;
        private String projectId;
        private String objectRef;
        private String strategy;
        private String status;
        private Integer stageCount;
        private Integer componentCount;
        private BigDecimal totalTimeMin;
        private String message;
        private String error;

        public Builder processId(String v) { this.processId = v; return this; }
        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder objectRef(String v) { this.objectRef = v; return this; }
        public Builder strategy(String v) { this.strategy = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder stageCount(Integer v) { this.stageCount = v; return this; }
        public Builder componentCount(Integer v) { this.componentCount = v; return this; }
        public Builder totalTimeMin(BigDecimal v) { this.totalTimeMin = v; return this; }
        public Builder message(String v) { this.message = v; return this; }
        public Builder error(String v) { this.error = v; return this; }

        public AssemblyResponse build() { return new AssemblyResponse(this); }
    }
}