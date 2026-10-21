package ru.iktk.bom.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Ответ на анализ BOM.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BomResponse {

    private String projectId;
    private String assemblyId;
    private String bomId;
    private String status;
    private Integer positionsCount;
    private Integer connectionsCount;
    private String message;

    public BomResponse() {
    }

    private BomResponse(Builder b) {
        this.projectId = b.projectId;
        this.assemblyId = b.assemblyId;
        this.bomId = b.bomId;
        this.status = b.status;
        this.positionsCount = b.positionsCount;
        this.connectionsCount = b.connectionsCount;
        this.message = b.message;
    }

    public static Builder builder() { return new Builder(); }

    public static BomResponse alreadyExists(String assemblyId, String projectId, int positionsCount) {
        return builder()
                .projectId(projectId)
                .assemblyId(assemblyId)
                .bomId(assemblyId)
                .status("ALREADY_EXISTS")
                .positionsCount(positionsCount)
                .message("BOM уже существует")
                .build();
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getAssemblyId() { return assemblyId; }
    public void setAssemblyId(String assemblyId) { this.assemblyId = assemblyId; }
    public String getBomId() { return bomId; }
    public void setBomId(String bomId) { this.bomId = bomId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getPositionsCount() { return positionsCount; }
    public void setPositionsCount(Integer positionsCount) { this.positionsCount = positionsCount; }
    public Integer getConnectionsCount() { return connectionsCount; }
    public void setConnectionsCount(Integer connectionsCount) { this.connectionsCount = connectionsCount; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public static class Builder {
        private String projectId;
        private String assemblyId;
        private String bomId;
        private String status;
        private Integer positionsCount;
        private Integer connectionsCount;
        private String message;

        public Builder projectId(String v) { this.projectId = v; return this; }
        public Builder assemblyId(String v) { this.assemblyId = v; return this; }
        public Builder bomId(String v) { this.bomId = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder positionsCount(Integer v) { this.positionsCount = v; return this; }
        public Builder connectionsCount(Integer v) { this.connectionsCount = v; return this; }
        public Builder message(String v) { this.message = v; return this; }

        public BomResponse build() { return new BomResponse(this); }
    }
}