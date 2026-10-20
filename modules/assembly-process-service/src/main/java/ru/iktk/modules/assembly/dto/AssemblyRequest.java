package ru.iktk.modules.assembly.dto;

import java.util.Objects;

/**
 * DTO запроса на проектирование ТП сборки.
 */
public class AssemblyRequest {

    private String projectId;
    private String objectRef;
    private String strategy;
    private String bomRef;

    public AssemblyRequest() {
    }

    public AssemblyRequest(String projectId, String objectRef) {
        this.projectId = projectId;
        this.objectRef = objectRef;
        this.strategy = "SEQUENTIAL";
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getObjectRef() { return objectRef; }
    public void setObjectRef(String objectRef) { this.objectRef = objectRef; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public String getBomRef() { return bomRef; }
    public void setBomRef(String bomRef) { this.bomRef = bomRef; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssemblyRequest other)) return false;
        return Objects.equals(projectId, other.projectId)
                && Objects.equals(objectRef, other.objectRef);
    }

    @Override
    public int hashCode() { return Objects.hash(projectId, objectRef); }

    @Override
    public String toString() {
        return "AssemblyRequest{projectId='" + projectId + "', objectRef='" + objectRef
                + "', strategy='" + strategy + "'}";
    }
}