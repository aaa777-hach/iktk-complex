package ru.iktk.bom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Запрос на анализ BOM.
 */
public class BomAnalyzeRequest {

    @NotBlank
    @Size(max = 64)
    private String projectId;

    @Size(max = 128)
    private String geometryId;

    @Size(max = 32)
    private String cadSystem;

    private boolean force;

    public BomAnalyzeRequest() {
    }

    public BomAnalyzeRequest(String projectId, String geometryId, String cadSystem, boolean force) {
        this.projectId = projectId;
        this.geometryId = geometryId;
        this.cadSystem = cadSystem;
        this.force = force;
    }

    public static BomAnalyzeRequest forProject(String projectId) {
        BomAnalyzeRequest r = new BomAnalyzeRequest();
        r.setProjectId(projectId);
        return r;
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getGeometryId() { return geometryId; }
    public void setGeometryId(String geometryId) { this.geometryId = geometryId; }

    public String getCadSystem() { return cadSystem; }
    public void setCadSystem(String cadSystem) { this.cadSystem = cadSystem; }

    public boolean isForce() { return force; }
    public void setForce(boolean force) { this.force = force; }
}