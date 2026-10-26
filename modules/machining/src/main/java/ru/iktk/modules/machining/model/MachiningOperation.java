package ru.iktk.modules.machining.model;

import ru.iktk.core.domain.Stage;

/**
 * Операция механообработки.
 */
public class MachiningOperation {

    private int order;
    private String designation;
    private String name;
    private MachiningMethod method;
    private Stage.StageKind kind;
    private String setupId;
    private boolean cnc;
    private String equipmentId;
    private CuttingTool tool;
    private CuttingMode cuttingMode;

    public MachiningOperation() {
    }

    private MachiningOperation(Builder b) {
        this.order = b.order;
        this.designation = b.designation;
        this.name = b.name;
        this.method = b.method;
        this.kind = b.kind;
        this.setupId = b.setupId;
        this.cnc = b.cnc;
        this.equipmentId = b.equipmentId;
        this.tool = b.tool;
        this.cuttingMode = b.cuttingMode;
    }

    public int getOrder() { return order; }
    public String getDesignation() { return designation; }
    public String getName() { return name; }
    public MachiningMethod getMethod() { return method; }
    public Stage.StageKind getKind() { return kind; }
    public String getSetupId() { return setupId; }
    public boolean isCnc() { return cnc; }
    public String getEquipmentId() { return equipmentId; }
    public CuttingTool getTool() { return tool; }
    public CuttingMode getCuttingMode() { return cuttingMode; }

    public void setEquipmentId(String v) { this.equipmentId = v; }
    public void setTool(CuttingTool v) { this.tool = v; }
    public void setCuttingMode(CuttingMode v) { this.cuttingMode = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int order;
        private String designation;
        private String name;
        private MachiningMethod method;
        private Stage.StageKind kind;
        private String setupId;
        private boolean cnc;
        private String equipmentId;
        private CuttingTool tool;
        private CuttingMode cuttingMode;

        public Builder order(int v) { this.order = v; return this; }
        public Builder designation(String v) { this.designation = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder method(MachiningMethod v) { this.method = v; return this; }
        public Builder kind(Stage.StageKind v) { this.kind = v; return this; }
        public Builder setupId(String v) { this.setupId = v; return this; }
        public Builder cnc(boolean v) { this.cnc = v; return this; }
        public Builder equipmentId(String v) { this.equipmentId = v; return this; }
        public Builder tool(CuttingTool v) { this.tool = v; return this; }
        public Builder cuttingMode(CuttingMode v) { this.cuttingMode = v; return this; }

        public MachiningOperation build() { return new MachiningOperation(this); }
    }
}