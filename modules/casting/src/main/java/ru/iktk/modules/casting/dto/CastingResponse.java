package ru.iktk.modules.casting.dto;

import ru.iktk.core.domain.Stage;
import ru.iktk.modules.casting.model.CastingMethod;
import ru.iktk.modules.casting.model.GatingSystem;
import ru.iktk.modules.casting.model.ShrinkageCalculation;

import java.util.ArrayList;
import java.util.List;

public class CastingResponse {
    private String projectId;
    private String processId;
    private CastingMethod method;
    private List<Stage> stages = new ArrayList<>();
    private ShrinkageCalculation shrinkage;
    private GatingSystem gatingSystem;
    private Object tooling;
    private List<String> warnings = new ArrayList<>();

    public String getProjectId() { return projectId; }
    public String getProcessId() { return processId; }
    public CastingMethod getMethod() { return method; }
    public List<Stage> getStages() { return stages; }
    public List<String> getWarnings() { return warnings; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CastingResponse r = new CastingResponse();
        public Builder projectId(String v) { r.projectId = v; return this; }
        public Builder processId(String v) { r.processId = v; return this; }
        public Builder method(CastingMethod v) { r.method = v; return this; }
        public Builder stages(List<Stage> v) { r.stages = v; return this; }
        public Builder shrinkage(ShrinkageCalculation v) { r.shrinkage = v; return this; }
        public Builder gatingSystem(GatingSystem v) { r.gatingSystem = v; return this; }
        public Builder tooling(Object v) { r.tooling = v; return this; }
        public Builder warnings(List<String> v) { r.warnings = v; return this; }
        public CastingResponse build() { return r; }
    }
}