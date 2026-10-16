package ru.iktk.modules.casting.model;

import java.math.BigDecimal;

public class ShrinkageCalculation {
    private BigDecimal shrinkagePercent;
    private BigDecimal linearAllowanceMm;
    private String material;
    private CastingMethod method;

    public BigDecimal getShrinkagePercent() { return shrinkagePercent; }
    public void setShrinkagePercent(BigDecimal v) { this.shrinkagePercent = v; }
    public BigDecimal getLinearAllowanceMm() { return linearAllowanceMm; }
    public void setLinearAllowanceMm(BigDecimal v) { this.linearAllowanceMm = v; }
    public String getMaterial() { return material; }
    public void setMaterial(String v) { this.material = v; }
    public CastingMethod getMethod() { return method; }
    public void setMethod(CastingMethod v) { this.method = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ShrinkageCalculation s = new ShrinkageCalculation();
        public Builder shrinkagePercent(BigDecimal v) { s.shrinkagePercent = v; return this; }
        public Builder linearAllowanceMm(BigDecimal v) { s.linearAllowanceMm = v; return this; }
        public Builder material(String v) { s.material = v; return this; }
        public Builder method(CastingMethod v) { s.method = v; return this; }
        public ShrinkageCalculation build() { return s; }
    }
}