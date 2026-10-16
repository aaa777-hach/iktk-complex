package ru.iktk.modules.casting.dto;

import ru.iktk.core.domain.TechnologyProcess;
import ru.iktk.modules.casting.model.CastingMethod;

public class CastingRequest {
    private String projectId;
    private String objectRef;
    private TechnologyProcess.ProductionType productionType;
    private CastingMethod preferredMethod;

    public String getProjectId() { return projectId; }
    public void setProjectId(String v) { this.projectId = v; }
    public String getObjectRef() { return objectRef; }
    public void setObjectRef(String v) { this.objectRef = v; }
    public TechnologyProcess.ProductionType getProductionType() { return productionType; }
    public void setProductionType(TechnologyProcess.ProductionType v) { this.productionType = v; }
    public CastingMethod getPreferredMethod() { return preferredMethod; }
    public void setPreferredMethod(CastingMethod v) { this.preferredMethod = v; }
}