package ru.iktk.modules.casting.model;

import ru.iktk.core.domain.Stage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CastingProcess {
    private String id;
    private String projectId;
    private String objectRef;
    private CastingMethod method;
    private ShrinkageCalculation shrinkage;
    private GatingSystem gatingSystem;
    private Object tooling;
    private List<Stage> stages = new ArrayList<>();
    private Object manufacturabilityReport;
    private Instant createdAt;

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getObjectRef() { return objectRef; }
    public CastingMethod getMethod() { return method; }
    public ShrinkageCalculation getShrinkage() { return shrinkage; }
    public GatingSystem getGatingSystem() { return gatingSystem; }
    public List<Stage> getStages() { return stages; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CastingProcess p = new CastingProcess();
        public Builder id(String v) { p.id = v; return this; }
        public Builder projectId(String v) { p.projectId = v; return this; }
        public Builder objectRef(String v) { p.objectRef = v; return this; }
        public Builder method(CastingMethod v) { p.method = v; return this; }
        public Builder shrinkage(ShrinkageCalculation v) { p.shrinkage = v; return this; }
        public Builder gatingSystem(GatingSystem v) { p.gatingSystem = v; return this; }
        public Builder tooling(Object v) { p.tooling = v; return this; }
        public Builder stages(List<Stage> v) { p.stages = v; return this; }
        public Builder manufacturabilityReport(Object v) { p.manufacturabilityReport = v; return this; }
        public Builder createdAt(Instant v) { p.createdAt = v; return this; }
        public CastingProcess build() { return p; }
    }
}